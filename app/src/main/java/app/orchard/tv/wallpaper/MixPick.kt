package app.orchard.tv.wallpaper

import kotlin.random.Random
import org.json.JSONArray

enum class MixSource(val key: String) {
    Bing("bing"), Commons("commons"), Apod("apod");

    companion object {
        fun of(key: String): MixSource? = entries.firstOrNull { it.key == key }
    }
}

/** [width] and [height] are 0 when the source does not say how big the picture is. */
data class MixPicture(
    val id: String,
    val source: MixSource,
    val url: String,
    val width: Int,
    val height: Int,
    val title: String,
    val credit: String,
)

/** Which picture the auto wallpaper shows next, and when. No Android here, so it is tested on the JVM. */
object MixPick {
    private const val UHD = 3840
    private const val HD = 1920

    class Result(val picture: MixPicture, val shown: Set<String>)

    /** Pictures at 3840 px or more (or of unknown size); if there are none, those of 1920 px or more. */
    fun pool(all: List<MixPicture>): List<MixPicture> {
        val uhd = all.filter { it.width == 0 || it.width >= UHD }
        return uhd.ifEmpty { all.filter { it.width >= HD } }
    }

    /**
     * A random picture of [pool] not in [shown]. When all have been shown the round starts again,
     * and the picture just shown ([last]) is not the first of it (unless it is the only one).
     */
    fun next(pool: List<MixPicture>, shown: Set<String>, last: String?, random: Random): Result? {
        if (pool.isEmpty()) return null
        val fresh = pool.filter { it.id !in shown }
        if (fresh.isNotEmpty()) return fresh.random(random).let { Result(it, shown + it.id) }
        val again = pool.filter { it.id != last }.ifEmpty { pool }
        return again.random(random).let { Result(it, setOf(it.id)) }
    }

    /** True once [intervalMinutes] have passed since the last change, or when the clock went back. */
    fun isDue(lastChangeMs: Long, nowMs: Long, intervalMinutes: Int): Boolean =
        nowMs < lastChangeMs || nowMs - lastChangeMs >= intervalMinutes * 60_000L

    fun parseHistory(text: String?): Set<String> = runCatching {
        val arr = JSONArray(text)
        (0 until arr.length()).map { arr.getString(it) }.toSet()
    }.getOrDefault(emptySet())

    fun writeHistory(shown: Set<String>): String = JSONArray(shown.toList()).toString()
}
