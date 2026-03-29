package com.astra.wakeup.ui

import java.util.concurrent.CopyOnWriteArraySet

data class CallState(
    val active: Boolean = false,
    val sessionId: String? = null,
    val phase: String = "idle",
    val lastUserText: String = "",
    val lastAssistantText: String = "",
    val lastTaskId: String? = null,
)

object CallStateRepository {
    private val listeners = CopyOnWriteArraySet<(CallState) -> Unit>()

    @Volatile
    private var state: CallState = CallState()

    fun get(): CallState = state

    fun update(transform: (CallState) -> CallState) {
        state = transform(state)
        for (listener in listeners) {
            runCatching { listener(state) }
        }
    }

    fun set(next: CallState) {
        state = next
        for (listener in listeners) {
            runCatching { listener(state) }
        }
    }

    fun subscribe(listener: (CallState) -> Unit): () -> Unit {
        listeners.add(listener)
        runCatching { listener(state) }
        return { listeners.remove(listener) }
    }
}
