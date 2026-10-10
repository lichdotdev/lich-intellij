package io.omartelo.lich.intellij

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/**
 * The few choices the plugin offers, kept per user, not per project. Registered
 * in plugin.xml rather than as a light service, which cannot be persistent state.
 */
@State(name = "LichSettings", storages = [Storage("lich.xml")])
class LichSettings : PersistentStateComponent<LichSettings.State> {
    class State {
        /** The `lich` command; empty looks for it on PATH and where it is usually installed. */
        var command: String = ""

        /** Wrap a selection in a fenced code block. */
        var fenced: Boolean = true

        /** Name files `@path` instead of `path`. */
        var atMention: Boolean = false

        /** Ask which session every time, even when there is only one. */
        var alwaysAsk: Boolean = false

        /** What a session opened from the IDE runs: a lich provider kind. */
        var newSessionKind: String = "claude"
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(): LichSettings = service()
    }
}
