package dev.lich.intellij

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.SimpleListCellRenderer
import dev.lich.intellij.core.Chooser
import dev.lich.intellij.core.Decision
import dev.lich.intellij.core.Inserted
import dev.lich.intellij.core.LichClient
import dev.lich.intellij.core.LichLocator
import dev.lich.intellij.core.Peer
import dev.lich.intellij.core.SessionMatcher

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
                is Decision.Ask -> choose(client, decision)
                is Decision.None -> notify(decision.reason, NotificationType.WARNING)
            }
        }
    }

    private fun client() = LichClient(LichLocator.find(settings.command, IdeEnvironment))

    private fun choose(client: LichClient, ask: Decision.Ask) {
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(ask.peers)
            .setTitle(ask.reason)
            .setRenderer(
                SimpleListCellRenderer.create<Peer> { label, peer, _ ->
                    label.text = describe(peer)
                    label.isEnabled = SessionMatcher.takesText(peer)
                },
            )
            .setNamerForFiltering { it.label + " " + it.project }
            .setItemChosenCallback { insert(client, it) }
            .createPopup()
            .showCenteredInCurrentWindow(project)
    }

    private fun describe(peer: Peer): String {
        val where = if (peer.path.isNotEmpty() && peer.path != peer.projectPath) " — ${peer.path}" else ""
        val waiting = if (SessionMatcher.takesText(peer)) "" else " (waiting on a permission)"
        return "${peer.label}  ·  ${peer.project}  ·  ${peer.kind}$where$waiting"
    }

    private fun insert(client: LichClient, peer: Peer) {
        background("Sending to lich", work = { client.insert(peer.id, text) }) { inserted ->
            PropertiesComponent.getInstance(project).setValue(LAST_SESSION, inserted.id)
            sent(inserted)
        }
    }

    private fun sent(inserted: Inserted) {
        NotificationGroupManager.getInstance().getNotificationGroup(GROUP)
            .createNotification(
                "Pasted at the prompt of “${inserted.label}” (${inserted.project}). Nothing was sent: it is waiting for you.",
                NotificationType.INFORMATION,
            )
            .addAction(NotificationAction.createSimple("Open lich") { raise() })
            .notify(project)
    }

    /** A second launch of lich hands over to the one running and exits, which is how its window is raised. */
    private fun raise() {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                ProcessBuilder(LichLocator.find(settings.command, IdeEnvironment)).start()
            } catch (e: Exception) {
                notify(e.message ?: "Could not open lich", NotificationType.WARNING)
            }
        }
    }

    private fun notify(message: String, type: NotificationType) {
        NotificationGroupManager.getInstance().getNotificationGroup(GROUP)
            .createNotification(message, type)
            .notify(project)
    }

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
        const val GROUP = "lich"
        const val LAST_SESSION = "dev.lich.intellij.lastSession"
    }
}
