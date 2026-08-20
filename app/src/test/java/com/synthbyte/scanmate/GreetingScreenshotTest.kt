package com.synthbyte.scanmate

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.synthbyte.scanmate.ui.theme.ScanMateTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GreetingScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun scanmate_theme_renders_text() {
        composeTestRule.setContent { ScanMateTheme { Text("ScanMate AI Pro") } }
        composeTestRule.onNodeWithText("ScanMate AI Pro").assertIsDisplayed()
    }
}
