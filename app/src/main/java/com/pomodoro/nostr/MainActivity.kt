package com.pomodoro.nostr

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pomodoro.nostr.nostr.KeyManager
import com.pomodoro.nostr.ui.navigation.PomodoroNavGraph
import com.pomodoro.nostr.ui.theme.PomodoroTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var keyManager: KeyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle Amber callback if app was launched via deep link
        handleAmberCallback(intent)

        setContent {
            PomodoroTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PomodoroNavGraph()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAmberCallback(intent)
    }

    private fun handleAmberCallback(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme == "nostr-pomodoro") {
            val result = keyManager.parseAmberCallback(data)
            keyManager.setPendingAmberCallback(result)
        }
    }
}
