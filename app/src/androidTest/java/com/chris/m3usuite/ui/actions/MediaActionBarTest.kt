package com.chris.m3usuite.ui.actions

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.input.key.Key
import org.junit.Rule
import org.junit.Test

class MediaActionBarTest {
    @get:Rule
    val rule: AndroidComposeTestRule<*, *> = createAndroidComposeRule<ComponentActivity>()

    @Composable
    private fun Fixture(onClicked: (MediaActionId) -> Unit) {
        val actions = listOf(
            MediaAction(MediaActionId.Resume, label = "Fortsetzen", onClick = { onClicked(MediaActionId.Resume) }),
            MediaAction(MediaActionId.Play, label = "Abspielen", primary = true, onClick = { onClicked(MediaActionId.Play) }),
            MediaAction(MediaActionId.Trailer, label = "Trailer", onClick = { onClicked(MediaActionId.Trailer) }),
            MediaAction(MediaActionId.Share, label = "Teilen", onClick = { onClicked(MediaActionId.Share) })
        )
        MediaActionBar(actions = actions)
    }

    @Test
    fun actions_present_and_clickable_in_declared_order() {
        var last: MediaActionId? = null
        rule.setContent { Fixture { last = it } }

        // Presence and enabled
        val tags = listOf(
            MediaActionDefaults.testTagFor(MediaActionId.Resume),
            MediaActionDefaults.testTagFor(MediaActionId.Play),
            MediaActionDefaults.testTagFor(MediaActionId.Trailer),
            MediaActionDefaults.testTagFor(MediaActionId.Share)
        )
        tags.forEach { tag ->
            rule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed().assertIsEnabled()
        }

        // Click Play and verify callback fired
        rule.onNodeWithTag(MediaActionDefaults.testTagFor(MediaActionId.Play), useUnmergedTree = true).performClick()
        assert(last == MediaActionId.Play)

        // Basic DPAD focus path: focus Resume, then RIGHT → Play
        val resumeTag = MediaActionDefaults.testTagFor(MediaActionId.Resume)
        val playTag = MediaActionDefaults.testTagFor(MediaActionId.Play)
        rule.onNodeWithTag(resumeTag, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.RequestFocus)
        rule.onNodeWithTag(resumeTag, useUnmergedTree = true).assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithTag(playTag, useUnmergedTree = true).assertIsFocused()
        rule.onNodeWithTag(resumeTag, useUnmergedTree = true).assertIsNotFocused()
    }
}

