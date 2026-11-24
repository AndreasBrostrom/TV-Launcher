package app.tvlauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundTest {
    @Test fun sampleSizeStillFillsTheScreen() {
        assertEquals(1, Background.sampleSize(1920, 1080, 1920, 1080))
        assertEquals(1, Background.sampleSize(8000, 1500, 1920, 1080)) // a panorama
        assertEquals(2, Background.sampleSize(4000, 3000, 1920, 1080))
        assertEquals(4, Background.sampleSize(8000, 6000, 1920, 1080))
        assertEquals(1, Background.sampleSize(-1, -1, 1920, 1080)) // not an image
    }

    @Test fun brighterPicturesAreDarkenedMore() {
        assertEquals(128, Background.shadeAlpha(0f))
        assertEquals(217, Background.shadeAlpha(1f))
    }
}
