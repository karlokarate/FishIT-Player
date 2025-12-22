package com.fishit.player.v2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.fishit.player.v2.navigation.AppNavHost
import com.fishit.player.v2.ui.theme.FishItV2Theme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main entry activity for FishIT Player v2.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Contract S-3: Bootstraps are started in Application.onCreate() ONLY
    // No duplicate bootstrap injections or starts here

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FishItV2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavHost()
                }
            }
        }
    }
}
