package dev.lich.intellij

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.util.Consumer
import com.intellij.util.concurrency.AppExecutorUtil
import dev.lich.intellij.core.LichClient
import dev.lich.intellij.core.LichException
import dev.lich.intellij.core.LichLocator
import dev.lich.intellij.core.Peer
import dev.lich.intellij.core.SessionMatcher
import dev.lich.intellij.core.SessionWatch
import java.awt.Component
import java.awt.event.MouseEvent
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.JList

class LichStatusWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = LichStatusWidget.ID

    override fun getDisplayName(): String = "lich Sessions"

    override fun createWidget(project: Project): StatusBarWidget = LichStatusWidget(project)
}

/**
 * The lich sessions on this project, in the status bar, and a balloon when one
 * finishes its turn or stops on a permission prompt. lich pushes nothing a
 * plugin may rely on, so this asks `lich sessions` again every few seconds.
 */
class LichStatusWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {
    private var statusBar: StatusBar? = null
    private var polling: ScheduledFuture<*>? = null

    // Written by the poll, read on the UI thread.
    @Volatile private var text = "lich"
    @Volatile private var near: List<Peer>? = null

    // ponytail: found once per project window, so a lich installed or a path set in Settings later is seen on reopening it.
    private val client: Result<LichClient> by lazy {
        runCatching { LichClient(LichLocator.find(LichSettings.getInstance().state.command, IdeEnvironment)) }
    }

    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        polling = AppExecutorUtil.getAppScheduledExecutorService()
            .scheduleWithFixedDelay(::poll, 0, POLL_SECONDS, TimeUnit.SECONDS)
    }

    override fun dispose() {
        polling?.cancel(false)
        statusBar = null
    }

    override fun getText(): String = text

    override fun getAlignment(): Float = Component.CENTER_ALIGNMENT

    override fun getTooltipText(): String = "lich sessions on this project. Click to list them."

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer { show() }

    private fun poll() {
        val lich = client.getOrElse {
            update("lich: not found", null)
            return
        }
        val peers = try {
            lich.sessions()
        } catch (_: LichException) {
            // Not running, most of the time; the balloon for it belongs to the send that needs lich, not to a poll.
            update("lich: off", null)
            return
        }
        val now = project.basePath?.let { SessionMatcher.nearest(peers, it)?.peers }.orEmpty()
        SessionWatch.changes(near, now).forEach(::announce)
        update("lich: ${SessionWatch.summary(now)}", now)
    }

    private fun update(text: String, near: List<Peer>?) {
        this.text = text
        this.near = near
        ApplicationManager.getApplication().invokeLater({ statusBar?.updateWidget(ID) }, project.disposed)
    }

    private fun announce(change: SessionWatch.Change) {
        val (message, type) = when (change.event) {
            SessionWatch.Event.FINISHED -> "“${change.peer.label}” finished its turn." to NotificationType.INFORMATION
            SessionWatch.Event.WAITING -> "“${change.peer.label}” is waiting on a permission." to NotificationType.WARNING
        }
        LichWindow.notify(project, message, type, offerShow = true)
    }

    /** The sessions in a list; picking one brings lich forward, the only way the IDE has to reach a session's card. */
    private fun show() {
        val peers = near.orEmpty()
        if (peers.isEmpty()) return LichWindow.raise(project)
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(peers)
            .setTitle("lich")
            .setRenderer(object : ColoredListCellRenderer<Peer>() {
                override fun customizeCellRenderer(list: JList<out Peer>, peer: Peer, index: Int, selected: Boolean, hasFocus: Boolean) =
                    PeerPresentation.render(this, peer)
            })
            .setItemChosenCallback { LichWindow.raise(project) }
            .createPopup()
            .showInCenterOf(statusBar?.component ?: return)
    }

    companion object {
        const val ID = "lich.sessions"

        // Each look is one `lich sessions` child process, about 30ms.
        private const val POLL_SECONDS = 5L
    }
}
