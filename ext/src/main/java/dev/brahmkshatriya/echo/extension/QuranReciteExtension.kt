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
import org.json.JSONObject
import java.net.URLEncoder

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
        val encodedQuery = URLEncoder.encode(query.ifBlank { "top hits" }, "UTF-8")
        val searchUrl = "$baseUrl/search?q=$encodedQuery&quality=high"

        val request = Request.Builder().url(searchUrl).build()
        val tracks = mutableListOf<Track>()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val tracksArray = json.optJSONArray("tracks")
                    if (tracksArray != null) {
                        for (i in 0 until tracksArray.length()) {
                            val item = tracksArray.getJSONObject(i)
                            val id = item.optString("id").replace("yt_", "")
                            val title = item.optString("title", "Unknown")
                            val artist = item.optString("artist", "Unknown")
                            val album = item.optString("album", "")
                            val art = item.optString("artworkURL", "")
                            val durationSeconds = item.optLong("duration", 0)

                            val track = Track(
                                id = id,
                                title = title,
                                artists = listOf(Artist(id = "artist_$id", name = artist)),
                                album = if (album.isNotEmpty()) Album(id = "album_$id", title = album) else null,
                                duration = durationSeconds * 1000L,
                                cover = art.toImageHolder(),
                                streamables = listOf(
                                    Streamable.server(
                                        id = id,
                                        quality = 256,
                                        title = "High (256k AAC)"
                                    )
                                )
                            )
                            tracks.add(track)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Feed(
            listOf(Tab("Tracks", "Tracks"))
        ) { _ ->
            PagedData.Single {
                listOf(Shelf.Lists.Tracks(tracks))
            }.toFeedData()
        }
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
}
