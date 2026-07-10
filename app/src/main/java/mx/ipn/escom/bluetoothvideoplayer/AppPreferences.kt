package mx.ipn.escom.bluetoothvideoplayer

import android.content.Context

enum class AppPalette {
    GUINDA,
    AZUL
}

enum class AppAppearance {
    SYSTEM,
    LIGHT,
    DARK
}

class AppPreferences(
    context: Context
) {
    companion object {
        private const val PREFERENCES_NAME =
            "bluetooth_video_player_preferences"

        private const val KEY_PALETTE =
            "palette"

        private const val KEY_APPEARANCE =
            "appearance"

        private const val KEY_PRIVATE_MODE =
            "private_mode"
    }

    private val preferences =
        context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )

    var palette: AppPalette
        get() = enumValueOrDefault(
            value = preferences.getString(
                KEY_PALETTE,
                AppPalette.GUINDA.name
            ),
            defaultValue = AppPalette.GUINDA
        )
        set(value) {
            preferences.edit()
                .putString(KEY_PALETTE, value.name)
                .apply()
        }

    var appearance: AppAppearance
        get() = enumValueOrDefault(
            value = preferences.getString(
                KEY_APPEARANCE,
                AppAppearance.SYSTEM.name
            ),
            defaultValue = AppAppearance.SYSTEM
        )
        set(value) {
            preferences.edit()
                .putString(KEY_APPEARANCE, value.name)
                .apply()
        }

    var privateMode: Boolean
        get() = preferences.getBoolean(
            KEY_PRIVATE_MODE,
            false
        )
        set(value) {
            preferences.edit()
                .putBoolean(KEY_PRIVATE_MODE, value)
                .apply()
        }

    private inline fun <reified T : Enum<T>>
        enumValueOrDefault(
            value: String?,
            defaultValue: T
        ): T {
        return value
            ?.let { storedValue ->
                enumValues<T>().firstOrNull {
                    it.name == storedValue
                }
            }
            ?: defaultValue
    }
}
