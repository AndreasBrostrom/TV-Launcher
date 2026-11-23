package app.tvfiles

import org.junit.Assert.assertEquals
import org.junit.Test

class ImagesTest {
    @Test fun sampleSizeNeverGoesBelowTheShownSize() {
        assertEquals(1, Images.sampleSize(100, 100, 480, 480))
        assertEquals(1, Images.sampleSize(900, 100, 480, 480))
        assertEquals(2, Images.sampleSize(960, 100, 480, 480))
        assertEquals(8, Images.sampleSize(4000, 3000, 480, 480))
        assertEquals(2, Images.sampleSize(4000, 3000, 1920, 1080))
        assertEquals(1, Images.sampleSize(-1, -1, 480, 480)) // not an image
    }
}
