package app.orchard.tv.shelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfFadeTest {
    @Test fun `the poster is whole above the fade and gone below it`() {
        assertEquals(1f, ShelfFade.alpha(0f, from = 0.45f, to = 0.72f), 0f)
        assertEquals(1f, ShelfFade.alpha(0.45f, from = 0.45f, to = 0.72f), 0f)
        assertEquals(0f, ShelfFade.alpha(0.72f, from = 0.45f, to = 0.72f), 0f)
        assertEquals(0f, ShelfFade.alpha(1f, from = 0.45f, to = 0.72f), 0f)
    }

    @Test fun `between them it falls steadily and half way is half`() {
        assertEquals(0.5f, ShelfFade.alpha(0.585f, from = 0.45f, to = 0.72f), 0.001f)
        var last = 1f
        for (i in 46..71) {
            val a = ShelfFade.alpha(i / 100f, from = 0.45f, to = 0.72f)
            assertTrue("rises at $i%", a <= last)
            last = a
        }
    }

    @Test fun `fading a pixel scales its alpha and leaves its colour`() {
        // getPixels gives straight colour: 0xFF204060 at half is alpha 0x80 over the same colour
        assertEquals(0x80204060.toInt(), ShelfFade.scale(0xFF204060.toInt(), 0.5f))
        assertEquals(0x00FFFFFF, ShelfFade.scale(0xFFFFFFFF.toInt(), 0f))
        assertEquals(0xFF204060.toInt(), ShelfFade.scale(0xFF204060.toInt(), 1f))
    }
}
