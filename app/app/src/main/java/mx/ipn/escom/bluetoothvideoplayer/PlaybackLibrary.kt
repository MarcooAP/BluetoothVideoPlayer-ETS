package mx.ipn.escom.bluetoothvideoplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PlaybackEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val filePath: String,
    val playedAt: Long,
    val updatedAt: Long,
    val favorite: Boolean
)

class PlaybackLibrary(
    context: Context
) {
    companion object {
        private const val PREFERENCES_NAME =
            "bluetooth_video_player_library"

        private const val KEY_ENTRIES =
            "entries"

        private const val MAX_ENTRIES = 80

        fun localKey(filePath: String): String {
            return "local:$filePath"
        }

        fun youtubeKey(videoId: String): String {
            return "youtube:$videoId"
        }
    }

    private val preferences =
        context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )

    @Synchronized
    fun recordPlayback(
        title: String,
        filePath: String,
        subtitle: String = "Recibido por Bluetooth"
    ) {
        if (filePath.isBlank()) {
            return
        }

        val now = System.currentTimeMillis()
        val key = localKey(filePath)
        val entries = readEntries()
        val existing = entries.firstOrNull {
            it.key == key
        }

        entries.removeAll {
            it.key == key
        }

        entries.add(
            PlaybackEntry(
                key = key,
                title = title.ifBlank {
                    "Video recibido"
                },
                subtitle = subtitle,
                filePath = filePath,
                playedAt = now,
                updatedAt = now,
                favorite = existing?.favorite == true
            )
        )

        writeEntries(normalize(entries))
    }

    @Synchronized
    fun toggleFavorite(
        key: String,
        title: String,
        subtitle: String = "",
        filePath: String = ""
    ): Boolean {
        val now = System.currentTimeMillis()
        val entries = readEntries()
        val existing = entries.firstOrNull {
            it.key == key
        }

        val newFavoriteValue =
            !(existing?.favorite ?: false)

        entries.removeAll {
            it.key == key
        }

        if (
            newFavoriteValue ||
            (existing?.playedAt ?: 0L) > 0L
        ) {
            entries.add(
                PlaybackEntry(
                    key = key,
                    title = title.ifBlank {
                        existing?.title
                            ?: "Video"
                    },
                    subtitle = subtitle.ifBlank {
                        existing?.subtitle.orEmpty()
                    },
                    filePath = filePath.ifBlank {
                        existing?.filePath.orEmpty()
                    },
                    playedAt = existing?.playedAt ?: 0L,
                    updatedAt = now,
                    favorite = newFavoriteValue
                )
            )
        }

        writeEntries(normalize(entries))
        return newFavoriteValue
    }

    @Synchronized
    fun isFavorite(key: String): Boolean {
        return readEntries().any {
            it.key == key && it.favorite
        }
    }

    @Synchronized
    fun history(): List<PlaybackEntry> {
        return readEntries()
            .filter {
                it.playedAt > 0L
            }
            .sortedByDescending {
                it.playedAt
            }
    }

    @Synchronized
    fun favorites(): List<PlaybackEntry> {
        return readEntries()
            .filter {
                it.favorite
            }
            .sortedByDescending {
                it.updatedAt
            }
    }

    @Synchronized
    fun remove(key: String) {
        val entries = readEntries()
        entries.removeAll {
            it.key == key
        }
        writeEntries(entries)
    }

    @Synchronized
    fun clearHistory() {
        val remaining = readEntries()
            .mapNotNull { entry ->
                if (entry.favorite) {
                    entry.copy(
                        playedAt = 0L
                    )
                } else {
                    null
                }
            }

        writeEntries(remaining)
    }

    @Synchronized
    fun clearFavorites() {
        val remaining = readEntries()
            .mapNotNull { entry ->
                if (entry.playedAt > 0L) {
                    entry.copy(
                        favorite = false
                    )
                } else {
                    null
                }
            }

        writeEntries(remaining)
    }

    private fun normalize(
        entries: List<PlaybackEntry>
    ): List<PlaybackEntry> {
        val distinctEntries =
            linkedMapOf<String, PlaybackEntry>()

        entries
            .sortedByDescending {
                maxOf(
                    it.playedAt,
                    it.updatedAt
                )
            }
            .forEach { entry ->
                if (!distinctEntries.containsKey(entry.key)) {
                    distinctEntries[entry.key] = entry
                }
            }

        return distinctEntries.values
            .take(MAX_ENTRIES)
    }

    private fun readEntries(): MutableList<PlaybackEntry> {
        val raw = preferences.getString(
            KEY_ENTRIES,
            "[]"
        ) ?: "[]"

        return try {
            val array = JSONArray(raw)
            val entries =
                mutableListOf<PlaybackEntry>()

            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)
                    ?: continue

                val key = item.optString("key")
                if (key.isBlank()) {
                    continue
                }

                entries += PlaybackEntry(
                    key = key,
                    title = item.optString(
                        "title",
                        "Video"
                    ),
                    subtitle = item.optString(
                        "subtitle"
                    ),
                    filePath = item.optString(
                        "filePath"
                    ),
                    playedAt = item.optLong(
                        "playedAt"
                    ),
                    updatedAt = item.optLong(
                        "updatedAt"
                    ),
                    favorite = item.optBoolean(
                        "favorite"
                    )
                )
            }

            entries
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun writeEntries(
        entries: List<PlaybackEntry>
    ) {
        val array = JSONArray()

        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("key", entry.key)
                    .put("title", entry.title)
                    .put("subtitle", entry.subtitle)
                    .put("filePath", entry.filePath)
                    .put("playedAt", entry.playedAt)
                    .put("updatedAt", entry.updatedAt)
                    .put("favorite", entry.favorite)
            )
        }

        preferences.edit()
            .putString(
                KEY_ENTRIES,
                array.toString()
            )
            .apply()
    }
}
