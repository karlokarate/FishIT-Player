package com.fishit.player.v2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.fishit.player.core.appstartup.bootstrap.CatalogSyncStarter
import com.fishit.player.core.appstartup.bootstrap.XtreamBootstrapper
import com.fishit.player.v2.navigation.AppNavHost
import com.fishit.player.v2.ui.theme.FishItV2Theme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main entry activity for FishIT Player v2.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var catalogSyncStarter: CatalogSyncStarter

    @Inject
    lateinit var xtreamBootstrapper: XtreamBootstrapper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Xtream session before catalog sync
        xtreamBootstrapper.start()

        setContent {
            FishItV2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavHost(
                        catalogSyncStarter = catalogSyncStarter,
                    )
                }
            }
        }
    }
}
