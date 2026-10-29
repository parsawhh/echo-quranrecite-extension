package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.*
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeedData
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toServerMedia
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.Settings
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.regex.Pattern

class QuranReciteExtension : ExtensionClient, SearchFeedClient, TrackClient {

    private lateinit var settings: Settings
    private val httpClient = OkHttpClient.Builder().build()
    private val baseUrl = "https://music.quranrecite.sbs"

    override fun setSettings(settings: Settings) {
        this.settings = settings
    }

    override suspend fun getSettingItems(): List<Setting> = emptyList()

    override suspend fun onInitialize() {}

    override suspend fun loadSearchFeed(query: String): Feed<Shelf> {
        val cleanQuery = if (query.trim().isEmpty()) "top hits" else query
        val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
        val searchUrl = "$baseUrl/search?q=$encodedQuery&quality=high"

        val request = Request.Builder().url(searchUrl).build()
        val tracks = mutableListOf<Track>()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    parseTracksManually(body, tracks)
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        return Feed(
            listOf(Tab("Tracks", "Tracks"))
        ) { _ ->
            PagedData.Single {
                listOf(Shelf.Lists.Tracks(id = "tracks", title = "Tracks", list = tracks) as Shelf)
            }.toFeedData()
        }
    }

    private fun parseTracksManually(body: String, outTracks: MutableList<Track>) {
        val trackBlocks = body.split("{\"id\":")
        for (i in 1 until trackBlocks.size) {
            val block = trackBlocks[i]
            val id = extractJsonString(block, "\"id\":") ?: extractFirstString(block) ?: continue
            val cleanId = id.replace("yt_", "")
            val title = extractJsonField(block, "title") ?: "Unknown"
            val artist = extractJsonField(block, "artist") ?: "Unknown"
            val album = extractJsonField(block, "album") ?: ""
            val art = extractJsonField(block, "artworkURL") ?: ""
            val durationSec = extractJsonLong(block, "duration") ?: 0L

            val track = Track(
                id = cleanId,
                title = title,
                artists = listOf(Artist(id = "artist_$cleanId", name = artist)),
                album = if (album.isNotEmpty()) Album(id = "album_$cleanId", title = album) else null,
                duration = durationSec * 1000L,
                cover = art.toImageHolder(),
                streamables = listOf(
                    Streamable.server(
                        id = cleanId,
                        quality = 256,
                        title = "High (256k AAC)"
                    )
                )
            )
            outTracks.add(track)
        }
    }

    private fun extractFirstString(block: String): String? {
        val start = block.indexOf('"')
        if (start == -1) return null
        val end = block.indexOf('"', start + 1)
        if (end == -1) return null
        return block.substring(start + 1, end)
    }

    private fun extractJsonField(block: String, key: String): String? {
        val pattern = "\"$key\":\\s*\"([^\"]*)\""
        val matcher = Pattern.compile(pattern).matcher(block)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun extractJsonString(block: String, keyWithColon: String): String? {
        val idx = block.indexOf(keyWithColon)
        if (idx == -1) return null
        val sub = block.substring(idx + keyWithColon.length).trim()
        if (!sub.startsWith("\"")) return null
        val end = sub.indexOf('"', 1)
        if (end == -1) return null
        return sub.substring(1, end)
    }

    private fun extractJsonLong(block: String, key: String): Long? {
        val pattern = "\"$key\":\\s*([0-9]+)"
        val matcher = Pattern.compile(pattern).matcher(block)
        return if (matcher.find()) matcher.group(1)?.toLongOrNull() else null
    }

    override suspend fun loadTrack(track: Track, isDownload: Boolean): Track {
        return track
    }

    override suspend fun loadStreamableMedia(
        streamable: Streamable,
        isDownload: Boolean
    ): Streamable.Media {
        val streamUrl = "$baseUrl/audio/${streamable.id}"
        return streamUrl.toServerMedia(
            headers = mapOf("User-Agent" to "EchoPlayer/1.0"),
            type = Streamable.SourceType.Progressive
        )
    }

    override suspend fun loadFeed(track: Track): Feed<Shelf>? {
        return null
    }
}
