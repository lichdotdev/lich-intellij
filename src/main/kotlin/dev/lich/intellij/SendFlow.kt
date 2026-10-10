package dev.lich.intellij

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import dev.lich.intellij.core.Chooser
import dev.lich.intellij.core.Decision
import dev.lich.intellij.core.Inserted
import dev.lich.intellij.core.LichClient
import dev.lich.intellij.core.LichLocator
import dev.lich.intellij.core.Peer
import javax.swing.JList

/**
 * Takes text from the IDE to a lich session: finds the session, asks only when
 * it has to, pastes, and says where it went.
 *
 * Every call to lich is a child process, so none of it runs on the UI thread;
 * what needs the UI (the chooser, the balloon) is handed back to it.
 */
class SendFlow(private val project: Project, private val text: String) {
    private val settings = LichSettings.getInstance().state

    fun start() {
        val basePath = project.basePath
        background(
            "Looking for a lich session",
            work = {
                val client = client()
                client to Chooser.decide(
                    client.sessions(),
                    basePath,
                    PropertiesComponent.getInstance(project).getValue(LAST_SESSION),
                    settings.alwaysAsk,
                )
            },
        ) { (client, decision) ->
            when (decision) {
                is Decision.Use -> insert(client, decision.peer)
                is Decision.Ask -> choose(client, decision.peers, decision.reason)
                is Decision.None -> choose(client, emptyList(), decision.reason)
            }
        }
    }

    private fun client() = LichClient(LichLocator.find(settings.command, IdeEnvironment))

    /** One row of the chooser: a session that is running, or a new one to open on this project. */
    private sealed interface Choice {
        data class Running(val peer: Peer) : Choice

        data class New(val kind: String) : Choice
    }

    /** With no folder open there is nowhere to open a session, and only the running ones are offered. */
    private fun choose(client: LichClient, peers: List<Peer>, reason: String) {
        val basePath = project.basePath
        val choices = peers.map { Choice.Running(it) } + listOfNotNull(basePath?.let { Choice.New(settings.newSessionKind) })
        if (choices.isEmpty()) return notify(reason, NotificationType.WARNING)
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(choices)
            .setTitle("Send to lich")
            .setAdText(reason)
            .setRenderer(ChoiceRenderer())
            .setNamerForFiltering {
                when (it) {
                    is Choice.Running -> it.peer.label + " " + it.peer.project
                    is Choice.New -> "new ${it.kind} session"
                }
            }
            .setItemChosenCallback {
                when (it) {
                    is Choice.Running -> insert(client, it.peer)
                    is Choice.New -> openAndInsert(client, basePath!!, it.kind)
                }
            }
            .createPopup()
            .showCenteredInCurrentWindow(project)
    }

    private class ChoiceRenderer : ColoredListCellRenderer<Choice>() {
        override fun customizeCellRenderer(list: JList<out Choice>, choice: Choice, index: Int, selected: Boolean, hasFocus: Boolean) {
            when (choice) {
                is Choice.Running -> PeerPresentation.render(this, choice.peer)
                is Choice.New -> {
                    icon = AllIcons.General.Add
                    append("Open a new ${choice.kind} session here", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                }
            }
        }
    }

    private fun insert(client: LichClient, peer: Peer) {
        background("Sending to lich", work = { client.insert(peer.id, text) }) { inserted ->
            PropertiesComponent.getInstance(project).setValue(LAST_SESSION, inserted.id)
            sent(inserted)
        }
    }

    private fun openAndInsert(client: LichClient, projectDir: String, kind: String) {
        background("Opening a $kind session in lich", work = {
            val opened = client.open(projectDir, kind)
            client.insert(opened.id, text, NEW_SESSION_PROMPT_SECONDS)
        }) { inserted ->
            PropertiesComponent.getInstance(project).setValue(LAST_SESSION, inserted.id)
            sent(inserted)
        }
    }

    private fun sent(inserted: Inserted) {
        LichWindow.notify(
            project,
            "Pasted at the prompt of “${inserted.label}” (${inserted.project}). Nothing was sent: it is waiting for you.",
            NotificationType.INFORMATION,
            offerShow = true,
        )
    }

    private fun notify(message: String, type: NotificationType) = LichWindow.notify(project, message, type)

    private fun <T : Any> background(title: String, work: () -> T, then: (T) -> Unit) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, false) {
            private var result: T? = null
            private var failure: Exception? = null

            override fun run(indicator: ProgressIndicator) {
                try {
                    result = work()
                } catch (e: Exception) {
                    // A LichException is already worded for a person; anything else is a bug here, and still deserves a balloon over a silent log line.
                    failure = e
                }
            }

            // Runs on the UI thread, once run() has returned.
            override fun onSuccess() {
                failure?.let { notify(it.message ?: it.javaClass.simpleName, NotificationType.ERROR) }
                result?.let(then)
            }
        })
    }

    private companion object {
        const val LAST_SESSION = "dev.lich.intellij.lastSession"

        // A new session's agent has to start before it shows a prompt; the slowest providers take tens of seconds.
        const val NEW_SESSION_PROMPT_SECONDS = 60L
    }
}
