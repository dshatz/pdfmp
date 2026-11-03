package com.dshatz.pdfmp.compose.platformModifier

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dshatz.pdfmp.compose.getMockedState
import com.dshatz.pdfmp.compose.state.PdfState
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
val desktopZoomTests by testSuite {
    // Viewport is 1000 px wide, so the scaled page width is 1000 * zoom.
    fun PdfState.zoom() = visiblePages.value.first().scaledWidth / 1000f

    test("one Ctrl+wheel notch zooms by 1.1") {
        runComposeUiTest {
            val state = getMockedState()
            setContent { Box(Modifier.size(100.dp).testTag("pdf").platformPageTransformModifier(state)) }

            onNodeWithTag("pdf").performKeyInput { keyDown(Key.CtrlLeft) }
            onNodeWithTag("pdf").performMouseInput {
                moveTo(Offset.Zero)
                scroll(-1f, ScrollWheel.Vertical)
            }
            waitForIdle()

            state.zoom() shouldBe (1.1f plusOrMinus 1e-3f)
        }
    }

    test("Ctrl+wheel consumed further out does not zoom") {
        runComposeUiTest {
            val state = getMockedState()
            setContent {
                Box(
                    Modifier
                        .onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { event ->
                            event.changes.forEach { it.consume() }
                        }
                ) {
                    Box(Modifier.size(100.dp).testTag("pdf").platformPageTransformModifier(state))
                }
            }

            onNodeWithTag("pdf").performKeyInput { keyDown(Key.CtrlLeft) }
            onNodeWithTag("pdf").performMouseInput {
                moveTo(Offset.Zero)
                scroll(-1f, ScrollWheel.Vertical)
            }
            waitForIdle()

            state.zoom() shouldBe 1f
        }
    }
}
