package com.example.ringpongwear

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

/** Full-screen Wear OS host for the rotary-controlled Ring Pong game. */
class MainActivity : Activity() {
    private var backCallback: Any? = null
    private lateinit var gameView: RingPongView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setBackgroundDrawableResource(android.R.color.black)

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN

        gameView = RingPongView(this)
        setContentView(gameView)
        gameView.requestFocus()

        // A no-op overlay callback consumes the software Back gesture/key while the
        // game is active. windowSwipeToDismiss=false in AppTheme disables Wear's
        // separate left-to-right activity-dismiss gesture.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = OnBackInvokedCallback { /* deliberately stay in the game */ }
            backCallback = callback
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                callback
            )
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // API 30–32 fallback. Back is intentionally blocked during gameplay.
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            (backCallback as? OnBackInvokedCallback)?.let { callback ->
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
            }
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }
}
