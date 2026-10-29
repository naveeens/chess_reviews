package wrklabs.chess.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import wrklabs.chess.Graph
import wrklabs.chess.R

/**
 * The move sounds: Lichess's Standard set (see SOUNDS.md), loaded once into a SoundPool so a sound starts the
 * instant a piece lands. Whether they play at all is the "Move sounds" setting.
 */
object SoundPlayer {
    private var pool: SoundPool? = null
    private val ids = mutableMapOf<Int, Int>()

    fun init(context: Context) {
        if (pool != null) return
        val p = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .build()
        for (res in listOf(R.raw.snd_move, R.raw.snd_capture, R.raw.snd_dong, R.raw.snd_confirmation, R.raw.snd_error)) {
            ids[res] = p.load(context.applicationContext, res, 1)
        }
        pool = p
    }

    private fun play(res: Int) {
        if (!Graph.settings.value.sound) return
        val id = ids[res] ?: return
        pool?.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun playMove() = play(R.raw.snd_move)
    fun playCapture() = play(R.raw.snd_capture)
    /** Lichess has no castling sound: it is a move. */
    fun playCastle() = play(R.raw.snd_move)
    /** Nor a sound for check: the move is enough, and the board shows it. */
    fun playCheck() = play(R.raw.snd_move)
    /** The bell, for the end of a game. */
    fun playGameEnd() = play(R.raw.snd_dong)
    fun playSolved() = play(R.raw.snd_confirmation)
    fun playFailed() = play(R.raw.snd_error)
}
