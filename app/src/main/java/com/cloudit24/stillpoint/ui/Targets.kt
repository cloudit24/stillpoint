package com.cloudit24.stillpoint.ui

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import com.cloudit24.stillpoint.LauncherViewModel
import com.cloudit24.stillpoint.Screen
import com.cloudit24.stillpoint.data.HomeAction
import com.cloudit24.stillpoint.data.GestureTarget
import com.cloudit24.stillpoint.service.LockAccessibilityService

/** Runs whatever a gesture or bottom shortcut is set to. */
fun runTarget(vm: LauncherViewModel, context: Context, target: String) {
    vm.appForTarget(target)?.let { vm.launch(it); return }
    when (GestureTarget.actionOf(target)) {
        HomeAction.APPS -> vm.screen = Screen.DRAWER
        HomeAction.SEARCH -> { vm.openSearch = true; vm.screen = Screen.DRAWER }
        HomeAction.WIDGETS -> vm.screen = Screen.WIDGETS
        HomeAction.FOCUS -> vm.screen = Screen.FOCUS
        HomeAction.SETTINGS -> vm.screen = Screen.SETTINGS
        HomeAction.DATA_USAGE -> vm.screen = Screen.DATA
        HomeAction.PRAYER -> vm.screen = Screen.PRAYER
        HomeAction.PHONE -> context.safeStart(Intent(Intent.ACTION_DIAL))
        HomeAction.CAMERA -> context.safeStart(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        HomeAction.NOTIFICATIONS -> if (!LockAccessibilityService.openNotifications()) needsService(context)
        HomeAction.LOCK -> if (!LockAccessibilityService.lockScreen()) needsService(context)
        HomeAction.NONE, null -> Unit
    }
}

private fun needsService(context: Context) =
    Toast.makeText(context, "Turn on the gesture service in Settings first.", Toast.LENGTH_SHORT).show()
