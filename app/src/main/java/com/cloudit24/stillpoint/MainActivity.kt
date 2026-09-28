package com.cloudit24.stillpoint

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cloudit24.stillpoint.ui.DataUsageScreen
import com.cloudit24.stillpoint.ui.DrawerScreen
import com.cloudit24.stillpoint.ui.PrayerScreen
import com.cloudit24.stillpoint.ui.FocusScreen
import com.cloudit24.stillpoint.ui.HomeScreen
import com.cloudit24.stillpoint.ui.SettingsScreen
import com.cloudit24.stillpoint.ui.StillpointTheme
import com.cloudit24.stillpoint.ui.WidgetsScreen

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    /** System "Allow Stillpoint to create widgets?" dialog. */
    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) configureOrCommitWidget() else vm.cancelPendingWidget()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { StillpointTheme { LauncherRoot(vm, ::addWidget) } }
    }

    override fun onStart() {
        super.onStart()
        runCatching { vm.widgetHost.startListening() }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    override fun onStop() {
        super.onStop()
        runCatching { vm.widgetHost.stopListening() }
        if (vm.screen == Screen.DRAWER) vm.screen = Screen.HOME
    }

    private fun addWidget(provider: AppWidgetProviderInfo) {
        val id = vm.allocateWidgetId()
        if (vm.widgetManager.bindAppWidgetIdIfAllowed(id, provider.profile, provider.provider, null)) {
            configureOrCommitWidget()
        } else {
            bindWidget.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.profile),
            )
        }
    }

    /** Some widgets ask for setup first (pick a city, an account...). */
    private fun configureOrCommitWidget() {
        val id = vm.pendingWidgetId
        if (vm.widgetManager.getAppWidgetInfo(id)?.configure == null) {
            vm.commitPendingWidget()
            return
        }
        try {
            vm.widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE_WIDGET, null)
        } catch (_: ActivityNotFoundException) {
            vm.commitPendingWidget()
        } catch (_: SecurityException) {
            vm.commitPendingWidget()
        }
    }

    // The configure screen can only be started through the widget host, which reports back here.
    @Deprecated("Needed for AppWidgetHost.startAppWidgetConfigureActivityForResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CONFIGURE_WIDGET) {
            if (resultCode == RESULT_OK) vm.commitPendingWidget() else vm.cancelPendingWidget()
        }
    }

    private companion object {
        const val REQ_CONFIGURE_WIDGET = 7101
    }

    /** Home button pressed while already on the launcher. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) vm.screen = Screen.HOME
    }
}

@Composable
private fun LauncherRoot(vm: LauncherViewModel, onAddWidget: (AppWidgetProviderInfo) -> Unit) {
    // Launcher never finishes on back; back always returns to home.
    BackHandler { vm.screen = Screen.HOME }

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        when (vm.screen) {
            Screen.HOME -> HomeScreen(vm)
            Screen.DRAWER -> DrawerScreen(vm)
            Screen.FOCUS -> FocusScreen(vm)
            Screen.SETTINGS -> SettingsScreen(vm)
            Screen.WIDGETS -> WidgetsScreen(vm, onAddWidget)
            Screen.DATA -> DataUsageScreen(vm)
            Screen.PRAYER -> PrayerScreen(vm)
        }
    }

    vm.blockedMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.blockedMessage = null },
            confirmButton = { TextButton(onClick = { vm.blockedMessage = null }) { Text("OK") } },
            text = { Text(msg) },
        )
    }
}
