package com.shenaas.alarm.challenge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechMatchTest {
    private val easy = 0
    private val normal = 1
    private val hard = 2
    private val nightmare = 3

    @Test
    fun exactWordMatchesAtEveryLevel() {
        for (level in easy..nightmare) assertTrue(SpeechMatch.matches("okay coffee please", "coffee", level))
    }

    @Test
    fun accentedPronunciationsMatch() {
        // v/w, p/b, th/t, r/l and vowel shifts, as a recognizer transcribes them.
        assertTrue(SpeechMatch.matches("vindow", "window", hard))
        assertTrue(SpeechMatch.matches("panana", "banana", hard))
        assertTrue(SpeechMatch.matches("kofi", "coffee", nightmare))
        assertTrue(SpeechMatch.matches("locket", "rocket", hard))
        assertTrue(SpeechMatch.matches("tigar", "tiger", nightmare))
        assertTrue(SpeechMatch.matches("sebra", "zebra", nightmare))
    }

    @Test
    fun sleepySlipsMatchOnEasierLevels() {
        assertTrue(SpeechMatch.matches("pan cake", "pancake", nightmare))
        assertTrue(SpeechMatch.matches("cookies", "cookie", hard))
        assertTrue(SpeechMatch.matches("pengin", "penguin", normal))
        assertTrue(SpeechMatch.matches("umbrela", "umbrella", normal))
    }

    @Test
    fun differentWordsDoNotMatch() {
        assertFalse(SpeechMatch.matches("pen", "banana", easy))
        assertFalse(SpeechMatch.matches("hello there", "coffee", easy))
        assertFalse(SpeechMatch.matches("water", "tiger", normal))
        assertFalse(SpeechMatch.matches("", "coffee", easy))
    }

    @Test
    fun nightmareIsStricterThanEasy() {
        // One real slip in the consonants: fine when sleepy-tolerant, not on Nightmare.
        assertTrue(SpeechMatch.matches("copy", "coffee", easy))
        assertFalse(SpeechMatch.matches("copy", "coffee", nightmare))
    }

    @Test
    fun twoWordPhrasesNeedBothWordsInOrder() {
        assertTrue(SpeechMatch.matches("the purple dragon", "purple dragon", nightmare))
        assertTrue(SpeechMatch.matches("pirple dragon", "purple dragon", hard))
        assertFalse(SpeechMatch.matches("dragon purple", "purple dragon", hard))
        assertFalse(SpeechMatch.matches("purple", "purple dragon", easy))
    }
}
