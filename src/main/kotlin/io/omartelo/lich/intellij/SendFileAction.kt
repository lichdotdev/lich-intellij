package io.omartelo.lich.intellij

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import io.omartelo.lich.intellij.core.FileStyle
import io.omartelo.lich.intellij.core.PathUtil
import io.omartelo.lich.intellij.core.Snippets

/** Pastes the path of the current file, or of every file chosen in the Project view, at a lich session's prompt. */
class SendFileAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null && filesOf(event).isNotEmpty()
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val paths = filesOf(event).map { PathUtil.display(it.path, project.basePath) }
        if (paths.isEmpty()) return
        val style = if (LichSettings.getInstance().state.atMention) FileStyle.AT_MENTION else FileStyle.PLAIN

        SendFlow(project, Snippets.files(paths, style, SystemInfo.isWindows)).start()
    }

    /** What is selected in the Project view, else the file open in the editor; only real files on disk. */
    private fun filesOf(event: AnActionEvent): List<VirtualFile> {
        val chosen = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.toList().orEmpty()
        val files = chosen.ifEmpty { listOfNotNull(event.getData(CommonDataKeys.VIRTUAL_FILE)) }
        return files.filter { it.isInLocalFileSystem }
    }
}
