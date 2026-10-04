package com.example.wallpaperslideshow

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * Single source of truth for every user-tunable value.
 * The wallpaper service and the settings UI both go through here.
 */
object Prefs {

    const val KEY_FOLDER           = "folder_uri"
    const val KEY_DURATION         = "duration_seconds"
    const val KEY_TRANSITION       = "transition_type"
    const val KEY_TRANSITION_MS    = "transition_duration_ms"
    const val KEY_SHUFFLE          = "shuffle"

    const val DEFAULT_DURATION      = 10      // seconds per picture
    const val DEFAULT_TRANSITION    = "fade"
    const val DEFAULT_TRANSITION_MS = 800     // milliseconds
    const val DEFAULT_SHUFFLE       = false

    fun prefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    fun folderUri(context: Context): String? =
        prefs(context).getString(KEY_FOLDER, null)

    fun setFolderUri(context: Context, uri: String) {
        prefs(context).edit().putString(KEY_FOLDER, uri).apply()
    }

    /** How long each picture stays on screen, in seconds. */
    fun durationSeconds(context: Context): Int =
        prefs(context).getInt(KEY_DURATION, DEFAULT_DURATION).coerceAtLeast(1)

    /** none | fade | slide_left | slide_up | zoom */
    fun transition(context: Context): String =
        prefs(context).getString(KEY_TRANSITION, DEFAULT_TRANSITION) ?: DEFAULT_TRANSITION

    fun transitionMs(context: Context): Long =
        prefs(context).getInt(KEY_TRANSITION_MS, DEFAULT_TRANSITION_MS).toLong().coerceAtLeast(0L)

    fun shuffle(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHUFFLE, DEFAULT_SHUFFLE)
}
