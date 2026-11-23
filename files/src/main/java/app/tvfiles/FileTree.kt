package app.tvfiles

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** A file or folder, or a [drive] in the list of drives. */
class Entry(val file: File, val isFolder: Boolean, val size: Long, val drive: Drive? = null)

class Row(val entry: Entry, val depth: Int, val expanded: Boolean)

/** The tree below [root]. Holds only listings of opened folders; never touches the disk. */
class FileTree(val root: File) {
    private val open = HashMap<File, List<Entry>>()

    fun isOpen(folder: File): Boolean = folder in open

    fun openFolders(): List<File> = open.keys.toList()

    fun put(folder: File, entries: List<Entry>) {
        open[folder] = entries
    }

    fun close(folder: File) {
        open.remove(folder)
    }

    fun rows(): List<Row> = buildList { addRows(root, 0) }

    private fun MutableList<Row>.addRows(folder: File, depth: Int) {
        for (entry in open[folder].orEmpty()) {
            add(Row(entry, depth, isOpen(entry.file)))
            if (entry.isFolder) addRows(entry.file, depth + 1)
        }
    }
}

/** Disk access. Slow: call off the main thread. */
object Disk {
    /** Folders first, then by name, hidden files left out. Null if unreadable. */
    fun list(folder: File): List<Entry>? = folder.listFiles()
        ?.filterNot { it.name.startsWith(".") }
        ?.map { file -> file.isDirectory.let { Entry(file, it, if (it) 0 else file.length()) } }
        ?.sortedWith(compareBy<Entry> { !it.isFolder }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.file.name })

    fun rename(file: File, name: String): Boolean {
        val target = File(file.parentFile, name)
        return isValidName(name) && !target.exists() && file.renameTo(target)
    }

    fun newFolder(parent: File, name: String): Boolean = isValidName(name) && File(parent, name).mkdir()

    /** Deletes [file] and everything in it, without following links out of it. */
    fun delete(file: File): Boolean = runCatching {
        Files.walkFileTree(file.toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(path)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(path: Path, error: IOException?): FileVisitResult {
                if (error != null) throw error
                Files.delete(path)
                return FileVisitResult.CONTINUE
            }
        })
    }.isSuccess

    private fun isValidName(name: String) = name.isNotBlank() && '/' !in name && name != "." && name != ".."
}
