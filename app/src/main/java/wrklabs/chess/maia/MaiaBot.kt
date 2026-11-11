package wrklabs.chess.maia

import kotlin.random.Random

/** Picks a move the way a player of the rating would: by the odds Maia gives, so the same position can be answered differently. */
object MaiaBot {
    /**
     * One move from [probs] (legal move to its odds), drawn in proportion to them. Moves below [floor] are left out, which
     * trims the freak moves nobody would play and keeps the ordinary mistakes a player of that rating makes.
     */
    fun sample(probs: Map<String, Float>, random: Random, floor: Float = 0.01f): String? {
        if (probs.isEmpty()) return null
        val kept = probs.filter { it.value >= floor }.ifEmpty { probs }
        var pick = random.nextFloat() * kept.values.sum()
        for ((uci, p) in kept) { pick -= p; if (pick < 0) return uci }
        return kept.keys.last()
    }
}
