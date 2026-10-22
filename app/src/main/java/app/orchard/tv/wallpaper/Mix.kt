package app.orchard.tv.wallpaper

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.orchard.tv.settings.AerialSettings
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The auto wallpaper: a new picture from Bing, Wikimedia Commons and NASA every few minutes while
 * Home is up, never the same one twice until all have been shown. The choosing and the
 * filters are in [MixPick] and [MixSources]; this is the files, the network and the clock.
 *
 * In `filesDir/mix`: `pool.json` (candidates), `history.json` (ids shown in this round),
 * `state.json` (the picture on the wall, when it went up, and the one fetched for next), and the
 * pictures themselves. At most those two are kept. State is Compose snapshot state, written on
 * the main thread only, as in [Wallpapers].
 */
object Mix {
    private const val TAG = "AerialMix"
    private const val POOL_REFRESH_MS = 6 * 60 * 60 * 1000L
    private const val RETRY_MS = 5 * 60 * 1000L
    private const val MAX_BYTES = 25 * 1024 * 1024
    private const val MAX_PER_SOURCE = 300
    private const val TRIES = 5

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val dir by lazy { File(app.filesDir, "mix").apply { mkdirs() } }
    private var started = false

    /** The picture on the wall now, as a wallpaper of its own (id "mix:<picture id>"); null until the first. */
    var current by mutableStateOf<Wallpaper?>(null); private set

    private var currentPicture: MixPicture? = null
    private var changedAt = 0L
    private var next: MixPicture? = null
    private var pool = emptyList<MixPicture>()
    private var shown = emptySet<String>()
    private var preparing = false
    private var poolBusy = false
    private var poolCheckedAt = 0L
    private var poolFetchedFor = emptySet<MixSource>()
    private var failedAt = 0L

    fun init(context: Context) {
        if (started) return
        started = true
        app = context.applicationContext
        pool = MixPick.readPictures(File(dir, "pool.json").takeIf { it.exists() }?.readText())
        shown = MixPick.parseHistory(File(dir, "history.json").takeIf { it.exists() }?.readText())
        runCatching {
            val state = JSONObject(File(dir, "state.json").readText())
            currentPicture = MixPick.readPicture(state.optJSONObject("current")?.toString())?.takeIf { fileOf(it).exists() }
            changedAt = state.optLong("at")
            next = MixPick.readPicture(state.optJSONObject("next")?.toString())?.takeIf { fileOf(it).exists() }
        }
        current = currentPicture?.let(::wallpaperOf)
    }

    /** Where a wallpaper of this kind keeps its picture; null if it is not one of ours. */
    fun fileFor(id: String): File? {
        val pictureId = id.removePrefix("mix:")
        val p = listOfNotNull(currentPicture, next).firstOrNull { it.id == pictureId } ?: return null
        return fileOf(p)
    }

    private fun fileOf(p: MixPicture) = File(dir, Integer.toHexString(p.id.hashCode()) + ".jpg")

    private fun wallpaperOf(p: MixPicture) = Wallpaper("mix:${p.id}", WallpaperKind.Mix, p.title, p.credit)

    /** The auto wallpaper was chosen: have a picture, and the pool behind it, ready. */
    fun onSelected() {
        refreshPoolIfStale()
        rotateIfDue()
    }

    /** Called whenever Home is up, and by a timer while it stays up. */
    fun rotateIfDue() {
        if (!started || Wallpapers.selectedId != Wallpapers.MIX) return
        val due = current == null || MixPick.isDue(changedAt, System.currentTimeMillis(), AerialSettings.mixMinutes)
        val ready = next
        when {
            due && ready != null -> show(ready)
            due || ready == null -> prepare(showWhenReady = due)
        }
    }

    private fun show(p: MixPicture) {
        currentPicture = p
        changedAt = System.currentTimeMillis()
        next = null
        current = wallpaperOf(p)
        saveState()
        Wallpapers.mixChanged()
        val keep = setOf(fileOf(p).name)
        scope.launch(Dispatchers.IO) {
            dir.listFiles { f -> f.extension == "jpg" && f.name !in keep }?.forEach { it.delete() }
        }
        prepare(showWhenReady = false)
    }

    /** Fetches the next picture, a few candidates at most; the first to download and decode wins. */
    private fun prepare(showWhenReady: Boolean) {
        if (preparing || System.currentTimeMillis() - failedAt < RETRY_MS && failedAt != 0L) return
        val sources = AerialSettings.mixSources
        val candidates = MixPick.pool(pool.filter { it.source in sources })
        if (candidates.isEmpty()) {
            refreshPoolIfStale(force = true)
            return
        }
        preparing = true
        scope.launch {
            var found: MixPicture? = null
            repeat(TRIES) {
                if (found != null) return@repeat
                val pick = MixPick.next(candidates, shown, currentPicture?.id, Random) ?: return@repeat
                shown = pick.shown
                val ok = withContext(Dispatchers.IO) { download(pick.picture) }
                shown = MixPick.afterDownload(ok, pick.picture.id, shown)
                if (ok) found = pick.picture
            }
            preparing = false
            saveHistory()
            val p = found
            if (p == null) {
                failedAt = System.currentTimeMillis()
                Log.w(TAG, "no picture could be fetched; trying again in a few minutes")
                return@launch
            }
            failedAt = 0L
            if (showWhenReady) show(p) else { next = p; saveState() }
        }
    }

    /** True if [p] is in its file now: a picture of at least 1920 px that decodes. */
    private fun download(p: MixPicture): Boolean = runCatching {
        val bytes = fetch(p.url)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth < 1920) {
            Log.i(TAG, "${p.id}: ${bounds.outWidth} px wide, skipped")
            return@runCatching false
        }
        if (bounds.outWidth < 3840) Log.i(TAG, "${p.id}: only ${bounds.outWidth} px wide")
        val file = fileOf(p)
        val tmp = File(dir, file.name + ".part")
        tmp.writeBytes(bytes)
        tmp.renameTo(file)
    }.onFailure { Log.w(TAG, "${p.id} not fetched", it) }.getOrDefault(false)

    private fun fetch(url: String, redirects: Int = 3): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", USER_AGENT)
            c.instanceFollowRedirects = false
            if (c.responseCode in 301..308 && redirects > 0) {
                val to = URL(URL(url), c.getHeaderField("Location")).toString()
                return fetch(to, redirects - 1)
            }
            if (c.contentLengthLong > MAX_BYTES) error("too big: ${c.contentLengthLong}")
            return c.inputStream.use { it.readBytes() }.also { if (it.size > MAX_BYTES) error("too big") }
        } finally {
            c.disconnect()
        }
    }

    private val USER_AGENT = "aerial-tv (https://github.com/pengchujin/aerial-tv)"

    // ---- the pool ----

    /** The candidates are asked for again every six hours, or at once when sources were added or there are none. */
    fun refreshPoolIfStale(force: Boolean = false) {
        if (!started || poolBusy) return
        val sources = AerialSettings.mixSources
        val now = System.currentTimeMillis()
        val stale = now - poolCheckedAt >= POOL_REFRESH_MS || !poolFetchedFor.containsAll(sources)
        val retry = pool.none { it.source in sources } && now - poolCheckedAt >= RETRY_MS
        if (!stale && !retry && !force) return
        if (force && now - poolCheckedAt < RETRY_MS && poolFetchedFor.containsAll(sources)) return
        poolBusy = true
        poolCheckedAt = now
        scope.launch {
            val fresh = withContext(Dispatchers.IO) { sources.flatMap { runCatching { load(it) }.onFailure { e -> Log.w(TAG, "$it unavailable", e) }.getOrDefault(emptyList()) } }
            poolBusy = false
            poolFetchedFor = sources
            val merged = (fresh + pool).distinctBy { it.id }
                .groupBy { it.source }.values.flatMap { it.take(MAX_PER_SOURCE) }
            if (merged.isNotEmpty()) {
                pool = merged
                withContext(Dispatchers.IO) { File(dir, "pool.json").writeText(MixPick.writePictures(merged)) }
            }
            if (Wallpapers.selectedId == Wallpapers.MIX) rotateIfDue()
        }
    }

    private fun load(source: MixSource): List<MixPicture> = when (source) {
        MixSource.Bing -> {
            val market = if (Locale.getDefault().language == "zh") "zh-CN" else "en-US"
            listOf(0, 8).flatMap { runCatching { MixSources.parseBing(text(MixSources.bingUrl(it, market))) }.getOrDefault(emptyList()) }
        }
        MixSource.Commons -> MixSources.COMMONS_CATEGORIES.flatMap { category ->
            val cursors = cursors()
            val json = text(MixSources.commonsUrl(category, cursors.optString(category).ifEmpty { null }))
            val next = JSONObject(json).optJSONObject("continue")?.optString("gcmcontinue").orEmpty()
            saveCursor(category, next)
            MixSources.parseCommons(json)
        }
        MixSource.Nasa -> {
            val query = MixSources.NASA_QUERIES.random()
            MixSources.parseNasa(text(MixSources.nasaUrl(query, Random.nextInt(1, 4))))
        }
    }

    private fun text(url: String) = fetch(url).decodeToString()

    private fun cursors(): JSONObject = runCatching { JSONObject(File(dir, "cursors.json").readText()) }.getOrDefault(JSONObject())

    /** Where the next page of a Commons category starts; an empty cursor sends the list back to its beginning. */
    private fun saveCursor(category: String, cursor: String) {
        File(dir, "cursors.json").writeText(cursors().put(category, cursor).toString())
    }

    // ---- files ----

    private fun saveHistory() {
        val text = MixPick.writeHistory(shown)
        scope.launch(Dispatchers.IO) { File(dir, "history.json").writeText(text) }
    }

    private fun saveState() {
        val state = JSONObject().put("at", changedAt)
        currentPicture?.let { state.put("current", JSONObject(MixPick.writePicture(it))) }
        next?.let { state.put("next", JSONObject(MixPick.writePicture(it))) }
        val text = state.toString()
        scope.launch(Dispatchers.IO) { File(dir, "state.json").writeText(text) }
    }
}
