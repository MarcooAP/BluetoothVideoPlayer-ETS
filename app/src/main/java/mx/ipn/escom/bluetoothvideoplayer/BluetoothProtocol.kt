package mx.ipn.escom.bluetoothvideoplayer

import org.json.JSONArray
import org.json.JSONObject

sealed class BluetoothMessage {

    data class Hello(
        val text: String
    ) : BluetoothMessage()

    data class HelloAck(
        val text: String
    ) : BluetoothMessage()

    data class SearchRequest(
        val query: String
    ) : BluetoothMessage()

    data class SearchResults(
        val items: List<YouTubeVideoResult>
    ) : BluetoothMessage()

    data class ErrorMessage(
        val message: String
    ) : BluetoothMessage()

    data class Unknown(
        val raw: String
    ) : BluetoothMessage()
}

object BluetoothProtocol {

    private const val TYPE_HELLO = "HELLO"
    private const val TYPE_HELLO_ACK = "HELLO_ACK"
    private const val TYPE_SEARCH_REQUEST = "SEARCH_REQUEST"
    private const val TYPE_SEARCH_RESULTS = "SEARCH_RESULTS"
    private const val TYPE_ERROR = "ERROR"

    fun hello(
        text: String
    ): String {
        return JSONObject()
            .put("type", TYPE_HELLO)
            .put("text", text)
            .toString()
    }

    fun helloAck(
        text: String
    ): String {
        return JSONObject()
            .put("type", TYPE_HELLO_ACK)
            .put("text", text)
            .toString()
    }

    fun searchRequest(
        query: String
    ): String {
        return JSONObject()
            .put("type", TYPE_SEARCH_REQUEST)
            .put("query", query.trim())
            .toString()
    }

    fun searchResults(
        results: List<YouTubeVideoResult>
    ): String {
        val items = JSONArray()

        results.forEach { result ->
            items.put(
                JSONObject()
                    .put("videoId", result.videoId)
                    .put("title", result.title)
                    .put(
                        "channelTitle",
                        result.channelTitle
                    )
                    .put(
                        "thumbnailUrl",
                        result.thumbnailUrl
                    )
            )
        }

        return JSONObject()
            .put("type", TYPE_SEARCH_RESULTS)
            .put("items", items)
            .toString()
    }

    fun error(
        message: String
    ): String {
        return JSONObject()
            .put("type", TYPE_ERROR)
            .put("message", message)
            .toString()
    }

    fun parse(
        raw: String
    ): BluetoothMessage {
        return try {
            val root = JSONObject(raw)

            when (root.optString("type")) {
                TYPE_HELLO -> {
                    BluetoothMessage.Hello(
                        text = root.optString("text")
                    )
                }

                TYPE_HELLO_ACK -> {
                    BluetoothMessage.HelloAck(
                        text = root.optString("text")
                    )
                }

                TYPE_SEARCH_REQUEST -> {
                    BluetoothMessage.SearchRequest(
                        query = root.optString("query")
                    )
                }

                TYPE_SEARCH_RESULTS -> {
                    val array =
                        root.optJSONArray("items")
                            ?: JSONArray()

                    val results =
                        mutableListOf<YouTubeVideoResult>()

                    for (index in 0 until array.length()) {
                        val item =
                            array.optJSONObject(index)
                                ?: continue

                        val videoId =
                            item.optString("videoId")

                        if (videoId.isBlank()) {
                            continue
                        }

                        results += YouTubeVideoResult(
                            videoId = videoId,
                            title =
                                item.optString("title"),
                            channelTitle =
                                item.optString(
                                    "channelTitle"
                                ),
                            thumbnailUrl =
                                item.optString(
                                    "thumbnailUrl"
                                )
                        )
                    }

                    BluetoothMessage.SearchResults(
                        items = results
                    )
                }

                TYPE_ERROR -> {
                    BluetoothMessage.ErrorMessage(
                        message =
                            root.optString("message")
                    )
                }

                else -> {
                    BluetoothMessage.Unknown(raw)
                }
            }
        } catch (_: Exception) {
            BluetoothMessage.Unknown(raw)
        }
    }
}