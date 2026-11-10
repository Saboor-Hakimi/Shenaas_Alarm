package com.shenaas.alarm.ui.ring.challenges

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shenaas.alarm.challenge.MathProblems
import com.shenaas.alarm.challenge.Sarcasm
import com.shenaas.alarm.challenge.Tuning
import com.shenaas.alarm.challenge.TypingPhrases
import com.shenaas.alarm.model.ChallengeSpec
import com.shenaas.alarm.ui.theme.Brand
import com.shenaas.alarm.ui.theme.ClockStyle
import com.shenaas.alarm.ui.theme.Jakarta
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun MathChallenge(spec: ChallengeSpec, cb: ChallengeCallbacks) {
    val random = remember { Random(spec.seed) }
    val level = spec.difficulty.level
    val total = remember { Tuning.mathProblems(spec) }
    val startedAt = remember { SystemClock.elapsedRealtime() }
    var solved by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf(MathProblems.next(level, random)) }
    var input by remember { mutableStateOf("") }
    val wobble = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    fun submit() {
        if (input.isEmpty()) return
        if (input.toIntOrNull() == problem.answer) {
            solved++
            input = ""
            if (solved >= total) {
                val expected = total * 9_000L
                cb.onWin((speedScore(expected, SystemClock.elapsedRealtime() - startedAt) - wrong * 0.25f).coerceIn(0f, 1f))
            } else {
                problem = MathProblems.next(level, random)
            }
        } else {
            wrong++
            input = ""
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch {
                wobble.animateTo(0f, keyframes { durationMillis = 400; -24f at 50; 24f at 120; -16f at 200; 10f at 280 })
            }
            if (spec.strict && solved > 0) {
                solved = 0
                cb.onFail("Wrong. Nightmare resets you to zero.")
            } else {
                cb.onFail(null)
            }
            problem = MathProblems.next(level, random)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.4f))
        Text("PROBLEM ${solved + 1} OF $total", style = MaterialTheme.typography.labelMedium, color = Brand.TextSecondary)
        Spacer(Modifier.height(12.dp))
        Text(
            "${problem.text} =",
            style = ClockStyle.copy(fontSize = if (problem.text.length > 14) 38.sp else 52.sp),
            color = Brand.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { translationX = wobble.value },
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth(0.7f)
                .height(76.dp)
                .clip(MaterialTheme.shapes.large)
                .background(Brand.SurfaceHigh)
                .border(2.dp, if (input.isEmpty()) Brand.Outline else Brand.Orange, MaterialTheme.shapes.large),
            contentAlignment = Alignment.Center,
        ) {
            Text(input.ifEmpty { "?" }, style = ClockStyle.copy(fontSize = 44.sp), color = if (input.isEmpty()) Brand.TextMuted else Brand.Text)
        }
        Spacer(Modifier.weight(0.6f))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓")
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key ->
                    val isSubmit = key == "✓"
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1.9f)
                            .clip(MaterialTheme.shapes.large)
                            .background(if (isSubmit) Brand.Orange else Brand.SurfaceHigh)
                            .clickable {
                                cb.onActivity()
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                when (key) {
                                    "⌫" -> input = input.dropLast(1)
                                    "✓" -> submit()
                                    else -> if (input.length < 6) input += key
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (key) {
                            "⌫" -> Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete", tint = Brand.Text)
                            "✓" -> Icon(Icons.Rounded.Check, "Submit", tint = Color.White, modifier = Modifier.size(30.dp))
                            else -> Text(key, style = ClockStyle.copy(fontSize = 30.sp), color = Brand.Text)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Swallows the copy/paste toolbar. Pasting is also caught by [TypingChallenge]'s insert-size check. */
private object NoTextToolbar : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden
    override fun hide() = Unit
    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) = Unit
}

private fun normalize(text: String, caseSensitive: Boolean): String {
    val collapsed = text.trim().replace(Regex("\\s+"), " ")
    return if (caseSensitive) collapsed else collapsed.lowercase()
}

@Composable
fun TypingChallenge(spec: ChallengeSpec, cb: ChallengeCallbacks) {
    val random = remember { Random(spec.seed) }
    val level = spec.difficulty.level
    val rounds = remember { Tuning.typingRounds(spec) }
    val caseSensitive = remember { Tuning.typingCaseSensitive(spec) }
    val msPerChar = remember { Tuning.typingMsPerChar(spec) }
    var round by remember { mutableIntStateOf(0) }
    var phrase by remember { mutableStateOf(TypingPhrases.pick(level, random)) }
    var typed by remember { mutableStateOf("") }
    val limitMs = 6_000L + phrase.length * msPerChar
    var deadline by remember { mutableLongStateOf(SystemClock.elapsedRealtime() + limitMs) }
    var remaining by remember { mutableLongStateOf(limitMs) }
    var timeUsedMs by remember { mutableLongStateOf(0L) }
    var budgetMs by remember { mutableLongStateOf(0L) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun nextPhrase() {
        var next = TypingPhrases.pick(level, random)
        while (next == phrase) next = TypingPhrases.pick(level, random)
        phrase = next
        typed = ""
        val limit = 6_000L + next.length * msPerChar
        deadline = SystemClock.elapsedRealtime() + limit
        remaining = limit
    }

    LaunchedEffect(phrase, round) {
        focus.requestFocus()
        keyboard?.show()
        while (true) {
            delay(100)
            remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0) {
                cb.onFail(Sarcasm.pick(Sarcasm.tooSlow))
                nextPhrase()
            }
        }
    }

    fun onType(value: String) {
        if (value.length - typed.length > 1) {
            // Anything arriving in one chunk is a paste or an autofill. Absolutely not.
            cb.onFail(Sarcasm.pick(Sarcasm.paste))
            if (spec.strict) typed = ""
            return
        }
        typed = value
        cb.onActivity()
        if (normalize(value, caseSensitive) == normalize(phrase, caseSensitive)) {
            val limit = 6_000L + phrase.length * msPerChar
            timeUsedMs += limit - remaining.coerceAtLeast(0)
            budgetMs += limit
            round++
            if (round >= rounds) {
                // Finishing in half the allowed time is a perfect score.
                cb.onWin(speedScore(budgetMs / 2, timeUsedMs))
            } else {
                nextPhrase()
            }
        }
    }

    val limitFraction = (remaining.toFloat() / limitMs).coerceIn(0f, 1f)
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PHRASE ${round + 1} OF $rounds", style = MaterialTheme.typography.labelMedium, color = Brand.TextSecondary, modifier = Modifier.weight(1f))
            Text("${(remaining.coerceAtLeast(0) / 1000) + 1}s", style = MaterialTheme.typography.titleMedium, color = if (limitFraction < 0.3f) Brand.HellRed else Brand.Text)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(Brand.SurfaceHighest),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(limitFraction)
                    .background(if (limitFraction < 0.3f) Brand.HellRed else Brand.Orange),
            )
        }
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(Brand.Surface)
                .border(1.dp, Brand.Outline, MaterialTheme.shapes.large)
                .padding(20.dp),
        ) {
            // Plain, unselectable text so it can't be copied.
            Text(
                buildAnnotatedString {
                    phrase.forEachIndexed { i, ch ->
                        val color = when {
                            i >= typed.length -> Brand.TextSecondary
                            typed[i] == ch || (!caseSensitive && typed[i].equals(ch, ignoreCase = true)) -> Brand.Green
                            else -> Brand.HellRed
                        }
                        withStyle(SpanStyle(color = color)) { append(ch) }
                    }
                },
                fontFamily = Jakarta,
                fontSize = 24.sp,
                lineHeight = 32.sp,
            )
        }
        val correct = typed.zip(phrase).takeWhile { (a, b) -> a == b || (!caseSensitive && a.equals(b, ignoreCase = true)) }.size
        Text(
            "$correct / ${phrase.length}",
            style = MaterialTheme.typography.titleMedium,
            color = if (correct == phrase.length) Brand.Green else Brand.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        )
        Spacer(Modifier.height(12.dp))
        CompositionLocalProvider(LocalTextToolbar provides NoTextToolbar) {
            BasicTextField(
                value = typed,
                onValueChange = ::onType,
                singleLine = false,
                textStyle = MaterialTheme.typography.titleLarge.copy(color = Brand.Text),
                cursorBrush = SolidColor(Brand.Orange),
                keyboardOptions = KeyboardOptions(
                    // Password keyboards turn off suggestions, autocorrect and swipe typing.
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(Brand.SurfaceHigh)
                            .border(2.dp, Brand.Orange.copy(alpha = 0.6f), MaterialTheme.shapes.large)
                            .padding(18.dp),
                    ) {
                        if (typed.isEmpty()) Text("Type it. Exactly.", style = MaterialTheme.typography.titleLarge, color = Brand.TextMuted)
                        inner()
                    }
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (caseSensitive) "Capitals and punctuation count. No pasting." else "Capitals don't matter. Pasting does.",
            style = MaterialTheme.typography.bodySmall,
            color = Brand.TextMuted,
        )
    }
}
