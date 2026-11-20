package com.activekids.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class NoticeLevel { INFO, WARNING, ERROR }

/** A message shown to the parent on the phone (and, for fallbacks, as a small badge on the TV). */
data class Notice(
    val key: String,
    val level: NoticeLevel,
    val message: String,
    val fallback: Boolean = false,
)

/**
 * The single place where handled errors and fallbacks go. Every call both logs (this app has no
 * remote error tracker, so errors log at ERROR and fallbacks at WARN with `fallback=true`) and
 * puts a user-visible notice on screen.
 */
class NoticeCenter {
    private val _items = MutableStateFlow<List<Notice>>(emptyList())
    val items: StateFlow<List<Notice>> = _items

    fun error(
        key: String,
        userMessage: String,
        area: String,
        detail: String,
        throwable: Throwable? = null,
        context: Map<String, Any?> = emptyMap(),
    ) {
        Log.e(TAG, "[$area] $detail${formatContext(context)}", throwable)
        post(Notice(key, NoticeLevel.ERROR, userMessage))
    }

    fun fallback(
        key: String,
        userMessage: String,
        area: String,
        attempted: String,
        reason: String,
        fallback: String,
        context: Map<String, Any?> = emptyMap(),
    ) {
        Log.w(
            TAG,
            "[$area] fallback=true attempted=\"$attempted\" reason=\"$reason\" fallback=\"$fallback\"" +
                formatContext(context),
        )
        post(Notice(key, NoticeLevel.WARNING, userMessage, fallback = true))
    }

    fun info(key: String, message: String) = post(Notice(key, NoticeLevel.INFO, message))

    fun post(notice: Notice) = _items.update { list -> list.filterNot { it.key == notice.key } + notice }

    fun clear(key: String) = _items.update { list -> list.filterNot { it.key == key } }

    private fun formatContext(context: Map<String, Any?>) =
        if (context.isEmpty()) "" else context.entries.joinToString(" ", prefix = " ") { "${it.key}=${it.value}" }

    companion object {
        const val TAG = "ActiveKids"
    }
}
