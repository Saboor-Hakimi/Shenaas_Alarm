package com.shenaas.alarm.challenge

import kotlin.math.max

/**
 * Decides whether a recognizer hypothesis contains a target word, tolerating how people actually
 * talk two minutes after waking up, and in a second language: slurred or dropped sounds, swapped
 * voicing (b/p, d/t, g/k, v/f/w, s/z), th spoken as t, r/l mix-ups, and any vowel quality.
 *
 * Every word is compared twice, by spelling and by a sound-alike key, and the better of the two counts.
 */
object SpeechMatch {
    /** Minimum similarity per difficulty. Nightmare still forgives accents but not a different word. */
    private val thresholds = floatArrayOf(0.60f, 0.68f, 0.78f, 0.86f)
    private const val KEY_WEIGHT = 0.95f

    fun threshold(level: Int): Float = thresholds[level.coerceIn(0, thresholds.lastIndex)]

    fun matches(hypothesis: String, target: String, level: Int): Boolean = score(hypothesis, target) >= threshold(level)

    /** Best similarity (0..1) of [target] to anything in [hypothesis]. Multi-word targets must appear in order. */
    fun score(hypothesis: String, target: String): Float {
        val tokens = words(hypothesis)
        val targetWords = words(target)
        if (tokens.isEmpty() || targetWords.isEmpty()) return 0f
        if (targetWords.size == 1) return bestIn(tokens, 0, targetWords[0]).first
        var from = 0
        var worst = 1f
        for (word in targetWords) {
            val (best, at) = bestIn(tokens, from, word)
            if (at < 0) return 0f
            worst = minOf(worst, best)
            from = at + 1
        }
        return worst
    }

    /** Best match for [target] among tokens from [from] on, also trying adjacent pairs ("pop corn"). */
    private fun bestIn(tokens: List<String>, from: Int, target: String): Pair<Float, Int> {
        var best = 0f
        var at = -1
        for (i in from until tokens.size) {
            val single = similarity(tokens[i], target)
            val joined = if (i + 1 < tokens.size) similarity(tokens[i] + tokens[i + 1], target) else 0f
            val s = max(single, joined)
            if (s > best) {
                best = s
                at = i
            }
        }
        return best to at
    }

    fun similarity(heard: String, target: String): Float {
        if (heard == target) return 1f
        val text = 1f - distance(heard, target).toFloat() / max(heard.length, target.length)
        val hk = soundKey(heard)
        val tk = soundKey(target)
        val sound = if (hk.isEmpty() || tk.isEmpty()) 0f else (1f - distance(hk, tk).toFloat() / max(hk.length, tk.length)) * KEY_WEIGHT
        // Plurals and "-ing" endings are the same word for our purposes.
        val stem = if (heard.removeSuffix("s") == target || heard.removeSuffix("ing") == target) 0.95f else 0f
        return maxOf(text, sound, stem)
    }

    /**
     * A sound-alike key: sounds that sleepy or non-native speakers commonly blur are merged, vowels
     * all become one vowel, and repeats collapse. "coffee", "kofi" and "cofee" all become "kafa".
     */
    fun soundKey(word: String): String {
        var w = word.lowercase().filter { it in 'a'..'z' }
        if (w.isEmpty()) return ""
        w = w
            .replace(Regex("^kn"), "n").replace(Regex("^wr"), "r").replace(Regex("mb$"), "m")
            .replace("tch", "j").replace("sh", "j").replace("ch", "j").replace("dg", "j")
            .replace("ph", "f").replace("gh", "").replace("th", "t").replace("ck", "k").replace("qu", "kw").replace("wh", "w")
            .replace(Regex("c(?=[eiy])"), "s").replace("x", "ks")
        val out = StringBuilder()
        for (c in w) {
            val mapped = when (c) {
                'a', 'e', 'i', 'o', 'u', 'y' -> 'a'
                'b', 'p' -> 'p'
                'd', 't' -> 't'
                'g', 'k', 'c', 'q' -> 'k'
                'v', 'f', 'w' -> 'f'
                's', 'z' -> 's'
                'l', 'r' -> 'r'
                'h' -> null
                else -> c
            }
            if (mapped != null && (out.isEmpty() || out.last() != mapped)) out.append(mapped)
        }
        return out.toString()
    }

    fun words(text: String): List<String> =
        text.lowercase().replace(Regex("[^a-z ]"), " ").split(' ').filter { it.isNotBlank() }

    private fun distance(a: String, b: String): Int {
        val row = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = row[0]
            row[0] = i
            for (j in 1..b.length) {
                val tmp = row[j]
                row[j] = minOf(row[j] + 1, row[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return row[b.length]
    }
}
