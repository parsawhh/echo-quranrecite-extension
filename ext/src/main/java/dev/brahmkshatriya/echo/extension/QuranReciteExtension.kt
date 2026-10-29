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
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

class QuranReciteExtension : ExtensionClient, SearchFeedClient, TrackClient {

    private lateinit var settings: Settings
    private val httpClient = OkHttpClient.Builder().build()
    private val baseUrl = "https://music.quranrecite.sbs"
    private val json = Json { ignoreUnknownKeys = true }

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
                    val root = json.parseToJsonElement(body).jsonObject
                    val tracksArray = root["tracks"]?.jsonArray
                    if (tracksArray != null) {
                        for (element in tracksArray) {
                            val item = element.jsonObject
                            val id = item["id"]?.jsonPrimitive?.contentOrNull?.replace("yt_", "") ?: continue
                            val title = item["title"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
                            val artist = item["artist"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
                            val album = item["album"]?.jsonPrimitive?.contentOrNull ?: ""
                            val art = item["artworkURL"]?.jsonPrimitive?.contentOrNull ?: ""
                            val durationSeconds = item["duration"]?.jsonPrimitive?.longOrNull ?: 0L

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
                listOf(Shelf.Lists.Tracks(id = "tracks", title = "Tracks", list = tracks) as Shelf)
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

    override suspend fun loadFeed(track: Track): Feed<Shelf>? {
        return null
    }
}
