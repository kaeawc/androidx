/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.ui.text

import androidx.compose.Composable
import androidx.compose.StructurallyEqual
import androidx.compose.emptyContent
import androidx.compose.getValue
import androidx.compose.mutableStateOf
import androidx.compose.onCommit
import androidx.compose.remember
import androidx.compose.setValue
import androidx.ui.core.Constraints
import androidx.ui.core.DensityAmbient
import androidx.ui.core.FontLoaderAmbient
import androidx.ui.core.HorizontalAlignmentLine
import androidx.ui.core.Layout
import androidx.ui.core.LayoutCoordinates
import androidx.ui.core.Modifier
import androidx.ui.core.drawBehind
import androidx.ui.core.onPositioned
import androidx.ui.core.selection.Selectable
import androidx.ui.core.selection.SelectionRegistrarAmbient
import androidx.ui.graphics.Color
import androidx.ui.text.font.Font
import androidx.ui.text.selection.TextSelectionDelegate
import androidx.ui.text.style.TextAlign
import androidx.ui.text.style.TextOverflow
import androidx.ui.unit.Density
import androidx.ui.unit.IntPx
import androidx.ui.unit.PxPosition
import androidx.ui.unit.ipx
import androidx.ui.unit.max
import androidx.ui.unit.min
import androidx.ui.unit.round

/** The default selection color if none is specified. */
internal val DefaultSelectionColor = Color(0x6633B5E5)

/**
 * CoreText is a low level element that displays text with multiple different styles. The text to
 * display is described using a [AnnotatedString]. Typically you will instead want to use
 * [androidx.ui.foundation.Text], which is a higher level Text element that contains semantics and
 * consumes style information from a theme.
 *
 * @param text AnnotatedString encoding a styled text.
 * @param modifier Modifier to apply to this layout node.
 * @param style Style configuration for the text such as color, font, line height etc.
 * @param softWrap Whether the text should break at soft line breaks. If false, the glyphs in the
 * text will be positioned as if there was unlimited horizontal space. If [softWrap] is false,
 * [overflow] and [TextAlign] may have unexpected effects.
 * @param overflow How visual overflow should be handled.
 * @param maxLines An optional maximum number of lines for the text to span, wrapping if
 * necessary. If the text exceeds the given number of lines, it will be truncated according to
 * [overflow] and [softWrap]. If it is not null, then it must be greater than zero.
 * @param onTextLayout Callback that is executed when a new text layout is calculated.
 */
@Composable
fun CoreText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle,
    softWrap: Boolean,
    overflow: TextOverflow,
    maxLines: Int,
    onTextLayout: (TextLayoutResult) -> Unit
) {
    require(maxLines > 0) { "maxLines should be greater than 0" }

    // Ambients
    // selection registrar, if no SelectionContainer is added ambient value will be null
    val selectionRegistrar = SelectionRegistrarAmbient.current
    val density = DensityAmbient.current
    val resourceLoader = FontLoaderAmbient.current

    val state = remember {
        TextState(
            TextDelegate(
                text = text,
                style = style,
                density = density,
                softWrap = softWrap,
                resourceLoader = resourceLoader,
                overflow = overflow,
                maxLines = maxLines
            )
        )
    }
    state.textDelegate = updateTextDelegate(
        current = state.textDelegate,
        text = text,
        style = style,
        density = density,
        softWrap = softWrap,
        resourceLoader = resourceLoader,
        overflow = overflow,
        maxLines = maxLines
    )

    Layout(
        children = emptyContent(),
        modifier = modifier.drawBehind {
            state.layoutResult?.let { layoutResult ->
                state.selectionRange?.let {
                    TextDelegate.paintBackground(
                        it.min,
                        it.max,
                        DefaultSelectionColor,
                        this,
                        layoutResult
                    )
                }
                TextDelegate.paint(this, layoutResult)
            }
        }.onPositioned {
            // Get the layout coordinates of the text composable. This is for hit test of
            // cross-composable selection.
            val oldLayoutCoordinates = state.layoutCoordinates
            state.layoutCoordinates = it

            if ((oldLayoutCoordinates == null || !oldLayoutCoordinates.isAttached) &&
                it.isAttached
            ) {
                selectionRegistrar?.onPositionChange()
            }

            if (oldLayoutCoordinates != null && oldLayoutCoordinates.isAttached &&
                it.isAttached
            ) {
                if (
                    oldLayoutCoordinates.localToGlobal(PxPosition.Origin) !=
                    it.localToGlobal(PxPosition.Origin)
                ) {
                    selectionRegistrar?.onPositionChange()
                }
            }
        },
        minIntrinsicWidthMeasureBlock = { _, _, layoutDirection ->
            state.textDelegate.layoutIntrinsics(layoutDirection)
            state.textDelegate.minIntrinsicWidth
        },
        minIntrinsicHeightMeasureBlock = { _, width, layoutDirection ->
            // given the width constraint, determine the min height
            state.textDelegate
                .layout(
                    Constraints(
                        0.ipx,
                        width,
                        0.ipx,
                        IntPx.Infinity
                    ),
                    layoutDirection
                ).size.height
        },
        maxIntrinsicWidthMeasureBlock = { _, _, layoutDirection ->
            state.textDelegate.layoutIntrinsics(layoutDirection)
            state.textDelegate.maxIntrinsicWidth
        },
        maxIntrinsicHeightMeasureBlock = { _, width, layoutDirection ->
            state.textDelegate
                .layout(
                    Constraints(
                        0.ipx,
                        width,
                        0.ipx,
                        IntPx.Infinity
                    ),
                    layoutDirection
                ).size.height
        }
    ) { _, constraints, layoutDirection ->
        val layoutResult = state.textDelegate.layout(
            constraints,
            layoutDirection,
            state.layoutResult
        )
        if (state.layoutResult != layoutResult) {
            onTextLayout(layoutResult)
        }
        state.layoutResult = layoutResult

        layout(
            layoutResult.size.width,
            layoutResult.size.height,
            // Provide values for the alignment lines defined by text - the first
            // and last baselines of the text. These can be used by parent layouts
            // to position this text or align this and other texts by baseline.
            //
            // Note: we use round to make IntPx but any rounding doesn't work well here since
            // the layout system works with integer pixels but baseline can be in a middle of
            // the pixel. So any rounding doesn't offer the pixel perfect baseline. We use
            // round just because the Android framework is doing float-to-int conversion with
            // round.
            // https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/jni/android/graphics/Paint.cpp;l=635?q=Paint.cpp
            mapOf(
                FirstBaseline to layoutResult.firstBaseline.round(),
                LastBaseline to layoutResult.lastBaseline.round()
            )
        ) {}
    }

    onCommit(selectionRegistrar) {
        // if no SelectionContainer is added as parent selectionRegistrar will be null
        val id: Selectable? =
            selectionRegistrar?.let {
                selectionRegistrar.subscribe(
                    TextSelectionDelegate(
                        selectionRangeUpdate = { state.selectionRange = it },
                        coordinatesCallback = { state.layoutCoordinates },
                        layoutResultCallback = { state.layoutResult }
                    )
                )
            }

        onDispose {
            // unregister only if any id was provided by SelectionRegistrar
            id?.let { selectionRegistrar.unsubscribe(id) }
        }
    }
}

/**
 * [AlignmentLine] defined by the baseline of a first line of a [CoreText].
 */
val FirstBaseline = HorizontalAlignmentLine(::min)

/**
 * [AlignmentLine] defined by the baseline of the last line of a [CoreText].
 */
val LastBaseline = HorizontalAlignmentLine(::max)

private class TextState(
    var textDelegate: TextDelegate
) {
    /**
     * The current selection range, used by selection.
     * This should be a state as every time we update the value during the selection we
     * need to redraw it. @Model observation during onDraw callback will make it work.
     */
    var selectionRange by mutableStateOf<TextRange?>(null, StructurallyEqual)
    /** The last layout coordinates for the Text's layout, used by selection */
    var layoutCoordinates: LayoutCoordinates? = null
    /** The latest TextLayoutResult calculated in the measure block */
    var layoutResult: TextLayoutResult? = null
}

/**
 * Returns the [TextDelegate] passed as a [current] param if the input didn't change
 * otherwise creates a new [TextDelegate].
 */
internal fun updateTextDelegate(
    current: TextDelegate,
    text: AnnotatedString,
    style: TextStyle,
    density: Density,
    resourceLoader: Font.ResourceLoader,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE
): TextDelegate {
    return if (current.text != text ||
        current.style != style ||
        current.softWrap != softWrap ||
        current.overflow != overflow ||
        current.maxLines != maxLines ||
        current.density != density
    ) {
        TextDelegate(
            text = text,
            style = style,
            softWrap = softWrap,
            overflow = overflow,
            maxLines = maxLines,
            density = density,
            resourceLoader = resourceLoader
        )
    } else {
        current
    }
}