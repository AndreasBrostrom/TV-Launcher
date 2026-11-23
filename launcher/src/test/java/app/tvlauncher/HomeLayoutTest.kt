package app.tvlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {
    private val layout = HomeLayout(
        listOf(Row("A", listOf("a1", "gone", "a2")), Row("B", listOf("b1")), Row("C", emptyList())),
    )
    private val visible = { pkg: String -> pkg != "gone" }

    private fun HomeLayout.apps(row: Int) = rows[row].apps

    @Test fun jsonRoundTrip() = assertEquals(layout, HomeLayout.fromJson(layout.toJson()))

    @Test fun defaultRoundTrip() =
        assertEquals(HomeLayout.DEFAULT, HomeLayout.fromJson(HomeLayout.DEFAULT.toJson()))

    @Test fun addingMovesInsteadOfDuplicating() {
        val added = layout.add("a1", 1)
        assertEquals(listOf("gone", "a2"), added.apps(0))
        assertEquals(listOf("b1", "a1"), added.apps(1))
    }

    @Test fun addToNewRow() {
        val added = layout.addToNewRow("x", "New")
        assertEquals(Row("New", listOf("x")), added.rows.last())
    }

    @Test fun remove() {
        assertTrue("b1" in layout)
        assertFalse("b1" in layout.remove("b1"))
    }

    @Test fun moveInRowSkipsUninstalled() {
        assertEquals(listOf("gone", "a2", "a1"), layout.moveInRow("a1", 1, visible).apps(0))
        assertEquals(listOf("a2", "a1", "gone"), layout.moveInRow("a2", -1, visible).apps(0))
    }

    @Test fun moveInRowStopsAtEdges() {
        assertSame(layout, layout.moveInRow("a1", -1, visible))
        assertSame(layout, layout.moveInRow("a2", 1, visible))
    }

    @Test fun moveToRowKeepsColumn() {
        assertEquals(listOf("b1", "a1", "gone", "a2"), layout.moveToRow("b1", -1, visible).apps(0))
        assertEquals(listOf("b1", "a2"), layout.moveToRow("a2", 1, visible).apps(1))
    }

    @Test fun moveToRowCanFillEmptyRowAndStopsAtEdges() {
        assertEquals(listOf("b1"), layout.moveToRow("b1", 1, visible).apps(2))
        assertSame(layout, layout.moveToRow("a1", -1, visible))
    }

    @Test fun moveRowSkipsHiddenRows() {
        assertEquals(listOf("B", "A", "C"), layout.moveRow(0, 1, visible).rows.map(Row::name))
        assertSame(layout, layout.moveRow(1, 1, visible))
    }

    @Test fun renameAndDeleteRow() {
        assertEquals("Z", layout.renameRow(1, "Z").rows[1].name)
        assertEquals(listOf("A", "C"), layout.deleteRow(1).rows.map(Row::name))
    }
}
