package com.cloudit24.stillpoint

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * What Android opens when Stillpoint is the phone's assistant (long-press home, corner swipe).
 * It only hands over to the launcher, which shows the assistant sheet.
 */
class AssistActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(EXTRA_ASSIST, true))
        finish()
    }

    companion object { const val EXTRA_ASSIST = "assist" }
}
