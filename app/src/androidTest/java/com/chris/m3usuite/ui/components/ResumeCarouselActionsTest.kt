package com.chris.m3usuite.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.chris.m3usuite.ui.actions.MediaActionDefaults
import com.chris.m3usuite.ui.actions.MediaActionId
import org.junit.Rule
import org.junit.Test

class ResumeCarouselActionsTest {
    @get:Rule
    val rule: AndroidComposeTestRule<*, *> = createAndroidComposeRule<ComponentActivity>()

    @Composable
    private fun Fixture(onPlay: () -> Unit, onClear: () -> Unit) {
        val items = listOf(
            VodResume(mediaId = 2_000_000_000_123L, name = "Test VOD", url = "http://example", positionSecs = 120)
        )
        ResumeVodRow(items = items, onPlay = { onPlay() }, onClear = { onClear() })
    }

    @Test
    fun resume_vod_row_actions_present_and_clickable() {
        var played = false
        var cleared = false
        rule.setContent { Fixture(onPlay = { played = true }, onClear = { cleared = true }) }

        // Play present
        rule.onNodeWithTag(MediaActionDefaults.testTagFor(MediaActionId.Play), useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        assert(played)

        // Remove present
        rule.onNodeWithTag(MediaActionDefaults.testTagFor(MediaActionId.RemoveFromList), useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        assert(cleared)
    }
}
