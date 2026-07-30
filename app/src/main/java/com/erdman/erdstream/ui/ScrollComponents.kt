package com.erdman.erdstream.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Discrete-jump list scrolling for e-ink: a drag gesture jumps the list by
 * [SCROLL_STEP] items via [LazyListState.scrollToItem] (an instant jump, no
 * fling/inertia animation) instead of following the drag continuously.
 * Pair with `LazyColumn(userScrollEnabled = false, ...)` so Compose's own
 * animated drag/fling scrolling is fully disabled, and with [EInkScrollbar]
 * for a visible up/down + position affordance.
 */
const val SCROLL_STEP = 4

@Composable
fun Modifier.eInkVerticalScroll(
    state: LazyListState,
    scope: CoroutineScope,
    isScrollable: Boolean
): Modifier {
    var isDragging by remember { mutableStateOf(false) }
    return this.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragEnd = { isDragging = false }
        ) { _, dragAmount ->
            if (!isDragging && isScrollable) {
                isDragging = true
                val direction = if (dragAmount > 0) -1 else 1
                val newIdx = (state.firstVisibleItemIndex + direction * SCROLL_STEP)
                    .coerceIn(0, (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
                scope.launch { state.scrollToItem(newIdx) }
            }
        }
    }
}

@Composable
fun EInkScrollbar(
    state: LazyListState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    val canScrollForward by remember { derivedStateOf { state.canScrollForward } }
    val canScrollBackward by remember { derivedStateOf { state.canScrollBackward } }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outline

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(32.dp)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = {
                val newIdx = (state.firstVisibleItemIndex - SCROLL_STEP).coerceAtLeast(0)
                scope.launch { state.scrollToItem(newIdx) }
            },
            modifier = Modifier.size(32.dp).padding(top = 8.dp)
        ) {
            Icon(
                Icons.Default.KeyboardArrowUp,
                contentDescription = "Scroll up",
                modifier = Modifier.size(20.dp),
                tint = if (canScrollBackward) onSurface else outline
            )
        }

        Canvas(
            modifier = Modifier
                .width(8.dp)
                .weight(1f)
                .border(0.5.dp, onSurface, RoundedCornerShape(4.dp))
        ) {
            val layoutInfo = state.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@Canvas

            val totalItems = layoutInfo.totalItemsCount
            val firstItem = visibleItems.first()
            val lastItem = visibleItems.last()

            val viewportSize = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            if (viewportSize <= 0) return@Canvas

            // Estimate total content height based on average item size
            val visibleItemsHeight = lastItem.offset + lastItem.size - firstItem.offset
            val averageItemSize = visibleItemsHeight.toFloat() / visibleItems.size
            val estimatedTotalSize = (averageItemSize * totalItems)
                .coerceAtLeast(viewportSize.toFloat())

            // If we can scroll, we want the thumb to be smaller than the track
            val isActuallyScrollable = state.canScrollForward || state.canScrollBackward
            val sliderFraction = if (isActuallyScrollable) {
                (viewportSize.toFloat() / estimatedTotalSize).coerceIn(0.1f, 0.9f)
            } else {
                1f
            }

            val sliderHeight = (size.height * sliderFraction).coerceAtLeast(16.dp.toPx())
            val maxOffset = size.height - sliderHeight

            val currentScrollOffset = state.firstVisibleItemIndex * averageItemSize + state.firstVisibleItemScrollOffset
            val maxScrollOffset = (estimatedTotalSize - viewportSize).coerceAtLeast(1f)
            val scrollFraction = (currentScrollOffset / maxScrollOffset).coerceIn(0f, 1f)

            val sliderTop = maxOffset * scrollFraction

            drawRoundRect(
                color = onSurface,
                topLeft = Offset(0f, sliderTop),
                size = Size(size.width, sliderHeight),
                cornerRadius = CornerRadius(size.width / 2, size.width / 2)
            )
        }

        IconButton(
            onClick = {
                val newIdx = (state.firstVisibleItemIndex + SCROLL_STEP)
                    .coerceAtMost(state.layoutInfo.totalItemsCount - 1)
                scope.launch { state.scrollToItem(newIdx) }
            },
            modifier = Modifier.size(32.dp).padding(bottom = 8.dp)
        ) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = "Scroll down",
                modifier = Modifier.size(20.dp),
                tint = if (canScrollForward) onSurface else outline
            )
        }
    }
}
