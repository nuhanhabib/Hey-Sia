package com.heysia.app.brain

/** Forgiving name matching, because offline speech recognition often splits or misspells names. */
object Fuzzy {

    /** "You Tube!" -> "youtube" */
    fun compact(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    /** How well [query] matches [candidate], from 0.0 (not at all) to 1.0 (exact). */
    fun score(query: String, candidate: String): Double {
        val q = compact(query)
        val c = compact(candidate)
        if (q.isEmpty() || c.isEmpty()) return 0.0
        if (q == c) return 1.0
        val words = candidate.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        if (words.any { it == q }) return 0.92               // "rafi" matches "Rafi Ahmed"
        if (q.length >= 3 && c.startsWith(q)) return 0.9    // "insta" matches "Instagram"
        if (c.length >= 4 && q.startsWith(c)) return 0.85   // "whatsapp messenger" matches "WhatsApp"
        val best = (words + c).maxOf { similarity(q, it) }
        return best * 0.9
    }

    /** The best match above [threshold], with its score. */
    fun <T> best(query: String, items: List<T>, threshold: Double = 0.7, names: (T) -> List<String>): Pair<T, Double>? =
        ranked(query, items, names).firstOrNull()?.takeIf { it.second >= threshold }

    fun <T> ranked(query: String, items: List<T>, names: (T) -> List<String>): List<Pair<T, Double>> =
        items.map { item -> item to names(item).maxOf { score(query, it) } }.sortedByDescending { it.second }

    private fun similarity(a: String, b: String): Double {
        val max = maxOf(a.length, b.length)
        return if (max == 0) 1.0 else 1.0 - levenshtein(a, b).toDouble() / max
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }
}
