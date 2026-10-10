package dev.lich.intellij

import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import dev.lich.intellij.core.PathUtil
import dev.lich.intellij.core.Snippets
import dev.lich.intellij.core.TestFailure

/** Pastes the failed tests chosen in the test runner, with their traces, at a lich session's prompt. */
class SendTestFailureAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null && failedTestsOf(event).isNotEmpty()
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val failures = failedTestsOf(event).map { failureOf(it, project) }
        if (failures.isEmpty()) return

        SendFlow(project, Snippets.testFailures(failures, LichSettings.getInstance().state.fenced)).start()
    }

    /** The failed tests under what is selected: a test itself, or every failed one in a chosen class or suite. */
    private fun failedTestsOf(event: AnActionEvent): List<AbstractTestProxy> {
        val selected = event.getData(AbstractTestProxy.DATA_KEYS)?.toList()
            ?: listOfNotNull(event.getData(AbstractTestProxy.DATA_KEY))
        return selected.flatMap { it.allTests }.filter { it.isLeaf && it.isDefect }.distinct()
    }

    private fun failureOf(test: AbstractTestProxy, project: Project) = TestFailure(
        name = nameOf(test),
        location = locationOf(test, project),
        message = test.errorMessage,
        stacktrace = test.stacktrace,
    )

    /** `Class › test`: a method's name alone says little, and the runner's root above the class says nothing. */
    private fun nameOf(test: AbstractTestProxy): String {
        val parent = test.parent
        return if (parent?.parent == null) test.name else "${parent.name} › ${test.name}"
    }

    private fun locationOf(test: AbstractTestProxy, project: Project): String? {
        val element = test.getLocation(project, GlobalSearchScope.allScope(project))?.psiElement ?: return null
        val file = element.containingFile ?: return null
        val path = file.virtualFile?.path ?: return null
        val line = PsiDocumentManager.getInstance(project).getDocument(file)?.getLineNumber(element.textOffset)
            ?: return PathUtil.display(path, project.basePath)
        return "${PathUtil.display(path, project.basePath)}:${line + 1}"
    }
}
