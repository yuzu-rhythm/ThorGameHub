package dev.thorhub.app.platform.home

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.thorhub.app.platform.diagnostics.DiagnosticEventLog
import javax.inject.Inject
import javax.inject.Singleton

enum class HomeRoleStatus {
    UNAVAILABLE,
    AVAILABLE_NOT_HELD,
    REQUEST_IN_PROGRESS,
    HELD,
    DENIED_OR_CANCELLED,
    ERROR,
}

data class HomeRoleState(
    val status: HomeRoleStatus,
    val explanation: String,
)

@Singleton
class HomeRoleCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val roleManager = context.getSystemService(RoleManager::class.java)

    fun currentState(): HomeRoleState = runCatching {
        when {
            !roleManager.isRoleAvailable(RoleManager.ROLE_HOME) -> HomeRoleState(
                HomeRoleStatus.UNAVAILABLE,
                "This Android build does not expose the HOME role.",
            )
            roleManager.isRoleHeld(RoleManager.ROLE_HOME) -> HomeRoleState(
                HomeRoleStatus.HELD,
                "Thor Game Hub currently holds the HOME role.",
            )
            else -> HomeRoleState(
                HomeRoleStatus.AVAILABLE_NOT_HELD,
                "HOME is available. Android system UI must confirm any change.",
            )
        }
    }.getOrElse { error ->
        DiagnosticEventLog.record("home_role", "query_failed", mapOf("exception" to error::class.java.simpleName))
        HomeRoleState(HomeRoleStatus.ERROR, "HOME role status could not be read.")
    }

    fun requestIntent(): Intent? = if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
        DiagnosticEventLog.record("home_role", "request_ui_opened", mapOf("role_granted" to "UNKNOWN"))
        roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
    } else {
        null
    }

    fun recordRequestResult(systemResultAccepted: Boolean): HomeRoleState {
        val actual = currentState()
        DiagnosticEventLog.record(
            "home_role",
            "request_ui_returned",
            mapOf(
                "system_result_accepted" to systemResultAccepted.toString(),
                "actual_role_state" to actual.status.name,
            ),
        )
        return if (actual.status == HomeRoleStatus.HELD) {
            actual
        } else {
            HomeRoleState(
                HomeRoleStatus.DENIED_OR_CANCELLED,
                "HOME was not granted. The app remains available as a normal launcher entry.",
            )
        }
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
