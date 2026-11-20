package com.activekids.settings

import android.content.Context
import android.content.SharedPreferences
import com.activekids.core.NoticeCenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Stores [AppSettings] in SharedPreferences. Bad stored values fall back to defaults, with a visible notice. */
class SettingsRepository(context: Context, private val notices: NoticeCenter) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings

    /** Applies [transform] and saves. Returns a user-facing error message instead of saving when invalid. */
    fun update(transform: (AppSettings) -> AppSettings): String? {
        val next = transform(_settings.value)
        next.validationErrors().firstOrNull()?.let { return it }
        if (!save(next)) return "Could not save settings. Try again."
        _settings.value = next
        return null
    }

    fun resetToDefaults(): String? = update { AppSettings() }

    private fun load(): AppSettings {
        var s = AppSettings(
            camera = readEnum(KEY_CAMERA, AppSettings().camera),
            resolution = readEnum(KEY_RESOLUTION, AppSettings().resolution),
            model = readEnum(KEY_MODEL, AppSettings().model),
            processor = readEnum(KEY_PROCESSOR, AppSettings().processor),
            showSkeleton = readBool(KEY_SKELETON, AppSettings().showSkeleton),
            mirrorView = readBool(KEY_MIRROR, AppSettings().mirrorView),
            soundOn = readBool(KEY_SOUND, AppSettings().soundOn),
        )
        for (spec in SettingSpecs.all) {
            if (!prefs.contains(spec.key)) continue
            val stored = try {
                prefs.getFloat(spec.key, spec.read(s))
            } catch (e: ClassCastException) {
                Float.NaN
            }
            val problem = spec.validate(stored)
            if (problem == null) {
                s = spec.write(s, stored)
            } else {
                notices.fallback(
                    key = "settings-${spec.key}",
                    userMessage = "Saved setting “${spec.label}” was invalid, so the default (${spec.format(spec.read(AppSettings()))}${spec.unit}) is used.",
                    area = "settings",
                    attempted = "load ${spec.key}",
                    reason = problem,
                    fallback = "default",
                )
            }
        }
        return s
    }

    private fun save(s: AppSettings): Boolean {
        val editor = prefs.edit()
            .putString(KEY_CAMERA, s.camera.name)
            .putString(KEY_RESOLUTION, s.resolution.name)
            .putString(KEY_MODEL, s.model.name)
            .putString(KEY_PROCESSOR, s.processor.name)
            .putBoolean(KEY_SKELETON, s.showSkeleton)
            .putBoolean(KEY_MIRROR, s.mirrorView)
            .putBoolean(KEY_SOUND, s.soundOn)
        SettingSpecs.all.forEach { editor.putFloat(it.key, it.read(s)) }
        val ok = editor.commit()
        if (!ok) {
            notices.error("settings-save", "Could not save settings.", "settings", "SharedPreferences commit returned false")
        } else {
            notices.clear("settings-save")
        }
        return ok
    }

    private inline fun <reified E : Enum<E>> readEnum(key: String, default: E): E {
        val raw = try {
            prefs.getString(key, null)
        } catch (e: ClassCastException) {
            "<wrong type>"
        } ?: return default
        return enumValues<E>().firstOrNull { it.name == raw } ?: default.also {
            notices.fallback(
                key = "settings-$key",
                userMessage = "A saved setting ($key) was not recognized, so the default is used.",
                area = "settings",
                attempted = "load $key",
                reason = "unknown value \"$raw\"",
                fallback = default.name,
            )
        }
    }

    private fun readBool(key: String, default: Boolean): Boolean = try {
        prefs.getBoolean(key, default)
    } catch (e: ClassCastException) {
        notices.fallback("settings-$key", "A saved setting ($key) was unreadable, so the default is used.", "settings", "load $key", "wrong type", default.toString())
        default
    }

    private companion object {
        const val PREFS = "active_kids_settings"
        const val KEY_CAMERA = "camera"
        const val KEY_RESOLUTION = "resolution"
        const val KEY_MODEL = "model"
        const val KEY_PROCESSOR = "processor"
        const val KEY_SKELETON = "showSkeleton"
        const val KEY_MIRROR = "mirrorView"
        const val KEY_SOUND = "soundOn"
    }
}
