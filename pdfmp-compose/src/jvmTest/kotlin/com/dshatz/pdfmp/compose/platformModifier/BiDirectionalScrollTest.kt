package com.dshatz.pdfmp.compose.platformModifier

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dshatz.pdfmp.compose.getMockedState
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
val biDirectionalScrollTests by testSuite {
    test("wheel scrolls the document") {
        runComposeUiTest {
            val state = getMockedState()
            setContent { Box(Modifier.size(100.dp).testTag("pdf").biDirectionalScroll(state)) }

            onNodeWithTag("pdf").performMouseInput {
                moveTo(Offset(10f, 10f))
                scroll(1f, ScrollWheel.Vertical)
            }
            waitForIdle()

            state.visiblePages.value.first().topCutoff shouldBeGreaterThan 0f
        }
    }

    test("Ctrl+wheel does not scroll the document") {
        runComposeUiTest {
            val state = getMockedState()
            setContent { Box(Modifier.size(100.dp).testTag("pdf").biDirectionalScroll(state)) }

            onNodeWithTag("pdf").performKeyInput { keyDown(Key.CtrlLeft) }
            onNodeWithTag("pdf").performMouseInput {
                moveTo(Offset(10f, 10f))
                scroll(1f, ScrollWheel.Vertical)
            }
            waitForIdle()

            state.visiblePages.value.first().topCutoff shouldBe 0f
        }
    }
}
