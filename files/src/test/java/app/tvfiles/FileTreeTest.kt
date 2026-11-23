package app.tvfiles

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileTreeTest {
    private val root = Files.createTempDirectory("files").toFile()

    @After fun cleanUp() { root.deleteRecursively() }

    private fun file(path: String) = File(root, path).apply { parentFile!!.mkdirs(); writeText("x") }

    private fun FileTree.names() = rows().map { "  ".repeat(it.depth) + it.entry.file.name }

    @Test fun listsFoldersFirstAndHidesDotFiles() {
        file("b.txt"); file("A.txt"); file("zoo/x"); file(".hidden")
        assertEquals(listOf("zoo", "A.txt", "b.txt"), Disk.list(root)!!.map { it.file.name })
        assertNull(Disk.list(File(root, "missing")))
    }

    @Test fun opensAndClosesFolders() {
        val sub = file("dir/inner.txt").parentFile!!
        file("z.txt")
        val tree = FileTree(root)
        tree.put(root, Disk.list(root)!!)
        assertEquals(listOf("dir", "z.txt"), tree.names())
        tree.put(sub, Disk.list(sub)!!)
        assertEquals(listOf("dir", "  inner.txt", "z.txt"), tree.names())
        assertTrue(tree.rows().first().expanded)
        tree.close(sub)
        assertEquals(listOf("dir", "z.txt"), tree.names())
    }

    @Test fun renameRefusesBadAndTakenNames() {
        val a = file("a"); file("b")
        for (bad in listOf("", " ", "b", "x/y", "..", ".")) assertFalse(bad, Disk.rename(a, bad))
        assertTrue(Disk.rename(a, "c"))
        assertTrue(File(root, "c").exists())
    }

    @Test fun newFolder() {
        assertTrue(Disk.newFolder(root, "n"))
        assertFalse(Disk.newFolder(root, "n"))
        assertFalse(Disk.newFolder(root, "../n"))
    }

    @Test fun deleteRemovesFolderButNotWhatLinksPointTo() {
        val outside = Files.createTempDirectory("outside").toFile()
        val kept = File(outside, "kept").apply { writeText("x") }
        file("d/e/f")
        Files.createSymbolicLink(File(root, "d/link").toPath(), outside.toPath())
        assertTrue(Disk.delete(File(root, "d")))
        assertFalse(File(root, "d").exists())
        assertTrue(kept.exists())
        assertFalse(Disk.delete(File(root, "missing")))
        outside.deleteRecursively()
    }
}
