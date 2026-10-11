package io.omartelo.lich.intellij

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.ColorUtil
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import io.omartelo.lich.intellij.core.LichClient
import io.omartelo.lich.intellij.core.LichException
import io.omartelo.lich.intellij.core.LichLocator
import io.omartelo.lich.intellij.core.Peer
import io.omartelo.lich.intellij.core.SessionMatcher
import io.omartelo.lich.intellij.core.SessionWatch
import java.awt.Component
import java.awt.Point
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

    /**
     * Each session on its own line, with what it runs, what it is doing and where; HTML, as the status bar renders
     * it. The provider is named in words: an icon in this HTML is not drawn by the status bar's tooltip.
     */
    override fun getTooltipText(): String {
        val peers = near
        if (peers.isNullOrEmpty()) return "lich sessions on this project. Click to show lich."
        val rows = peers.map { peer ->
            val where = FileUtil.getLocationRelativeToUserHome(peer.path.ifEmpty { peer.projectPath })
            HtmlChunk.tag("tr").children(
                HtmlChunk.tag("td").child(HtmlChunk.text(peer.label).bold()),
                HtmlChunk.tag("td").child(HtmlChunk.text(peer.kind)),
                HtmlChunk.tag("td").child(stateOf(peer)),
                HtmlChunk.tag("td").child(HtmlChunk.text(where).wrapWith(HtmlChunk.font(ColorUtil.toHex(UIUtil.getContextHelpForeground())))),
            )
        }
        return HtmlBuilder()
            .append(HtmlChunk.tag("table").children(rows))
            .append(HtmlChunk.text("Click to list them; pick one to open it in lich."))
            .wrapWithHtmlBody()
            .toString()
    }

    private fun stateOf(peer: Peer): HtmlChunk = when (peer.state) {
        "busy" -> HtmlChunk.text("working")
        "done" -> HtmlChunk.text("done")
        "waiting" -> HtmlChunk.text("waiting on a permission").wrapWith(HtmlChunk.font(ColorUtil.toHex(JBColor.RED)))
        else -> HtmlChunk.text("idle")
    }


    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer { show(it) }

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
        LichWindow.notify(project, message, type, showSession = change.peer.id)
    }

    /** The sessions in a list; picking one opens its card in lich. */
    private fun show(click: MouseEvent) {
        val peers = near.orEmpty()
        if (peers.isEmpty()) return LichWindow.raise(project)
        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(peers)
            .setTitle("lich")
            .setRenderer(object : ColoredListCellRenderer<Peer>() {
                override fun customizeCellRenderer(list: JList<out Peer>, peer: Peer, index: Int, selected: Boolean, hasFocus: Boolean) =
                    PeerPresentation.render(this, peer)
            })
            .setItemChosenCallback { LichWindow.show(project, it.id) }
            .createPopup()
        // Above the widget, its left edge on the widget's: the status bar is at the bottom of the window.
        popup.show(RelativePoint(click.component, Point(0, -popup.content.preferredSize.height)))
    }

    companion object {
        const val ID = "lich.sessions"

        // Each look is one `lich sessions` child process, about 30ms.
        private const val POLL_SECONDS = 5L
    }
}
