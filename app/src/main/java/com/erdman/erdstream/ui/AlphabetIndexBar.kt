package com.erdman.erdstream.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val ALPHABET_INDEX_LETTERS = listOf("#") + ('A'..'Z').map { it.toString() }

/**
 * Right-edge A-Z index rail. Tap or drag to jump [listState] to the first
 * item at the index recorded for that letter in [letterToIndex]. A letter
 * with no entries snaps to the nearest letter that does have one, since the
 * rail always shows the full alphabet regardless of what's actually present.
 */
@Composable
fun AlphabetIndexBar(
    letterToIndex: Map<String, Int>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var railHeightPx by remember { mutableFloatStateOf(0f) }
    var activeLetter by remember { mutableStateOf<String?>(null) }

    fun jumpTo(letter: String) {
        val target = nearestAvailableLetter(letter, letterToIndex) ?: return
        activeLetter = letter
        scope.launch { listState.scrollToItem(letterToIndex.getValue(target)) }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(28.dp)
            .onSizeChanged { railHeightPx = it.height.toFloat() }
            .pointerInput(letterToIndex) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    jumpTo(letterForOffset(down.position.y, railHeightPx))
                    val pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) break
                        change.consume()
                        jumpTo(letterForOffset(change.position.y, railHeightPx))
                    }
                    activeLetter = null
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            ALPHABET_INDEX_LETTERS.forEach { letter ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = letter,
                        fontSize = 11.sp,
                        fontWeight = if (letter == activeLetter) FontWeight.Bold else FontWeight.Normal,
                        color = if (letterToIndex.containsKey(letter)) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                }
            }
        }

        activeLetter?.let { letter ->
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

private fun letterForOffset(y: Float, railHeightPx: Float): String {
    if (railHeightPx <= 0f) return ALPHABET_INDEX_LETTERS.first()
    val fraction = (y / railHeightPx).coerceIn(0f, 0.999f)
    val index = (fraction * ALPHABET_INDEX_LETTERS.size).toInt()
    return ALPHABET_INDEX_LETTERS[index.coerceIn(0, ALPHABET_INDEX_LETTERS.lastIndex)]
}

private fun nearestAvailableLetter(letter: String, letterToIndex: Map<String, Int>): String? {
    if (letterToIndex.isEmpty()) return null
    if (letterToIndex.containsKey(letter)) return letter
    val startIndex = ALPHABET_INDEX_LETTERS.indexOf(letter)
    for (i in startIndex + 1 until ALPHABET_INDEX_LETTERS.size) {
        val candidate = ALPHABET_INDEX_LETTERS[i]
        if (letterToIndex.containsKey(candidate)) return candidate
    }
    for (i in startIndex - 1 downTo 0) {
        val candidate = ALPHABET_INDEX_LETTERS[i]
        if (letterToIndex.containsKey(candidate)) return candidate
    }
    return null
}

/** Builds a first-letter -> first-matching-item-index map from an already-sorted list. */
fun <T> buildAlphabetIndex(items: List<T>, keyOf: (T) -> String): Map<String, Int> {
    val map = LinkedHashMap<String, Int>()
    items.forEachIndexed { index, item ->
        val firstChar = keyOf(item).trim().firstOrNull()?.uppercaseChar()
        val letter = if (firstChar != null && firstChar in 'A'..'Z') firstChar.toString() else "#"
        if (letter !in map) map[letter] = index
    }
    return map
}
