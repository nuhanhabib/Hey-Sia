package com.heysia.app.actions

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.view.KeyEvent
import android.app.SearchManager
import android.media.AudioManager
import androidx.core.content.ContextCompat
import com.heysia.app.brain.Fuzzy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Plays music:
 * - offline: songs saved on your phone, opened in your music app
 * - online: YouTube, preferring YouTube Vanced / ReVanced if installed
 * Also pause / resume / next / previous for whatever is playing.
 */
class Music(private val context: Context, private val apps: AppLauncher) {

    data class Song(val title: String, val artist: String?, val uri: Uri)

    // ---------- Your songs (offline) ----------

    val canReadSongs: Boolean
        get() = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED

    /** Best matching song saved on the phone, searching titles and artists. Reads only music files. */
    fun findLocal(query: String): Song? {
        if (!canReadSongs) return null
        val songs = mutableListOf<Song>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
                val artist = c.getString(2)?.takeUnless { it == MediaStore.UNKNOWN_STRING }
                songs += Song(c.getString(1) ?: continue, artist, uri)
            }
        }
        return Fuzzy.best(query, songs, threshold = 0.72) { song ->
            listOfNotNull(song.title, song.artist, song.artist?.let { "${song.title} $it" })
        }?.first
    }

    /** Opens the song in your default music app. */
    fun playLocal(song: Song): Boolean = Screens.open(
        context,
        Intent(Intent.ACTION_VIEW).setDataAndType(song.uri, "audio/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    )

    /** "Play some music": opens your music app. */
    fun openMusicApp(): Boolean =
        Screens.open(context, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC))

    // ---------- YouTube (online) ----------

    /** The YouTube app to use, preferring ReVanced / Vanced. Null if none is installed. */
    fun youTubeApp(): String? = YOUTUBE_APPS.firstOrNull { apps.isInstalled(it) }

    enum class YouTubeResult { Playing, ShowingSearch, Failed }

    /**
     * Finds the top video for [query] and plays it. Uses YouTube's public search page (no account,
     * no API key). If that fails, opens the search results instead.
     */
    suspend fun playOnYouTube(query: String): YouTubeResult {
        val pkg = youTubeApp()
        val videoId = findVideoId(query)
        if (videoId != null) {
            val watch = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId"))
            if (pkg != null) watch.setPackage(pkg)
            if (Screens.open(context, watch)) return YouTubeResult.Playing
        }
        if (pkg != null) {
            val search = Intent(Intent.ACTION_SEARCH).setPackage(pkg).putExtra(SearchManager.QUERY, query)
            if (Screens.open(context, search)) return YouTubeResult.ShowingSearch
        }
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + encode(query)))
        return if (Screens.open(context, web)) YouTubeResult.ShowingSearch else YouTubeResult.Failed
    }

    private suspend fun findVideoId(query: String): String? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
            try {
                val url = URL("https://www.youtube.com/results?search_query=" + encode(query))
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept-Language", "en")
                }
                val html = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                VIDEO_ID.find(html)?.groupValues?.get(1)
            } catch (e: Exception) {
                null
            }
        }
    }

    // ---------- Playback controls ----------

    fun pause() = mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
    fun resume() = mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
    fun next() = mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    fun previous() = mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    /** Acts like pressing the button on headphones: works with any app that's playing. */
    private fun mediaKey(code: Int) {
        val audio = context.getSystemService(AudioManager::class.java)
        val time = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, code, 0))
    }

    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        val audioPermission: String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE

        /** Preferred first. */
        val YOUTUBE_APPS = listOf(
            "app.revanced.android.youtube",
            "com.vanced.android.youtube",
            "com.google.android.youtube"
        )
        private val VIDEO_ID = Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"")
        private const val LOOKUP_TIMEOUT_MS = 8000L
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
    }
}
