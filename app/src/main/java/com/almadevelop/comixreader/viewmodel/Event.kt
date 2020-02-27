package com.almadevelop.comixreader.viewmodel

/**
 * Wrapper around any [content] object with [handled] flag
 * Used with [androidx.lifecycle.LiveData]
 */
data class Event<T : Any>(val content: T) {
    var handled: Boolean = false
}

/**
 * Helper funtion to wrap object into [Event]
 */
fun <T : Any> T.intoEvent(): Event<T> = Event(this)