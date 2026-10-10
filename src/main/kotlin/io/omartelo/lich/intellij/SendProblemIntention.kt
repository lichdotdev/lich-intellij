package io.omartelo.lich.intellij

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import io.omartelo.lich.intellij.core.PathUtil
import io.omartelo.lich.intellij.core.Problem
import io.omartelo.lich.intellij.core.Selection
import io.omartelo.lich.intellij.core.Snippets

/** In Alt+Enter on an error or warning: pastes what the IDE says, over the lines it is about, at a lich session's prompt. */
class SendProblemIntention : IntentionAction, LowPriorityAction {
    override fun getText(): String = "Send problem to lich"

    override fun getFamilyName(): String = text

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
        editor != null && problemsAt(project, editor).isNotEmpty()

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        val highlights = problemsAt(project, editor)
        if (highlights.isEmpty()) return
        val virtualFile = file.virtualFile ?: return
        val problems = highlights.map { Problem(it.severity.name, it.description) }
        val document = editor.document
        val code = wholeLines(
            document,
            document.getLineNumber(highlights.minOf { it.startOffset }),
            document.getLineNumber(highlights.maxOf { it.endOffset }),
            virtualFile,
            project,
        )

        SendFlow(project, Snippets.problems(problems, code, LichSettings.getInstance().state.fenced)).start()
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.EMPTY

    /** The errors and warnings under the caret that say something; the ones below a weak warning are styling, not problems. */
    private fun problemsAt(project: Project, editor: Editor): List<HighlightInfo> {
        val caret = editor.caretModel.offset
        val found = mutableListOf<HighlightInfo>()
        DaemonCodeAnalyzerEx.processHighlights(editor.document, project, HighlightSeverity.WEAK_WARNING, caret, caret) { info ->
            if (!info.description.isNullOrBlank() && caret in info.startOffset..info.endOffset) found += info
            true
        }
        return found.distinctBy { it.severity to it.description }
    }
}

/** Lines [firstLine]..[lastLine] (0-based) whole: a problem marks a word, and the agent needs the statement around it. */
internal fun wholeLines(document: Document, firstLine: Int, lastLine: Int, file: VirtualFile, project: Project): Selection {
    val range = TextRange(document.getLineStartOffset(firstLine), document.getLineEndOffset(lastLine))
    return Selection(
        PathUtil.display(file.path, project.basePath),
        firstLine + 1,
        lastLine + 1,
        file.extension,
        document.getText(range),
    )
}
