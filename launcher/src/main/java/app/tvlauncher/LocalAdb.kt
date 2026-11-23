package app.tvlauncher

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAKeyGenParameterSpec
import java.util.Base64
import javax.crypto.Cipher

/**
 * Talks to this TV's own adb daemon over loopback. Power control is reserved for
 * system apps, but adb may do it, once the user has approved our key on screen.
 */
class LocalAdb(private val keyFile: File, private val port: Int = 5555) {

    /** Runs one adb service, such as "reboot:". Blocks, possibly on the TV's approval dialog. */
    fun run(service: String) {
        Socket().use { socket ->
            // A literal address: never anything but this device.
            socket.connect(InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), port), 3_000)
            socket.soTimeout = 5_000
            val input = DataInputStream(socket.getInputStream())
            val output = socket.getOutputStream()
            handshake(socket, input, output)

            send(output, OPEN, LOCAL_ID, 0, cString(service))
            val reply = read(input)
            if (reply.command != OKAY) throw IOException("adb refused $service")
            try {
                while (true) {
                    when (read(input).command) {
                        WRTE -> send(output, OKAY, LOCAL_ID, reply.arg0)
                        CLSE -> return
                    }
                }
            } catch (_: IOException) {
                // Accepted, then cut off: expected when the TV is going down.
            }
        }
    }

    private fun handshake(socket: Socket, input: DataInputStream, output: OutputStream) {
        send(output, CNXN, VERSION, MAX_DATA, cString("host::"))
        val key by lazy(::loadKey)
        var attempt = 0
        while (true) {
            val message = read(input)
            when {
                message.command == CNXN -> {
                    socket.soTimeout = 5_000
                    return
                }
                message.command == AUTH && message.arg0 == AUTH_TOKEN && attempt == 0 ->
                    send(output, AUTH, AUTH_SIGNATURE, 0, sign(key, message.data))
                message.command == AUTH && message.arg0 == AUTH_TOKEN && attempt == 1 -> {
                    socket.soTimeout = APPROVAL_TIMEOUT_MS
                    send(output, AUTH, AUTH_PUBLIC_KEY, 0, publicKeyMessage(key))
                }
                else -> throw IOException("not allowed")
            }
            attempt++
        }
    }

    // Plain RSAPrivateKey: Android 8 does not return the CRT type here.
    private fun loadKey(): RSAPrivateKey {
        if (!keyFile.exists()) {
            val pair = KeyPairGenerator.getInstance("RSA").apply {
                initialize(RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4))
            }.generateKeyPair()
            // Write then rename, so a crash never leaves half a key behind.
            val temp = File(keyFile.path + ".tmp")
            temp.writeBytes(pair.private.encoded)
            if (!temp.renameTo(keyFile)) throw IOException("cannot store adb key")
        }
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes())) as RSAPrivateKey
    }

    private class Message(val command: Int, val arg0: Int, val data: ByteArray)

    private fun read(input: DataInputStream): Message {
        val header = ByteArray(24).also(input::readFully)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = buffer.int
        val arg0 = buffer.int
        buffer.int // arg1
        val length = buffer.int
        if (length !in 0..MAX_DATA) throw IOException("bad adb message")
        return Message(command, arg0, ByteArray(length).also(input::readFully))
    }

    private fun send(output: OutputStream, command: Int, arg0: Int, arg1: Int, data: ByteArray = ByteArray(0)) {
        output.write(encode(command, arg0, arg1, data))
        output.flush()
    }

    companion object {
        private const val CNXN = 0x4e584e43
        private const val AUTH = 0x48545541
        private const val OPEN = 0x4e45504f
        private const val OKAY = 0x59414b4f
        private const val WRTE = 0x45545257
        private const val CLSE = 0x45534c43
        private const val AUTH_TOKEN = 1
        private const val AUTH_SIGNATURE = 2
        private const val AUTH_PUBLIC_KEY = 3
        private const val VERSION = 0x01000000
        private const val MAX_DATA = 256 * 1024
        private const val LOCAL_ID = 1
        private const val APPROVAL_TIMEOUT_MS = 60_000
        private const val KEY_WORDS = 64

        /** ASN.1 prefix saying "what follows is a SHA-1 digest"; adb signs its token as one. */
        private val SHA1_PREFIX = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14,
        )

        private fun cString(text: String) = text.toByteArray() + 0

        internal fun encode(command: Int, arg0: Int, arg1: Int, data: ByteArray): ByteArray =
            ByteBuffer.allocate(24 + data.size).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(command).putInt(arg0).putInt(arg1)
                .putInt(data.size).putInt(data.sumOf { it.toInt() and 0xff }).putInt(command.inv())
                .put(data).array()

        internal fun sign(key: PrivateKey, token: ByteArray): ByteArray =
            Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
                init(Cipher.ENCRYPT_MODE, key)
                doFinal(SHA1_PREFIX + token)
            }

        private fun publicKeyMessage(key: RSAPrivateKey): ByteArray =
            cString(Base64.getEncoder().encodeToString(publicKey(key)) + " tv-launcher")

        /** Android's own RSA public key layout, all little-endian words. */
        internal fun publicKey(key: RSAPrivateKey): ByteArray {
            val word = BigInteger.ONE.shiftLeft(32)
            val n = key.modulus
            val out = ByteArrayOutputStream()
            fun put(value: BigInteger, bytes: Int) {
                val big = value.toByteArray()
                for (i in 0 until bytes) out.write(if (i < big.size) big[big.size - 1 - i].toInt() else 0)
            }
            put(KEY_WORDS.toBigInteger(), 4)
            put(word - n.mod(word).modInverse(word), 4)
            put(n, KEY_WORDS * 4)
            put(BigInteger.ONE.shiftLeft(KEY_WORDS * 32 * 2).mod(n), KEY_WORDS * 4)
            put(RSAKeyGenParameterSpec.F4, 4)
            return out.toByteArray()
        }
    }
}
