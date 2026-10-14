package io.omartelo.lich.intellij

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import io.omartelo.lich.intellij.core.PathUtil
import io.omartelo.lich.intellij.core.Snippets

/**
 * Asks for a comment on the selected lines, or the caret's, and adds it to the project's review batch. Works in a
 * diff on the side that is the file on disk; the other side is a revision, whose lines the session cannot open.
 */
class AddReviewCommentAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR)
        event.presentation.isEnabledAndVisible = event.project != null && editor != null && fileOf(editor) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val file = fileOf(editor) ?: return
        val lines = selectedLines(editor)
        val location = Snippets.location(PathUtil.display(file.path, project.basePath), lines.first, lines.last)
        val text = askReviewComment(project, "Add Comment to lich Review", "Comment on $location") ?: return
        ReviewBatch.of(project).add(location, text)
    }

    private fun fileOf(editor: Editor): VirtualFile? =
        FileDocumentManager.getInstance().getFile(editor.document)?.takeIf { it.isInLocalFileSystem }
}
