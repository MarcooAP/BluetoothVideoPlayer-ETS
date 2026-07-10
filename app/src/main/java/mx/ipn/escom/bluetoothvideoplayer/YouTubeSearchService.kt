package mx.ipn.escom.bluetoothvideoplayer

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Html
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread

data class YouTubeVideoResult(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String
)

class YouTubeSearchService(
    private val context: Context
) {

    private val mainHandler =
        Handler(Looper.getMainLooper())

    fun searchVideos(
        query: String,
        onSuccess: (List<YouTubeVideoResult>) -> Unit,
        onError: (String) -> Unit
    ) {
        val cleanQuery = query.trim()

        if (cleanQuery.isBlank()) {
            onError("Escribe algo para buscar.")
            return
        }

        if (BuildConfig.YOUTUBE_API_KEY.isBlank()) {
            onError(
                "No se encontró YOUTUBE_API_KEY en local.properties."
            )
            return
        }

        thread(
            name = "YouTubeSearchThread",
            start = true
        ) {
            try {
                val results =
                    executeSearch(cleanQuery)

                mainHandler.post {
                    onSuccess(results)
                }
            } catch (error: Exception) {
                mainHandler.post {
                    onError(
                        error.message
                            ?.takeIf { it.isNotBlank() }
                            ?: "No se pudo buscar en YouTube."
                    )
                }
            }
        }
    }

    private fun executeSearch(
        query: String
    ): List<YouTubeVideoResult> {
        val encodedQuery =
            URLEncoder.encode(
                query,
                StandardCharsets.UTF_8.name()
            )

        val encodedKey =
            URLEncoder.encode(
                BuildConfig.YOUTUBE_API_KEY,
                StandardCharsets.UTF_8.name()
            )

        val url =
            java.net.URL(
                "https://www.googleapis.com/youtube/v3/search" +
                        "?part=snippet" +
                        "&type=video" +
                        "&maxResults=5" +
                        "&safeSearch=moderate" +
                        "&regionCode=MX" +
                        "&relevanceLanguage=es" +
                        "&q=$encodedQuery" +
                        "&key=$encodedKey"
            )

        val connection =
            url.openConnection() as HttpsURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.useCaches = false

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            connection.setRequestProperty(
                "X-Android-Package",
                context.packageName
            )

            connection.setRequestProperty(
                "X-Android-Cert",
                getSigningCertificateSha1()
            )

            val responseCode =
                connection.responseCode

            val responseText =
                if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }?.bufferedReader(
                    StandardCharsets.UTF_8
                )?.use { reader ->
                    reader.readText()
                }.orEmpty()

            if (responseCode !in 200..299) {
                throw IllegalStateException(
                    readApiError(
                        responseCode = responseCode,
                        responseText = responseText
                    )
                )
            }

            return parseSearchResults(responseText)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSearchResults(
        responseText: String
    ): List<YouTubeVideoResult> {
        val root =
            JSONObject(responseText)

        val items =
            root.optJSONArray("items")
                ?: return emptyList()

        val results =
            mutableListOf<YouTubeVideoResult>()

        for (index in 0 until items.length()) {
            val item =
                items.optJSONObject(index)
                    ?: continue

            val id =
                item.optJSONObject("id")
                    ?: continue

            val snippet =
                item.optJSONObject("snippet")
                    ?: continue

            val videoId =
                id.optString("videoId")

            if (videoId.isBlank()) {
                continue
            }

            val thumbnails =
                snippet.optJSONObject("thumbnails")

            val thumbnailUrl =
                thumbnails
                    ?.optJSONObject("medium")
                    ?.optString("url")
                    ?.takeIf { it.isNotBlank() }
                    ?: thumbnails
                        ?.optJSONObject("default")
                        ?.optString("url")
                        .orEmpty()

            results += YouTubeVideoResult(
                videoId = videoId,
                title = decodeHtml(
                    snippet.optString("title")
                ),
                channelTitle = decodeHtml(
                    snippet.optString("channelTitle")
                ),
                thumbnailUrl = thumbnailUrl
            )
        }

        return results
    }

    private fun readApiError(
        responseCode: Int,
        responseText: String
    ): String {
        return try {
            val root =
                JSONObject(responseText)

            val error =
                root.optJSONObject("error")

            val message =
                error?.optString("message")
                    ?.takeIf { it.isNotBlank() }

            "YouTube respondió $responseCode: " +
                    (message ?: "error desconocido")
        } catch (_: Exception) {
            "YouTube respondió con el código $responseCode."
        }
    }

    private fun decodeHtml(
        text: String
    ): String {
        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.N
        ) {
            Html.fromHtml(
                text,
                Html.FROM_HTML_MODE_LEGACY
            ).toString()
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(text).toString()
        }
    }

    /**
     * El encabezado X-Android-Cert necesita el SHA-1
     * hexadecimal sin espacios, dos puntos ni prefijos.
     */
    @Suppress("DEPRECATION")
    private fun getSigningCertificateSha1(): String {
        val packageInfo =
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )

        val signature =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.P
            ) {
                packageInfo.signingInfo
                    ?.apkContentsSigners
                    ?.firstOrNull()
            } else {
                packageInfo.signatures
                    ?.firstOrNull()
            }
                ?: throw IllegalStateException(
                    "No se encontró el certificado de firma."
                )

        val digest =
            MessageDigest
                .getInstance("SHA-1")
                .digest(signature.toByteArray())

        return digest.joinToString(
            separator = ""
        ) { byte ->
            "%02X".format(byte)
        }
    }
}