package app.orchard.tv.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MixSourcesTest {
    private fun fixture(name: String) = javaClass.getResource("/mix/$name")!!.readText()

    @Test
    fun bingMakesUhdUrl() {
        val first = MixSources.parseBing(fixture("bing.json")).first()
        assertEquals("bing:20261022", first.id)
        assertEquals(MixSource.Bing, first.source)
        assertEquals("https://cn.bing.com/th?id=OHR.NaejangsanNP_EN-US7010419904_UHD.jpg", first.url)
        assertEquals(3840, first.width)
        assertEquals(2160, first.height)
        assertEquals("Still waters, vivid colors", first.title)
        assertEquals("Autumn in Naejangsan National Park, South Korea (© Twenty47studio/Getty Images)", first.credit)
    }

    @Test
    fun nasaKeepsOriginalsAndDropsPortraitsAndMissingOnes() {
        val list = MixSources.parseNasa(fixture("nasa.json"))
        assertEquals(
            listOf("GSFC_20171208_Archive_e001465", "hubble-sees-the-wings-of-a-butterfly-the-twin-jet-nebula_20283986193_o", "GSFC_20171208_Archive_e001518", "photog")
                .map { "nasa:$it" },
            list.map { it.id },
        )
        assertTrue(list.all { it.url.startsWith("https://") && it.url.endsWith("~orig.jpg") })
        assertEquals(0 to 0, list[0].width to list[0].height)
        assertEquals(1280 to 1070, list[1].width to list[1].height)
    }

    @Test
    fun nasaCreditNamesThePhotographerWhenThereIsOne() {
        val list = MixSources.parseNasa(fixture("nasa.json"))
        assertEquals("NASA", list[0].credit)
        assertEquals("Jane Doe / NASA", list.last().credit)
    }

    @Test
    fun commonsKeepsOnlyWideJpegsInRange() {
        val list = MixSources.parseCommons(fixture("commons.json"))
        assertEquals(3, list.size)
        assertTrue(list.all { it.id.startsWith("commons:File:") && it.width >= 3840 })
        assertTrue(list.all { it.url.startsWith("https://thumb.wikimedia.org") })
    }

    @Test
    fun commonsStripsHtmlFromArtist() {
        val first = MixSources.parseCommons(fixture("commons.json")).first { it.id.startsWith("commons:File:S-charl") }
        assertEquals("Dominicus Johannes Bergsma. · CC BY-SA 4.0", first.credit)
    }

    @Test
    fun commonsWithoutArtistUsesLicenseOnly() {
        val obersaxen = MixSources.parseCommons(fixture("commons.json")).first { it.id.startsWith("commons:File:Obersaxen") }
        assertEquals("CC BY-SA 4.0", obersaxen.credit)
    }

    @Test
    fun plainTextDecodesEntitiesAndDropsTags() {
        assertEquals("A & B", MixSources.plainText("<b>A &amp; B</b>"))
    }

    @Test
    fun commonsUrlCarriesTheCursor() {
        assertTrue(MixSources.commonsUrl("Featured pictures of landscapes", null).contains("iiurlwidth=3840"))
        assertTrue(MixSources.commonsUrl("Featured pictures of landscapes", "20260608|1").contains("gcmcontinue="))
    }
}
