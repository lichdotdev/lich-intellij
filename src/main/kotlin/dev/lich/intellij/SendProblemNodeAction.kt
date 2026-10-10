package dev.lich.intellij

import com.intellij.analysis.problemsView.toolWindow.ProblemNode
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import dev.lich.intellij.core.Problem
import dev.lich.intellij.core.Snippets

/** In the Problems view: pastes the chosen problem, over the line it is on, at a lich session's prompt. */
class SendProblemNodeAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null && nodeOf(event) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val node = nodeOf(event) ?: return
        val document = FileDocumentManager.getInstance().getDocument(node.file) ?: return
        val line = node.line.coerceIn(0, maxOf(0, document.lineCount - 1))
        val problem = Problem(severityName(node.severity), node.text)

        SendFlow(project, Snippets.problems(listOf(problem), wholeLines(document, line, line, node.file, project), LichSettings.getInstance().state.fenced)).start()
    }

    private fun nodeOf(event: AnActionEvent): ProblemNode? = event.getData(PlatformCoreDataKeys.SELECTED_ITEM) as? ProblemNode

    /** The node keeps only the severity's weight; these are the names the editor's own highlights carry. */
    private fun severityName(weight: Int): String = when {
        weight >= HighlightSeverity.ERROR.myVal -> HighlightSeverity.ERROR.name
        weight >= HighlightSeverity.WARNING.myVal -> HighlightSeverity.WARNING.name
        else -> HighlightSeverity.WEAK_WARNING.name
    }
}
