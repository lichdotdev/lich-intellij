package io.omartelo.lich.intellij

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toNullableProperty

/** Settings › Tools › lich. */
class LichConfigurable : BoundConfigurable("lich") {
    override fun createPanel(): DialogPanel {
        val state = LichSettings.getInstance().state
        return panel {
            row("lich command:") {
                textField()
                    .columns(40)
                    .bindText(state::command)
                    .comment("Leave empty to look for <code>lich</code> on PATH and where it is usually installed.")
            }
            row {
                checkBox("Wrap a selection in a fenced code block").bindSelected(state::fenced)
            }
            row {
                checkBox("Name files with an @ (<code>@src/Main.kt</code>)").bindSelected(state::atMention)
                    .comment("Claude Code and several other agents read it as a mention of the file.")
            }
            row {
                checkBox("Always ask which session").bindSelected(state::alwaysAsk)
                    .comment("Otherwise the session running on the project is used, and asked about only when there are several.")
            }
            row("New sessions run:") {
                comboBox(KINDS)
                    .bindItem(state::newSessionKind.toNullableProperty())
                    .comment("What a session opened from the chooser's <b>Open a new session here</b> runs.")
            }
        }
    }

    private companion object {
        // The kinds `lich open --kind` takes, as lich's own provider list names them.
        val KINDS = listOf("claude", "codex", "antigravity", "opencode", "omp", "crush", "cursor", "kiro", "shell")
    }
}
