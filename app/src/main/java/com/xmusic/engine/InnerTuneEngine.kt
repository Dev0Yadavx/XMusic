package com.xmusic.engine

import com.xmusic.engine.model.HomeShelf
import com.xmusic.engine.model.StreamAudioResult
import com.xmusic.engine.model.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class InnerTuneEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val vrUA = "com.google.android.apps.youtube.vr.oculus/1.37 (Linux; U; Android 12; Quest 3)"
    private val androidUA = "com.google.android.youtube/19.02.39 (Linux; U; Android 14)"

    suspend fun fetchHomeFeed(): List<HomeShelf> = withContext(Dispatchers.IO) {
        val shelves = mutableListOf<HomeShelf>()
        val payload = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "ANDROID")
                    put("clientVersion", "19.02.39")
                    put("hl", "en")
                    put("gl", "IN")
                })
            })
            put("browseId", "FEwhat_to_watch")
            put("params", "Egh0cmVuZGluZw%3D%3D")
        }

        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/browse?key=$apiKey")
            .header("User-Agent", androidUA)
            .header("X-YouTube-Client-Name", "3")
            .header("X-YouTube-Client-Version", "19.02.39")
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()

        try {
            val response = client.newCall(request).execute()
            val raw = response.body?.string() ?: return@withContext emptyList()
            val root = JSONObject(raw)

            val tracks = mutableListOf<TrackItem>()
            extractTracks(root, tracks, mutableSetOf())

            if (tracks.isNotEmpty()) {
                shelves.add(HomeShelf("Trending Music", tracks.take(15)))
                shelves.add(HomeShelf("Top Charts", tracks.drop(15).take(15)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext shelves
    }

    suspend fun search(query: String): List<TrackItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TrackItem>()
        val payload = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "ANDROID")
                    put("clientVersion", "19.02.39")
                    put("hl", "en")
                    put("gl", "IN")
                })
            })
            put("query", query)
        }

        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/search?key=$apiKey")
            .header("User-Agent", androidUA)
            .header("X-YouTube-Client-Name", "3")
            .header("X-YouTube-Client-Version", "19.02.39")
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()

        try {
            val response = client.newCall(request).execute()
            val raw = response.body?.string() ?: return@withContext emptyList()
            val root = JSONObject(raw)
            extractTracks(root, results, mutableSetOf())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext results
    }

    suspend fun resolveStream(videoId: String): StreamAudioResult? = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "ANDROID_VR")
                    put("clientVersion", "1.37")
                    put("hl", "en")
                    put("gl", "IN")
                })
            })
            put("playbackContext", JSONObject().apply {
                put("contentPlaybackContext", JSONObject().apply {
                    put("signatureTimestamp", 20702)
                })
            })
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }

        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?key=$apiKey")
            .header("User-Agent", vrUA)
            .header("X-YouTube-Client-Name", "28")
            .header("X-YouTube-Client-Version", "1.37")
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()

        try {
            val response = client.newCall(request).execute()
            val root = JSONObject(response.body?.string() ?: return@withContext null)

            val streamingData = root.optJSONObject("streamingData") ?: return@withContext null
            val loudness = root.optJSONObject("playerConfig")
                ?.optJSONObject("audioConfig")?.optDouble("loudnessDb", 0.0) ?: 0.0

            val hls = streamingData.optString("hlsManifestUrl")
            if (hls.isNotEmpty()) {
                return@withContext StreamAudioResult(url = hls, itag = 0, bitrate = 0, isLiveHls = true)
            }

            val formats = streamingData.optJSONArray("adaptiveFormats") ?: JSONArray()
            for (i in 0 until formats.length()) {
                val fmt = formats.getJSONObject(i)
                val mime = fmt.optString("mimeType")
                val url = fmt.optString("url")
                val itag = fmt.optInt("itag")

                if (url.isNotEmpty() && (itag == 251 || mime.startsWith("audio/"))) {
                    return@withContext StreamAudioResult(
                        url = url,
                        itag = itag,
                        bitrate = fmt.optInt("bitrate"),
                        isLiveHls = false,
                        loudnessDb = loudness
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    private fun extractTracks(node: Any?, results: MutableList<TrackItem>, seen: MutableSet<String>) {
        when (node) {
            is JSONObject -> {
                val vid = node.optString("videoId")
                if (vid.length == 11 && !seen.contains(vid)) {
                    var title = node.optJSONObject("title")?.optString("simpleText") ?: ""
                    if (title.isEmpty()) {
                        val runs = node.optJSONObject("title")?.optJSONArray("runs")
                        if (runs != null && runs.length() > 0) {
                            title = runs.getJSONObject(0).optString("text", "")
                        }
                    }

                    var artist = ""
                    val bylineRuns = node.optJSONObject("shortBylineText")?.optJSONArray("runs")
                        ?: node.optJSONObject("longBylineText")?.optJSONArray("runs")
                    if (bylineRuns != null && bylineRuns.length() > 0) {
                        artist = bylineRuns.getJSONObject(0).optString("text", "")
                    }

                    if (title.isNotEmpty()) {
                        seen.add(vid)
                        results.add(
                            TrackItem(
                                id = vid,
                                title = title,
                                artist = artist,
                                thumbnailUrl = "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
                            )
                        )
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    extractTracks(node.get(keys.next()), results, seen)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    extractTracks(node.get(i), results, seen)
                }
            }
        }
    }
}
