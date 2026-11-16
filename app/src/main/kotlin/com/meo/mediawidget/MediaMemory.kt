package com.meo.mediawidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Der gemerkte Stand einer App: reicht, um das Widget ohne laufende Session zu füllen. */
data class Remembered(
    val pkg: String,
    val title: String,
    val artist: String,
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean,
    /** Wann die App zuletzt zu spielen begann (ms seit 1970), 0 = nie. */
    val playingSince: Long,
    /** Letzte Aktivität, bestimmt die Reihenfolge. */
    val savedAt: Long
) {
    fun toJson(): String = JSONObject()
        .put("title", title).put("artist", artist)
        .put("pos", positionMs).put("dur", durationMs)
        .put("playing", playing).put("since", playingSince).put("saved", savedAt)
        .toString()

    companion object {
        fun fromJson(pkg: String, json: String): Remembered? = try {
            val o = JSONObject(json)
            Remembered(
                pkg = pkg,
                title = o.optString("title"), artist = o.optString("artist"),
                positionMs = o.optLong("pos"), durationMs = o.optLong("dur"),
                playing = o.optBoolean("playing"), playingSince = o.optLong("since"),
                savedAt = o.optLong("saved")
            )
        } catch (_: Exception) { null }
    }
}

/**
 * Gedächtnis des Widgets: pro App der zuletzt gesehene Titel samt Cover.
 * Liegt im privaten App-Speicher (SharedPreferences + JPEG-Dateien) und
 * überlebt so das Beenden der Media-App und einen Neustart des Telefons.
 */
object MediaMemory {

    private const val PREFS = "media_memory"
    private const val PREFS_SEEN = "media_seen"
    private const val KEY_SEEN = "packages"
    private const val KEY_NEEDS_OPEN = "needs_open"
    private const val MAX_ENTRIES = 6
    private const val COVER_PX = 512
    private val io = Executors.newSingleThreadExecutor()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun coverFile(ctx: Context, pkg: String) = File(File(ctx.filesDir, "covers").apply { mkdirs() }, "$pkg.jpg")

    fun get(ctx: Context, pkg: String): Remembered? =
        prefs(ctx).getString(pkg, null)?.let { Remembered.fromJson(pkg, it) }

    /** Alle Einträge, neueste Aktivität zuerst. */
    fun all(ctx: Context): List<Remembered> =
        prefs(ctx).all.mapNotNull { (pkg, v) -> (v as? String)?.let { Remembered.fromJson(pkg, it) } }
            .sortedByDescending { it.savedAt }

    fun cover(ctx: Context, pkg: String): Bitmap? = try {
        coverFile(ctx, pkg).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
    } catch (_: Throwable) { null }

    /**
     * Alle Apps, die je eine Session offen hatten — auch Anruf-Apps, die nie
     * im Widget landen. Daraus baut das App-Menü seine Liste.
     */
    fun seenPackages(ctx: Context): Set<String> =
        ctx.getSharedPreferences(PREFS_SEEN, Context.MODE_PRIVATE).getStringSet(KEY_SEEN, emptySet()).orEmpty()

    private fun noteSeen(ctx: Context, pkgs: List<String>) {
        val known = seenPackages(ctx)
        if (known.containsAll(pkgs)) return
        ctx.getSharedPreferences(PREFS_SEEN, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_SEEN, known + pkgs).apply()
    }

    /** Apps, die sich nicht unsichtbar wieder starten liessen: Play öffnet sie direkt. */
    fun needsOpen(ctx: Context, pkg: String): Boolean =
        pkg in ctx.getSharedPreferences(PREFS_SEEN, Context.MODE_PRIVATE).getStringSet(KEY_NEEDS_OPEN, emptySet()).orEmpty()

    fun setNeedsOpen(ctx: Context, pkg: String, needs: Boolean) {
        val p = ctx.getSharedPreferences(PREFS_SEEN, Context.MODE_PRIVATE)
        val old = p.getStringSet(KEY_NEEDS_OPEN, emptySet()).orEmpty()
        val new = if (needs) old + pkg else old - pkg
        if (new != old) p.edit().putStringSet(KEY_NEEDS_OPEN, new).apply()
    }

    fun forget(ctx: Context, pkg: String) {
        prefs(ctx).edit().remove(pkg).apply()
        coverFile(ctx, pkg).delete()
    }

    /**
     * Gleicht das Gedächtnis mit den aktuell lebenden Sessions ab.
     * @param keepDetails false = nur Zeitstempel führen (User hat "merken" ausgeschaltet).
     */
    fun sync(
        ctx: Context, controllers: List<MediaController>,
        keepDetails: Boolean, now: Long = System.currentTimeMillis()
    ) {
        val p = prefs(ctx)
        val edit = p.edit()
        val livePkgs = mutableSetOf<String>()
        noteSeen(ctx, controllers.map { it.packageName })

        for (c in controllers) {
            val md = c.metadata
            val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            if (title.isNullOrBlank()) continue
            if (SessionFilter.isCall(c.playbackInfo?.audioAttributes?.usage)) continue
            if (!livePkgs.add(c.packageName)) continue

            val old = get(ctx, c.packageName)
            val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
            val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
            val trackChanged = old == null || old.title != title || old.artist != artist
            val startedNow = playing && old?.playing != true

            val entry = Remembered(
                pkg = c.packageName,
                title = if (keepDetails) title else "",
                artist = if (keepDetails) artist else "",
                positionMs = c.playbackState?.position ?: 0L,
                durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
                playing = playing,
                playingSince = if (startedNow) now else old?.playingSince ?: 0L,
                savedAt = if (playing || trackChanged || old == null) now else old.savedAt
            )
            if (entry != old) edit.putString(c.packageName, entry.toJson())

            val file = coverFile(ctx, c.packageName)
            if (!keepDetails) {
                file.delete()
            } else if (trackChanged || !file.exists()) {
                val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                if (art != null) saveCover(file, art) else if (trackChanged) file.delete()
            }
        }

        // Apps, deren Session verschwunden ist, spielen nicht mehr.
        val all = all(ctx)
        all.filter { it.playing && it.pkg !in livePkgs }
            .forEach { edit.putString(it.pkg, it.copy(playing = false).toJson()) }

        // Älteste Einträge verwerfen, lebende nie.
        all.filter { it.pkg !in livePkgs }.drop((MAX_ENTRIES - livePkgs.size).coerceAtLeast(0))
            .forEach { edit.remove(it.pkg); coverFile(ctx, it.pkg).delete() }

        edit.apply()
    }

    private fun saveCover(file: File, art: Bitmap) {
        io.execute {
            try {
                val scale = COVER_PX.toFloat() / maxOf(art.width, art.height)
                val bmp = if (scale < 1f) Bitmap.createScaledBitmap(
                    art, (art.width * scale).toInt(), (art.height * scale).toInt(), true
                ) else art
                val tmp = File(file.path + ".tmp")
                tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                tmp.renameTo(file)
            } catch (_: Throwable) { /* Cover ist Zugabe, kein Muss */ }
        }
    }
}
