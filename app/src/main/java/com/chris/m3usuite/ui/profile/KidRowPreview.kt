package com.chris.m3usuite.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.theme.AppTheme
import com.chris.m3usuite.ui.focus.tvClickable

@Preview(name = "Kid Row – tvClickable", showBackground = true)
@Composable
private fun PreviewKidRowTvClickable() {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            val (checked, setChecked) = remember { mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .tvClickable { setChecked(!checked) },
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Kind Max")
                Switch(checked = checked, onCheckedChange = setChecked)
            }
        }
    }
}
