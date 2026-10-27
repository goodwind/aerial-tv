package app.orchard.tv.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import app.orchard.tv.wallpaper.Images
import kotlin.math.roundToInt

/**
 * The wallpaper frosted behind the shelf, over its sharp picture, shaded the way Home shades a
 * wallpaper so the shelf's white text reads: it comes in with [show] (the shelf's progress, 0 to
 * 1). It is the glass the grid and the panels already show, so the panels over the shelf lie on the
 * same picture, and the top row's plate is left as it is. Only the draw phase reads [show], so
 * nothing here recomposes while it runs.
 */
@Composable
fun ShelfBlur(frosted: ImageBitmap?, show: () -> Float) {
    if (frosted == null) return
    Canvas(Modifier.fillMaxSize()) {
        val shown = show()
        if (shown <= 0.01f) return@Canvas
        drawImage(frosted, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), alpha = shown)
        drawRect(Brush.verticalGradient(*Images.SHADE_AT.indices.map { Images.SHADE_AT[it] to Color.Black.copy(alpha = Images.SHADE[it]) }.toTypedArray()), alpha = shown)
        drawRect(Brush.horizontalGradient(0f to Color.Black.copy(alpha = Images.SHADE_LEFT), 1f to Color.Transparent, endX = size.width / 2), alpha = shown)
    }
}
