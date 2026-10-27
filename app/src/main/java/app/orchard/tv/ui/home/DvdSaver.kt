package app.orchard.tv.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.orchard.tv.R

/**
 * The bouncing DVD logo's motion: a point that flies on at a steady speed and turns back at the
 * edges of a box (the screen less the logo), [x] and [y] being the logo's top left corner.
 */
class DvdBounce(var x: Float, var y: Float, var vx: Float, var vy: Float) {
    /** Moves on by [dt] seconds within 0..[maxX] and 0..[maxY]; a pause longer than a crossing folds back as many times as it needs. */
    fun step(dt: Float, maxX: Float, maxY: Float) {
        val (nx, nvx) = fold(x, vx, dt, maxX)
        val (ny, nvy) = fold(y, vy, dt, maxY)
        x = nx; vx = nvx
        y = ny; vy = nvy
    }

    companion object {
        /** Where [p] ends up moving at [v] for [dt] in 0..[max], and its velocity there. */
        private fun fold(p: Float, v: Float, dt: Float, max: Float): Pair<Float, Float> {
            if (max <= 0f) return 0f to v
            val period = 2 * max
            // Unfolded the way is straight on; the box is a strip repeated back to back, every other copy mirrored.
            val q = ((p + v * dt) % period + period) % period
            return if (q <= max) q to v else (period - q) to -v
        }

        /** The hue, in degrees, [seconds] into a colour that goes once round the wheel in [secondsPerTurn]. */
        fun hue(seconds: Float, secondsPerTurn: Float): Float = (seconds % secondsPerTurn) / secondsPerTurn * 360f
    }
}

/** Black, and the DVD logo going round the screen, changing colour as it goes. */
@Composable
fun DvdSaver() {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        var logo by remember { mutableStateOf(IntSize.Zero) }
        val bounce = remember { DvdBounce(x = screenW * 0.15f, y = screenH * 0.2f, vx = screenW * 0.11f, vy = screenH * 0.13f) }
        var frame by remember { mutableLongStateOf(0L) }
        var seconds by remember { mutableStateOf(0f) }
        LaunchedEffect(logo, screenW, screenH) {
            if (logo == IntSize.Zero) return@LaunchedEffect
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    // A first frame, or one after a stall, moves nothing: the logo must not leap.
                    val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.1f)
                    last = now
                    seconds += dt
                    bounce.step(dt, screenW - logo.width, screenH - logo.height)
                    frame = now
                }
            }
        }
        Box(
            Modifier
                .onSizeChanged { logo = it }
                .graphicsLayer {
                    // The frame is read here, in the draw phase: the logo moves without recomposing.
                    if (frame >= 0L) {
                        translationX = bounce.x
                        translationY = bounce.y
                    }
                },
        ) {
            // The hue in steps of 2 degrees, too fine to see, so the logo recomposes a dozen times a second, not every frame.
            val hue by remember { derivedStateOf { (DvdBounce.hue(seconds, secondsPerTurn = 14f) / 2f).toInt() * 2f } }
            DvdLogo(Color.hsv(hue, 0.85f, 1f))
        }
    }
}

/** The DVD Video logo (res/drawable-nodpi/dvd_logo.png, black on clear), laid in [color]. */
@Composable
private fun DvdLogo(color: Color) {
    Image(
        painter = painterResource(R.drawable.dvd_logo),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = Modifier.width(LOGO_WIDTH),
    )
}

private val LOGO_WIDTH = 150.dp
