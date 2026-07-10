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

    data class PlayRequest(
        val videoId: String,
        val title: String
    ) : BluetoothMessage()

    data class PlayPreparing(
        val videoId: String,
        val message: String
    ) : BluetoothMessage()

    data class BinaryTestRequest(
        val sizeBytes: Int
    ) : BluetoothMessage()

    data class BinaryTestInfo(
        val sizeBytes: Int,
        val sha256: String
    ) : BluetoothMessage()

    data class BinaryTestResult(
        val success: Boolean,
        val receivedBytes: Int,
        val sha256: String,
        val message: String
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
    private const val TYPE_PLAY_REQUEST = "PLAY_REQUEST"
    private const val TYPE_PLAY_PREPARING = "PLAY_PREPARING"
    private const val TYPE_BINARY_TEST_REQUEST = "BINARY_TEST_REQUEST"
    private const val TYPE_BINARY_TEST_INFO = "BINARY_TEST_INFO"
    private const val TYPE_BINARY_TEST_RESULT = "BINARY_TEST_RESULT"
    private const val TYPE_ERROR = "ERROR"

    fun hello(text: String): String {
        return JSONObject()
            .put("type", TYPE_HELLO)
            .put("text", text)
            .toString()
    }

    fun helloAck(text: String): String {
        return JSONObject()
            .put("type", TYPE_HELLO_ACK)
            .put("text", text)
            .toString()
    }

    fun searchRequest(query: String): String {
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
                    .put("channelTitle", result.channelTitle)
                    .put("thumbnailUrl", result.thumbnailUrl)
            )
        }

        return JSONObject()
            .put("type", TYPE_SEARCH_RESULTS)
            .put("items", items)
            .toString()
    }

    fun playRequest(
        videoId: String,
        title: String
    ): String {
        return JSONObject()
            .put("type", TYPE_PLAY_REQUEST)
            .put("videoId", videoId)
            .put("title", title)
            .toString()
    }

    fun playPreparing(
        videoId: String,
        message: String
    ): String {
        return JSONObject()
            .put("type", TYPE_PLAY_PREPARING)
            .put("videoId", videoId)
            .put("message", message)
            .toString()
    }

    fun binaryTestRequest(sizeBytes: Int): String {
        return JSONObject()
            .put("type", TYPE_BINARY_TEST_REQUEST)
            .put("sizeBytes", sizeBytes)
            .toString()
    }

    fun binaryTestInfo(
        sizeBytes: Int,
        sha256: String
    ): String {
        return JSONObject()
            .put("type", TYPE_BINARY_TEST_INFO)
            .put("sizeBytes", sizeBytes)
            .put("sha256", sha256)
            .toString()
    }

    fun binaryTestResult(
        success: Boolean,
        receivedBytes: Int,
        sha256: String,
        message: String
    ): String {
        return JSONObject()
            .put("type", TYPE_BINARY_TEST_RESULT)
            .put("success", success)
            .put("receivedBytes", receivedBytes)
            .put("sha256", sha256)
            .put("message", message)
            .toString()
    }

    fun error(message: String): String {
        return JSONObject()
            .put("type", TYPE_ERROR)
            .put("message", message)
            .toString()
    }

    fun parse(raw: String): BluetoothMessage {
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
                        root.optJSONArray("items") ?: JSONArray()

                    val results =
                        mutableListOf<YouTubeVideoResult>()

                    for (index in 0 until array.length()) {
                        val item =
                            array.optJSONObject(index) ?: continue

                        val videoId = item.optString("videoId")
                        if (videoId.isBlank()) continue

                        results += YouTubeVideoResult(
                            videoId = videoId,
                            title = item.optString("title"),
                            channelTitle =
                                item.optString("channelTitle"),
                            thumbnailUrl =
                                item.optString("thumbnailUrl")
                        )
                    }

                    BluetoothMessage.SearchResults(results)
                }

                TYPE_PLAY_REQUEST -> {
                    BluetoothMessage.PlayRequest(
                        videoId = root.optString("videoId"),
                        title = root.optString("title")
                    )
                }

                TYPE_PLAY_PREPARING -> {
                    BluetoothMessage.PlayPreparing(
                        videoId = root.optString("videoId"),
                        message = root.optString("message")
                    )
                }

                TYPE_BINARY_TEST_REQUEST -> {
                    BluetoothMessage.BinaryTestRequest(
                        sizeBytes = root.optInt("sizeBytes")
                    )
                }

                TYPE_BINARY_TEST_INFO -> {
                    BluetoothMessage.BinaryTestInfo(
                        sizeBytes = root.optInt("sizeBytes"),
                        sha256 = root.optString("sha256")
                    )
                }

                TYPE_BINARY_TEST_RESULT -> {
                    BluetoothMessage.BinaryTestResult(
                        success = root.optBoolean("success"),
                        receivedBytes =
                            root.optInt("receivedBytes"),
                        sha256 = root.optString("sha256"),
                        message = root.optString("message")
                    )
                }

                TYPE_ERROR -> {
                    BluetoothMessage.ErrorMessage(
                        message = root.optString("message")
                    )
                }

                else -> BluetoothMessage.Unknown(raw)
            }
        } catch (_: Exception) {
            BluetoothMessage.Unknown(raw)
        }
    }
}
