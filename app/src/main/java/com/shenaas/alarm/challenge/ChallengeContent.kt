package com.shenaas.alarm.challenge

import kotlin.random.Random

object Sarcasm {
    val fails = listOf(
        "You lazy ahh!",
        "Nice try. You're not going back to sleep.",
        "Get out of bed!",
        "Your alarm isn't going anywhere.",
        "Wake up. We both know you need this.",
        "Was that your brain or your pillow answering?",
        "Even your coffee is disappointed.",
        "Wrong. The sun is up and so are you.",
        "That was painful to watch.",
        "Sleeping Beauty called. She wants her excuses back.",
        "Your bed is not your friend. We are.",
        "Bold of you to assume that would work.",
    )
    val idle = listOf(
        "Your alarm isn't going anywhere.",
        "Nice try. You're not going back to sleep.",
        "Did you just fall asleep mid-challenge? Wow.",
        "Hello? Anybody in there?",
    )
    val headlines = listOf("WAKE UP.", "GET UP. NOW.", "RISE & GRIND.", "BED IS CANCELLED.", "UP. NOW. GO.")
    val taunts = listOf(
        "Wake up. We both know you need this.",
        "Get out of bed!",
        "Snooze won't save you.",
        "Your future self is begging you.",
        "The bed is lava. Move.",
    )
    val paste = listOf("Copy-paste? In THIS economy?", "Pasting is cheating. Type it.", "Nice try, keyboard warrior.")
    val tooSlow = listOf("Too slow. New one.", "Time's up, sleepyhead.", "Were you typing with your nose?")
    const val RESUMED = "Rebooting won't save you."
    const val WAKE_CHECK_FAILED = "You fell back asleep, didn't you?"
    const val SWAPPED = "Your phone can't do that one right now, so here's something worse."
    const val SUCCESS = "Congratulations, you're finally awake!"

    fun pick(list: List<String>): String = list.random()
}

object BeatWords {
    // Common words with clear, distinct sounds that are easy to say half asleep or in a second
    // language. No tongue-twisters, no near-homophones, no "th".
    private val words = listOf(
        "coffee", "banana", "tiger", "rocket", "pizza", "dragon", "guitar", "window", "purple", "monkey",
        "chicken", "pancake", "sunshine", "jungle", "magic", "zebra", "castle", "cookie", "robot", "planet",
        "butter", "pillow", "breakfast", "morning", "shower", "lemon", "mango", "turtle", "camera", "basket",
        "yellow", "rainbow", "penguin", "tomato", "popcorn", "elephant", "umbrella", "football", "music", "doctor",
        "garden", "kitten", "apple", "orange", "water", "sugar", "honey", "taxi", "piano", "hello",
    )
    private val adjectives = listOf("angry", "purple", "crazy", "golden", "happy", "giant", "spicy", "frozen", "flying", "sleepy")

    /** Distinct words for one run; Nightmare uses two-word combos. */
    fun sequence(count: Int, twoWords: Boolean, random: Random): List<String> {
        val nouns = words.shuffled(random).take(count)
        if (!twoWords) return nouns
        val adj = adjectives.shuffled(random)
        return nouns.mapIndexed { i, noun -> "${adj[i % adj.size]} $noun" }
    }
}

object TypingPhrases {
    private val easy = listOf(
        "wake up now", "i am awake", "coffee time", "get out of bed", "good morning", "rise and shine", "no more snooze",
    )
    private val normal = listOf(
        "I am awake and I will not go back to sleep",
        "My bed is not my boss",
        "Today I choose coffee over my pillow",
        "Getting out of bed was my idea",
        "Snoozing is for quitters and I am no quitter",
        "The early bird gets the worm and the coffee",
    )
    private val hard = listOf(
        "Rise & grind: Shenaas never sleeps, and neither do I!",
        "I promise, solemnly & loudly, to stay awake (for real).",
        "Wake up. We both know you need this.",
        "Monday? Tuesday? Doesn't matter: I'm UP!",
        "My pillow is a liar; my alarm is my BEST friend.",
    )
    private const val NIGHTMARE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"

    fun pick(level: Int, random: Random): String = when (level) {
        0 -> easy.random(random)
        1 -> normal.random(random)
        2 -> hard.random(random)
        else -> if (random.nextBoolean()) hard.random(random) else gibberish(random)
    }

    private fun gibberish(random: Random): String =
        List(3) { String(CharArray(4) { NIGHTMARE_CHARS[random.nextInt(NIGHTMARE_CHARS.length)] }) }.joinToString("-")
}

/**
 * Something to go and find. Two on-device recognizers vote: the object detector (COCO labels, with
 * boxes) and the ML Kit image labeler (400+ whole-scene labels such as "Sink", "Shoe", "Kitchen").
 */
data class HuntTarget(
    val name: String,
    val emoji: String,
    val hint: String,
    /** 0 = probably in the bedroom, 2 = you'll have to walk. */
    val tier: Int,
    /** Object-detector labels that count as this object. */
    val detectorLabels: Set<String> = emptySet(),
    /** Image-labeler labels that count as this object. */
    val sceneLabels: Set<String> = emptySet(),
    /** Image-labeler labels for where it usually is; seeing them makes a detection more believable. */
    val contextLabels: Set<String> = emptySet(),
    /** Detector labels it's often mistaken for (a vessel sink reads as "bowl"). Easy/Normal only, and only in context. */
    val lookAlikes: Set<String> = emptySet(),
)

/** Which set of everyday objects Find the Object asks for. */
enum class HuntPack(val label: String) { AFGHAN_HOME("Afghan home"), GENERAL("General") }

object HuntTargets {
    private val kitchen = setOf("Kitchen", "Countertop")
    private val bathroom = setOf("Bathroom", "Tile")
    private val meal = setOf("Food", "Meal", "Tableware")

    // Found in most homes, Afghan or not.
    private val sink = HuntTarget("Sink", "🚰", "Sink in the bathroom or kitchen. Splash some water while you're there.", 2, setOf("sink"), setOf("Sink"), kitchen + bathroom, setOf("bowl", "toilet"))
    private val fridge = HuntTarget("Refrigerator", "🧊", "Kitchen. Open it, close it, you're awake.", 2, setOf("refrigerator"), contextLabels = kitchen)
    private val toothbrush = HuntTarget("Toothbrush", "🪥", "Bathroom. Use it after.", 2, setOf("toothbrush"), contextLabels = bathroom)
    private val toilet = HuntTarget("Toilet", "🚽", "You were going there anyway.", 2, setOf("toilet"), contextLabels = bathroom, lookAlikes = setOf("sink"))
    private val tv = HuntTarget("TV", "📺", "Living room. Don't turn it on.", 1, setOf("tv"), setOf("Television"))
    private val clock = HuntTarget("Wall clock", "🕰️", "Ironic, isn't it?", 1, setOf("clock"), setOf("Clock"))
    private val plant = HuntTarget("Plant", "🪴", "Yes, the one you forget to water.", 1, setOf("potted plant"), setOf("Flowerpot"))
    private val book = HuntTarget("Book", "📚", "Remember those?", 0, setOf("book"), contextLabels = setOf("Shelf", "Desk"))

    /** Everyday things in Afghan homes that the on-device recognizers can actually see. */
    private val afghanHome = listOf(
        HuntTarget("Piyala or tea glass", "🍵", "Chai time. Any tea cup or glass will do.", 0, setOf("cup", "wine glass"), setOf("Cup", "Saucer"), kitchen + meal),
        HuntTarget("Pardah (curtains)", "🪟", "The curtains. Open them while you're there.", 0, sceneLabels = setOf("Curtain")),
        book.copy(name = "Kitab (a book)", hint = "Any book from the shelf."),
        HuntTarget("Toshak", "🛋️", "The toshak cushions in the sitting room. Don't you dare lie down.", 1, sceneLabels = setOf("Cushion"), lookAlikes = setOf("couch")),
        HuntTarget("Kawch (sofa)", "🛋️", "The sofa in the mehmankhana.", 1, setOf("couch"), setOf("Couch", "Loveseat")),
        HuntTarget("Dastarkhan", "🍽️", "Spread it for breakfast, or find where it's folded.", 1, sceneLabels = setOf("Tablecloth", "Placemat"), contextLabels = meal),
        HuntTarget("Chai thermos", "🫖", "The thermos. Probably in the kitchen, probably empty.", 1, setOf("bottle"), contextLabels = kitchen + meal),
        HuntTarget("Fruit", "🍎", "Seb, anaar, angoor, kela... whatever is in the fruit bowl.", 1, setOf("apple", "orange", "banana"), setOf("Fruit"), kitchen + meal),
        HuntTarget("Chaplak or shoes", "🩴", "By the door, where everyone leaves them.", 1, sceneLabels = setOf("Shoe", "Sneakers")),
        tv,
        clock,
        plant.copy(name = "Gul (a plant)"),
        HuntTarget("Naan", "🫓", "Kitchen. Breakfast won't eat itself.", 2, setOf("sandwich", "pizza"), setOf("Bread"), kitchen + meal),
        HuntTarget("Deg (cooking pot)", "🍲", "Kitchen. The big pot or the pressure cooker.", 2, sceneLabels = setOf("Cookware and bakeware"), contextLabels = kitchen, lookAlikes = setOf("bowl")),
        sink,
        fridge,
        toothbrush,
        toilet,
    )

    private val general = listOf(
        HuntTarget("Cup or mug", "☕", "Kitchen. The coffee is waiting anyway.", 0, setOf("cup", "wine glass"), setOf("Cup"), kitchen + "Tableware"),
        HuntTarget("Bottle", "🧴", "Water bottle, shampoo, anything bottle-shaped.", 0, setOf("bottle"), contextLabels = kitchen + bathroom),
        HuntTarget("Chair", "🪑", "Somewhere that isn't your bed.", 0, setOf("chair"), setOf("Chair")),
        book,
        HuntTarget("Laptop", "💻", "Wherever you abandoned it last night.", 0, setOf("laptop"), setOf("Computer"), setOf("Desk")),
        HuntTarget("Shoes", "👟", "By the door. You'll need them anyway.", 1, sceneLabels = setOf("Shoe", "Sneakers")),
        HuntTarget("Couch", "🛋️", "Living room. Don't you dare lie down.", 1, setOf("couch"), setOf("Couch", "Loveseat")),
        tv,
        plant,
        HuntTarget("Bowl", "🥣", "Kitchen cabinet. Cereal optional.", 1, setOf("bowl"), contextLabels = kitchen + "Tableware"),
        clock.copy(name = "Clock"),
        HuntTarget("Dining table", "🍽️", "Where breakfast allegedly happens.", 1, setOf("dining table"), setOf("Tablecloth", "Placemat"), kitchen),
        sink,
        fridge,
        toothbrush,
        toilet,
        HuntTarget("Microwave", "🍿", "Kitchen counter.", 2, setOf("microwave"), contextLabels = kitchen, lookAlikes = setOf("oven", "tv")),
        HuntTarget("Oven", "🔥", "Kitchen. Look, don't touch.", 2, setOf("oven"), contextLabels = kitchen, lookAlikes = setOf("microwave")),
        HuntTarget("Toaster", "🍞", "Kitchen. Toast is optional.", 2, setOf("toaster"), contextLabels = kitchen, lookAlikes = setOf("microwave")),
    )

    fun pack(pack: HuntPack): List<HuntTarget> = if (pack == HuntPack.AFGHAN_HOME) afghanHome else general

    /** Things a cheater might hold up a photo on. */
    val screens = setOf("tv", "laptop", "cell phone")

    fun poolFor(level: Int, pack: HuntPack): List<HuntTarget> {
        val all = pack(pack)
        return when (level) {
            0 -> all.filter { it.tier <= 1 }
            1 -> all.filter { it.tier >= 1 }
            else -> all.filter { it.tier == 2 }
        }
    }
}

data class MathProblem(val text: String, val answer: Int)

object MathProblems {
    fun next(level: Int, random: Random): MathProblem = when (level) {
        0 -> {
            val a = random.nextInt(10, 50)
            val b = random.nextInt(10, 50)
            MathProblem("$a + $b", a + b)
        }
        1 -> if (random.nextBoolean()) {
            val a = random.nextInt(12, 99)
            val b = random.nextInt(12, 99)
            val c = random.nextInt(12, 99)
            MathProblem("$a + $b + $c", a + b + c)
        } else {
            val a = random.nextInt(11, 20)
            val b = random.nextInt(3, 10)
            MathProblem("$a × $b", a * b)
        }
        2 -> {
            val a = random.nextInt(12, 50)
            val b = random.nextInt(3, 10)
            val c = random.nextInt(10, 100)
            MathProblem("$a × $b + $c", a * b + c)
        }
        else -> {
            val a = random.nextInt(13, 40)
            val b = random.nextInt(11, 20)
            val c = random.nextInt(100, 500)
            val d = random.nextInt(10, 100)
            MathProblem("($a × $b) + $c − $d", a * b + c - d)
        }
    }
}

enum class DanceMove(val label: String, val emoji: String, val hint: String) {
    TILT_LEFT("TILT LEFT", "⬅️", "Rotate the phone sideways to the left"),
    TILT_RIGHT("TILT RIGHT", "➡️", "Rotate the phone sideways to the right"),
    FACE_UP("PALM UP", "🫴", "Lay the phone flat, screen facing the ceiling"),
    SHAKE("SHAKE IT", "🫨", "Shake the phone hard, three times"),
    SPIN("SPIN", "🌀", "Twirl! Turn the phone all the way around"),
}
