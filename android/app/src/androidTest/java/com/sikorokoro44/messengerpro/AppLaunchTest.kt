package com.sikorokoro44.messengerpro

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device-level smoke test for the Batch 1 requirement: the app opens to a stable shell. */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesToStableShell() {
        composeRule.onNodeWithText("Messenger Pro").assertIsDisplayed()
        composeRule.onNodeWithText("Foundation build · Authentication is not connected yet")
            .assertIsDisplayed()
    }
}
