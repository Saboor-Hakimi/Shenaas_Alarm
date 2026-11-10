package com.shenaas.alarm.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shenaas.alarm.ui.theme.Brand
import com.shenaas.alarm.ui.theme.ClockStyle
import kotlin.math.abs

private const val VISIBLE = 5
private const val LOOPS = 2000

/**
 * A snapping scroll wheel. Looping wheels repeat their values; non-looping ones are padded with
 * blank rows so the first and last values can reach the middle.
 */
@Composable
private fun Wheel(
    count: Int,
    selected: Int,
    onSelected: (Int) -> Unit,
    label: (Int) -> String,
    modifier: Modifier = Modifier,
    loop: Boolean = true,
    itemHeight: Dp = 56.dp,
    fontSize: Int = 44,
) {
    val pad = VISIBLE / 2
    val total = if (loop) count * LOOPS else count + pad * 2
    val initial = if (loop) (LOOPS / 2) * count + selected - pad else selected
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val haptics = LocalHapticFeedback.current
    val callback by rememberUpdatedState(onSelected)

    val centered by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index ?: (initial + pad)
        }
    }
    fun valueAt(index: Int) = if (loop) index.mod(count) else (index - pad).coerceIn(0, count - 1)

    LaunchedEffect(centered) {
        val value = valueAt(centered)
        if (value != selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        callback(value)
    }

    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.height(itemHeight * VISIBLE),
    ) {
        items(total) { index ->
            val blank = !loop && (index < pad || index >= count + pad)
            val distance = abs(index - centered)
            Box(
                Modifier
                    .height(itemHeight)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = when (distance) {
                            0 -> 1f
                            1 -> 0.45f
                            else -> 0.18f
                        }
                        val scale = if (distance == 0) 1f else 0.82f
                        scaleX = scale
                        scaleY = scale
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (!blank) {
                    Text(
                        label(valueAt(index)),
                        style = ClockStyle.copy(fontSize = fontSize.sp),
                        color = if (distance == 0) Color.White else Brand.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
fun WheelTimePicker(
    hour: Int,
    minute: Int,
    is24h: Boolean,
    onChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemHeight = 56.dp
    val latestHour by rememberUpdatedState(hour)
    val latestMinute by rememberUpdatedState(minute)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Selection band behind the middle row.
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(MaterialTheme.shapes.medium)
                .background(Brush.horizontalGradient(listOf(Brand.Orange.copy(alpha = 0.18f), Brand.HellRed.copy(alpha = 0.18f)))),
        )
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            if (is24h) {
                Wheel(24, hour, { onChange(it, latestMinute) }, { "%02d".format(it) }, Modifier.width(96.dp), itemHeight = itemHeight)
            } else {
                val h12 = if (hour % 12 == 0) 12 else hour % 12
                Wheel(
                    count = 12,
                    selected = h12 - 1,
                    onSelected = { index ->
                        val pm = latestHour >= 12
                        val h = (index + 1) % 12
                        onChange(if (pm) h + 12 else h, latestMinute)
                    },
                    label = { "${it + 1}" },
                    modifier = Modifier.width(96.dp),
                    itemHeight = itemHeight,
                )
            }
            Text(":", style = ClockStyle.copy(fontSize = 44.sp), color = Brand.Orange, modifier = Modifier.padding(horizontal = 4.dp))
            Wheel(60, minute, { onChange(latestHour, it) }, { "%02d".format(it) }, Modifier.width(96.dp), itemHeight = itemHeight)
            if (!is24h) {
                Wheel(
                    count = 2,
                    selected = if (hour >= 12) 1 else 0,
                    onSelected = { index ->
                        val base = latestHour % 12
                        onChange(if (index == 1) base + 12 else base, latestMinute)
                    },
                    label = { if (it == 0) "AM" else "PM" },
                    modifier = Modifier.width(76.dp),
                    loop = false,
                    itemHeight = itemHeight,
                    fontSize = 24,
                )
            }
        }
    }
}
