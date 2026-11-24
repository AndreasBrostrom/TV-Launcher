package app.tvfiles

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlacesTest {
    @get:Rule val pictures = TemporaryFolder()

    @Test fun backgroundsIsCreatedWithThisSpelling() =
        assertEquals(File(pictures.root, "Background"), Places.backgrounds(pictures.root))

    @Test fun backgroundsIsFoundWhateverItsCapitals() {
        val folder = pictures.newFolder("background")
        assertEquals(folder, Places.backgrounds(pictures.root))
    }

    @Test fun aFileIsNotTheFolder() {
        pictures.newFile("background")
        assertEquals(File(pictures.root, "Background"), Places.backgrounds(pictures.root))
    }
}
