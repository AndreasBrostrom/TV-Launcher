package app.tvlauncher

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class LocalAdbTest {
    private val dir = Files.createTempDirectory("adb").toFile()
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val private = pair.private as RSAPrivateKey

    @After fun cleanUp() { dir.deleteRecursively() }

    @Test fun publicKeyLayout() {
        val b = ByteBuffer.wrap(LocalAdb.publicKey(private)).order(ByteOrder.LITTLE_ENDIAN)
        fun big(size: Int) = BigInteger(1, ByteArray(size).also(b::get).reversedArray())
        assertEquals(524, b.limit())
        assertEquals(64, b.int)
        val n0inv = b.int
        val n = big(256)
        assertEquals(private.modulus, n)
        assertEquals(-1, n0inv * n.toInt())
        assertEquals(BigInteger.ONE.shiftLeft(4096).mod(n), big(256))
        assertEquals(65537, b.int)
    }

    @Test fun signatureIsSha1Rsa() {
        // Signing a digest must equal a normal SHA1withRSA signature of the original.
        val digest = MessageDigest.getInstance("SHA-1").digest("x".toByteArray())
        val verifier = Signature.getInstance("SHA1withRSA").apply { initVerify(pair.public) }
        verifier.update("x".toByteArray())
        assertTrue(verifier.verify(LocalAdb.sign(private, digest)))
    }

    private class Msg(val command: String, val arg0: Int, val data: ByteArray)

    private fun DataInputStream.msg(): Msg {
        val b = ByteBuffer.wrap(ByteArray(24).also(::readFully)).order(ByteOrder.LITTLE_ENDIAN)
        val command = b.int
        val arg0 = b.int
        b.int
        val data = ByteArray(b.int)
        val sum = b.int
        assertEquals(command.inv(), b.int)
        readFully(data)
        assertEquals(sum, data.sumOf { it.toInt() and 0xff })
        return Msg(String(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(command).array()), arg0, data)
    }

    private fun Socket.reply(command: String, arg0: Int, arg1: Int = 0, data: ByteArray = ByteArray(0)) {
        val code = ByteBuffer.wrap(command.toByteArray()).order(ByteOrder.LITTLE_ENDIAN).int
        getOutputStream().write(LocalAdb.encode(code, arg0, arg1, data))
    }

    /** Runs [script] as a fake adb daemon while [client] talks to it. */
    private fun withDaemon(script: (Socket, DataInputStream) -> Unit, client: (LocalAdb) -> Unit) {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val done = CompletableFuture.runAsync {
                server.accept().use { script(it, DataInputStream(it.getInputStream())) }
            }
            client(LocalAdb(File(dir, "key"), server.localPort))
            done.get(5, TimeUnit.SECONDS)
        }
    }

    @Test fun newKeyIsOfferedThenServiceRuns() {
        val token = ByteArray(20) { it.toByte() }
        withDaemon({ socket, input ->
            assertEquals("CNXN", input.msg().command)
            socket.reply("AUTH", 1, data = token)
            assertEquals(2, input.msg().arg0)
            socket.reply("AUTH", 1, data = token) // signature unknown
            val offered = input.msg()
            assertEquals(3, offered.arg0)
            val text = String(offered.data).trimEnd('\u0000')
            assertEquals(524, Base64.getDecoder().decode(text.substringBefore(' ')).size)
            socket.reply("CNXN", 0x01000000, 4096, "device::".toByteArray())
            assertArrayEquals("reboot:\u0000".toByteArray(), input.msg().data)
            socket.reply("OKAY", 7, 1)
            socket.reply("WRTE", 7, 1, "ok".toByteArray())
            assertEquals("OKAY", input.msg().command)
            socket.reply("CLSE", 7, 1)
        }) { it.run("reboot:") }
        assertTrue(File(dir, "key").exists())
    }

    @Test fun droppedConnectionAfterAcceptIsSuccess() = withDaemon({ socket, input ->
        input.msg()
        socket.reply("CNXN", 0x01000000, 4096)
        input.msg()
        socket.reply("OKAY", 7, 1)
    }) { it.run("reboot:") }

    @Test fun refusalFails() = withDaemon({ socket, input ->
        input.msg()
        socket.reply("CNXN", 0x01000000, 4096)
        input.msg()
        socket.reply("CLSE", 0, 1)
    }) { adb -> assertThrows(IOException::class.java) { adb.run("nope:") } }
}
