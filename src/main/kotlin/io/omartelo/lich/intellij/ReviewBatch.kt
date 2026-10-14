package io.omartelo.lich.intellij

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import io.omartelo.lich.intellij.core.ReviewComment
import io.omartelo.lich.intellij.core.ReviewComments
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JPanel

/**
 * The review comments written on this project and not yet sent, held until they reach a session as one paste.
 *
 * In memory only, and touched on the UI thread only: a comment names lines as they were when it was written, and
 * nothing follows them as the file is edited, so a batch that outlived the IDE would point at lines that moved.
 */
@Service(Service.Level.PROJECT)
class ReviewBatch(private val project: Project) {
    private val comments = mutableListOf<ReviewComment>()
    private val listeners = mutableListOf<() -> Unit>()

    val pending: List<ReviewComment> get() = comments.toList()

    /** Blank [text] is not a comment, and is dropped. A first comment brings the batch's tool window up. */
    fun add(location: String?, text: String) {
        if (text.isBlank()) return
        comments += ReviewComment(location, text.trim())
        changed()
        toolWindow()?.show()
    }

    /** By identity: two comments with the same words on the same lines are still two. */
    fun remove(sent: Collection<ReviewComment>) {
        if (comments.removeIf { comment -> sent.any { it === comment } }) changed()
    }

    /** Rewords [comment] in place, keeping its lines and its turn in the list; blank [text] removes it. */
    fun edit(comment: ReviewComment, text: String) {
        val index = comments.indexOfFirst { it === comment }
        if (index < 0) return
        if (text.isBlank()) comments.removeAt(index) else comments[index] = comment.copy(text = text.trim())
        changed()
    }

    fun clear() = remove(pending)

    fun onChange(listener: () -> Unit) {
        listeners += listener
    }

    private fun changed() {
        toolWindow()?.isAvailable = comments.isNotEmpty()
        listeners.forEach { it() }
    }

    private fun toolWindow(): ToolWindow? = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)

    companion object {
        const val TOOL_WINDOW_ID = "lich Review"

        fun of(project: Project): ReviewBatch = project.service()
    }
}

/** Hidden until the first comment: an empty batch has nothing to show or send. */
class ReviewBatchToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun shouldBeAvailable(project: Project): Boolean = false

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = ContentFactory.getInstance().createContent(ReviewBatchPanel(project), null, false)
        toolWindow.contentManager.addContent(content)
    }
}

/** The pending comments, a note about the change as a whole, and the one action that hands them all over. */
private class ReviewBatchPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val batch = ReviewBatch.of(project)
    private val model = CollectionListModel<ReviewComment>()
    private val list = JBList(model)
    private val count = JBLabel()
    private val note = JBTextField()

    init {
        list.cellRenderer = object : ColoredListCellRenderer<ReviewComment>() {
            override fun customizeCellRenderer(list: JList<out ReviewComment>, comment: ReviewComment, index: Int, selected: Boolean, hasFocus: Boolean) {
                comment.location?.let { append("$it  ", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
                append(comment.text.lineSequence().first())
                toolTipText = comment.text
            }
        }
        val comments = ToolbarDecorator.createDecorator(list)
            .disableAddAction()
            .disableUpDownActions()
            .setEditAction { editSelected() }
            .setRemoveAction { batch.remove(list.selectedValuesList) }
            .createPanel()
        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                editSelected()
                return true
            }
        }.installOn(list)

        // A remark joins the batch only once there is one: alone it is a sentence the session's own prompt takes.
        note.emptyText.text = "A note about the change as a whole; Enter adds it"
        note.addActionListener {
            batch.add(null, note.text)
            note.text = ""
        }

        val clear = JButton("Clear").apply { addActionListener { clearAll() } }
        val send = JButton("Send to lich").apply { addActionListener { send() } }
        val buttons = JPanel(BorderLayout()).apply {
            add(count, BorderLayout.WEST)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0)).apply { add(clear); add(send) }, BorderLayout.EAST)
        }
        val foot = JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            border = JBUI.Borders.empty(4)
            add(note, BorderLayout.NORTH)
            add(buttons, BorderLayout.SOUTH)
        }
        add(comments, BorderLayout.CENTER)
        add(foot, BorderLayout.SOUTH)

        batch.onChange(::refresh)
        refresh()
    }

    private fun refresh() {
        val pending = batch.pending
        model.replaceAll(pending)
        count.text = if (pending.size == 1) "1 comment" else "${pending.size} comments"
    }

    private fun editSelected() {
        val comment = list.selectedValue ?: return
        val title = comment.location?.let { "Comment on $it" } ?: "Note about the change as a whole"
        val text = askReviewComment(project, "Edit lich Review Comment", title, comment.text) ?: return
        batch.edit(comment, text)
    }

    private fun clearAll() {
        batch.clear()
        note.text = ""
    }

    // The comments are what the user typed, so they outlive a failed send: only a paste that landed clears them, and
    // only the ones it carried, since more may have been written while lich was being asked.
    private fun send() {
        val sent = batch.pending
        if (sent.isEmpty()) return
        val remark = note.text
        SendFlow(project, ReviewComments.compose(sent, remark)) {
            batch.remove(sent)
            if (note.text == remark) note.text = ""
        }.start()
    }
}

/** A comment's text, in a box with room to write several lines; null when the dialog is cancelled. */
internal fun askReviewComment(project: Project, title: String, prompt: String, initial: String = ""): String? {
    val text = JBTextArea(initial, 6, 60).apply {
        lineWrap = true
        wrapStyleWord = true
        border = JBUI.Borders.empty(6, 8)
    }
    val dialog = object : DialogWrapper(project) {
        init {
            this.title = title
            init()
        }

        override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            add(JBLabel(prompt), BorderLayout.NORTH)
            add(JBScrollPane(text), BorderLayout.CENTER)
        }

        override fun getPreferredFocusedComponent(): JComponent = text
    }
    return if (dialog.showAndGet()) text.text else null
}
