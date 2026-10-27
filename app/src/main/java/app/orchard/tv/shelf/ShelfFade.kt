package app.orchard.tv.shelf

import android.graphics.Bitmap
import kotlin.math.roundToInt

/**
 * The shelf's poster as Home's background: whole at the top, fading to nothing by the bottom panel,
 * so the wallpaper shows under the panel and its glass has nothing to be recoloured for.
 */
object ShelfFade {
    /** From where (fraction of the height) the poster starts to fade, and where it is gone. */
    const val FROM = 0.45f
    const val TO = 0.72f

    /** How much of the poster is left at [at] (0 top, 1 bottom): a smooth fall between [from] and [to]. */
    fun alpha(at: Float, from: Float = FROM, to: Float = TO): Float {
        val t = ((at - from) / (to - from)).coerceIn(0f, 1f)
        return 1f - t * t * (3f - 2f * t)
    }

    /** A straight (not premultiplied) ARGB pixel at [a] of its strength: only its alpha scales. */
    fun scale(argb: Int, a: Float): Int =
        (((argb ushr 24) * a).roundToInt().coerceIn(0, 255) shl 24) or (argb and 0x00FFFFFF)

    /** [b] faded in place, row by row; it must be mutable ARGB_8888 (get/setPixels take and give straight colour). */
    fun apply(b: Bitmap): Bitmap {
        val w = b.width
        val row = IntArray(w)
        for (y in 0 until b.height) {
            val a = alpha(y.toFloat() / (b.height - 1).coerceAtLeast(1))
            if (a >= 1f) continue
            b.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) row[x] = scale(row[x], a)
            b.setPixels(row, 0, w, 0, y, w, 1)
        }
        return b
    }
}
