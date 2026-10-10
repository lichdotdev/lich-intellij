package dev.lich.intellij

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import dev.lich.intellij.core.PathUtil
import dev.lich.intellij.core.Selection
import dev.lich.intellij.core.Snippets

/** Pastes the selected code, with where it comes from, at a lich session's prompt. */
class SendSelectionAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR)
        event.presentation.isEnabledAndVisible = event.project != null && editor?.selectionModel?.hasSelection() == true
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val selection = selectionOf(editor, PathUtil.display(file.path, project.basePath), file.extension) ?: return

        SendFlow(project, Snippets.selection(selection, LichSettings.getInstance().state.fenced)).start()
    }

    /**
     * The lines the selection covers, 1-based and inclusive. A selection that
     * ends at the start of a line (a whole-line drag) does not include that
     * line, which would otherwise be named though none of it was selected.
     */
    private fun selectionOf(editor: Editor, displayPath: String, language: String?): Selection? {
        val model = editor.selectionModel
        val text = model.selectedText?.takeIf { it.isNotBlank() } ?: return null
        val document = editor.document
        val start = model.selectionStart
        val end = model.selectionEnd
        val lastOffset = if (end > start && document.charsSequence[end - 1] == '\n') end - 1 else end
        return Selection(
            displayPath = displayPath,
            startLine = document.getLineNumber(start) + 1,
            endLine = document.getLineNumber(lastOffset) + 1,
            language = language,
            text = text,
        )
    }
}
