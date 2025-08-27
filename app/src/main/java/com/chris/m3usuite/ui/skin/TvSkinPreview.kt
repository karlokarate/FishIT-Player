package com.chris.m3usuite.ui.skin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(name = "TV Skin – Focus Overlay", showBackground = true)
@Composable
private fun PreviewTvSkinOverlay() {
    M3UTvSkin {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("M3UTvSkin Preview", style = MaterialTheme.typography.titleLarge)
                Card(
                    modifier = Modifier
                        .tvClickable { }
                ) {
                    Text("Focusable Card (tvClickable)", modifier = Modifier.padding(16.dp))
                }
                Button(onClick = { }, modifier = Modifier.focusScaleOnTv()) {
                    Text("Button with focusScaleOnTv")
                }
            }
        }
    }
}

