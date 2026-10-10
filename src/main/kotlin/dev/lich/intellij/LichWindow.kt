package dev.lich.intellij

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import dev.lich.intellij.core.LichLocator
import dev.lich.intellij.core.LichProcess

/** The running lich window, as far as the IDE can reach it. */
object LichWindow {
    const val NOTIFICATION_GROUP = "lich"

    /** A second launch of lich hands over to the one running and exits, which is how its window is raised. */
    fun raise(project: Project) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                LichProcess.builder(listOf(LichLocator.find(LichSettings.getInstance().state.command, IdeEnvironment))).start()
            } catch (e: Exception) {
                notify(project, e.message ?: "Could not show lich", NotificationType.WARNING)
            }
        }
    }

    fun notify(project: Project, message: String, type: NotificationType, offerShow: Boolean = false) {
        val notification = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP).createNotification(message, type)
        if (offerShow) notification.addAction(NotificationAction.createSimple("Show lich") { raise(project) })
        notification.notify(project)
    }
}
