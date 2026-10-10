package io.omartelo.lich.intellij

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import io.omartelo.lich.intellij.core.LichClient
import io.omartelo.lich.intellij.core.LichException
import io.omartelo.lich.intellij.core.LichLocator
import io.omartelo.lich.intellij.core.LichProcess

/** The running lich window, as far as the IDE can reach it. */
object LichWindow {
    const val NOTIFICATION_GROUP = "lich"

    /** A second launch of lich hands over to the one running and exits, which is how its window is raised. */
    fun raise(project: Project) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                LichProcess.builder(listOf(command())).start()
            } catch (e: Exception) {
                notify(project, e.message ?: "Could not show lich", NotificationType.WARNING)
            }
        }
    }

    /** Opens [sessionId]'s card and raises the window; a lich from before `lich focus` only raises it. */
    fun show(project: Project, sessionId: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                if (!LichClient(command()).focus(sessionId)) raise(project)
            } catch (e: LichException) {
                notify(project, e.message ?: "Could not show the session in lich", NotificationType.WARNING)
            }
        }
    }

    /** With [showSession], the balloon offers to open that session's card in lich. */
    fun notify(project: Project, message: String, type: NotificationType, showSession: String? = null) {
        val notification = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP).createNotification(message, type)
        if (showSession != null) notification.addAction(NotificationAction.createSimple("Show in lich") { show(project, showSession) })
        notification.notify(project)
    }

    private fun command() = LichLocator.find(LichSettings.getInstance().state.command, IdeEnvironment)
}
