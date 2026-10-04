package com.cloudit24.stillpoint

import android.content.Context
import com.cloudit24.stillpoint.data.Lang
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.CompositionLocalProvider
import com.cloudit24.stillpoint.ui.SeniorHome
import com.cloudit24.stillpoint.ui.SeniorApps
import com.cloudit24.stillpoint.ui.ToolsScreen
import com.cloudit24.stillpoint.update.UpdateNotice
import com.cloudit24.stillpoint.widget.Refresh
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import com.cloudit24.stillpoint.ui.PauseScreen
import com.cloudit24.stillpoint.service.Guard
import com.cloudit24.stillpoint.ui.SettingsScreen
import com.cloudit24.stillpoint.ui.StillpointTheme
import com.cloudit24.stillpoint.ui.WidgetsScreen

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    /** System "Allow Stillpoint to create widgets?" dialog. */
    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) configureOrCommitWidget() else vm.cancelPendingWidget()
    }

    override fun attachBaseContext(newBase: Context) {

        super.attachBaseContext(Lang.wrap(newBase))

    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        if (CrashLog.takeUnseen(this)) {
            vm.blockedMessage = "Stillpoint closed unexpectedly and restarted. " +
                "If you want to report it, the details are in Settings, About."
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            StillpointTheme(accent = androidx.compose.ui.graphics.Color(vm.settings.accent), accentStyle = vm.settings.accentStyle, font = vm.settings.font,
                neon = vm.settings.theme == com.cloudit24.stillpoint.data.AppTheme.NEON) { LauncherRoot(vm, ::addWidget) }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    /** From the "new version is ready" notification: straight to Settings. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(UpdateNotice.EXTRA_OPEN_SETTINGS, false) == true) vm.screen = Screen.SETTINGS
        intent?.getStringExtra(Guard.EXTRA_PKG)?.let { vm.guard(it, intent.getBooleanExtra(Guard.EXTRA_BLOCK, false)) }
    }

    override fun onStart() {
        super.onStart()
        runCatching { vm.widgetHost.startListening() }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
        runCatching { Refresh.all(this) }
    }

    override fun onStop() {
        super.onStop()
        runCatching { vm.widgetHost.stopListening() }
        if (vm.screen == Screen.DRAWER) vm.screen = Screen.HOME
    }

    private fun addWidget(provider: AppWidgetProviderInfo) {
        val id = vm.allocateWidgetId()
        // Say where the widget goes (home screen) so apps that check it agree to show.
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
        }
        if (vm.widgetManager.bindAppWidgetIdIfAllowed(id, provider.profile, provider.provider, options)) {
            configureOrCommitWidget()
        } else {
            bindWidget.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.profile)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options),
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
        handleIntent(intent)
    }
}

@Composable
private fun LauncherRoot(vm: LauncherViewModel, onAddWidget: (AppWidgetProviderInfo) -> Unit) {
    // Launcher never finishes on back; back always returns to home.
    BackHandler { vm.screen = Screen.HOME }

    // Senior mode: every text a fifth bigger.
    val base = LocalDensity.current
    val density = if (vm.settings.seniorMode) Density(base.density, base.fontScale * 1.2f) else base
    CompositionLocalProvider(LocalDensity provides density) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        AnimatedContent(targetState = vm.screen, transitionSpec = { screenTransition(initialState, targetState) }, label = "screen") { screen ->
        when (screen) {
            Screen.HOME -> if (vm.settings.seniorMode) SeniorHome(vm) else HomeScreen(vm)
            Screen.DRAWER -> if (vm.settings.seniorMode) SeniorApps(vm) else DrawerScreen(vm)
            Screen.FOCUS -> FocusScreen(vm)
            Screen.SETTINGS -> SettingsScreen(vm)
            Screen.WIDGETS -> WidgetsScreen(vm, onAddWidget)
            Screen.DATA -> DataUsageScreen(vm)
            Screen.PRAYER -> PrayerScreen(vm)
            Screen.TOOLS -> ToolsScreen(vm)
        }
        }
    }
    }

    // A new version is announced in the terminal display on home, not in a popup.

    vm.pausing?.let { PauseScreen(vm, it) }

    vm.blockedMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.blockedMessage = null },
            confirmButton = { TextButton(onClick = { vm.blockedMessage = null }) { Text(stringResource(R.string.s_ok)) } },
            text = { Text(msg) },
        )
    }

}

/** Widgets sit left of home and the app list right, so they slide sideways; other pages rise in. */
private fun Screen.lane(): Int = when (this) {
    Screen.WIDGETS -> -1
    Screen.HOME -> 0
    Screen.DRAWER -> 1
    else -> 2
}

private fun screenTransition(from: Screen, to: Screen): ContentTransform {
    val a = from.lane()
    val b = to.lane()
    return if (a != 2 && b != 2) {
        val dir = if (b > a) 1 else -1
        (slideInHorizontally(tween(260)) { w -> dir * w / 3 } + fadeIn(tween(260))) togetherWith
            (slideOutHorizontally(tween(220)) { w -> -dir * w / 3 } + fadeOut(tween(180)))
    } else {
        (slideInVertically(tween(260)) { h -> if (b == 2) h / 12 else -h / 24 } + fadeIn(tween(260))) togetherWith
            fadeOut(tween(160))
    }
}
