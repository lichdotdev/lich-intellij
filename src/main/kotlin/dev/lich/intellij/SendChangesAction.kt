package dev.lich.intellij

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitContext
import dev.lich.intellij.core.Snippets
import java.io.StringWriter
import java.nio.file.Path

/** In the Commit view: pastes the diff of the chosen changes at a lich session's prompt, for a review or a commit message. */
class SendChangesAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible =
            event.project?.basePath != null && !event.getData(VcsDataKeys.CHANGES).isNullOrEmpty()
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val basePath = project.basePath ?: return
        val changes = event.getData(VcsDataKeys.CHANGES)?.toList().orEmpty()
        if (changes.isEmpty()) return

        // Reading the old side of a change asks the VCS (a `git show`), which must not run on the UI thread.
        val diff = ProgressManager.getInstance().runProcessWithProgressSynchronously<String, Exception>(
            { diffOf(project, changes, Path.of(basePath)) },
            "Building the diff for lich",
            true,
            project,
        )
        SendFlow(project, Snippets.changes(diff, changes.size, LichSettings.getInstance().state.fenced)).start()
    }

    private fun diffOf(project: Project, changes: List<Change>, base: Path): String {
        val patches = IdeaTextPatchBuilder.buildPatch(project, changes, base, false, false)
        return StringWriter().also { UnifiedDiffWriter.write(project, base, patches, it, "\n", CommitContext(), emptyList()) }.toString()
    }
}
