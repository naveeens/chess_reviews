package wrklabs.chess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import wrklabs.chess.ui.MainScreen
import wrklabs.chess.ui.theme.ChessTheme
import wrklabs.chess.ui.theme.appColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Graph.init(this)
        setContent {
            val settings by Graph.settings.state.collectAsState()
            // The system bars take the page's colour, with icons that read on it.
            val page = appColors().bg
            val argb = android.graphics.Color.argb((page.alpha * 255).toInt(), (page.red * 255).toInt(), (page.green * 255).toInt(), (page.blue * 255).toInt())
            androidx.compose.runtime.SideEffect {
                val style = SystemBarStyle.dark(argb)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            ChessTheme(settings.accent) {
                MainScreen(settings)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) Graph.shutdown()
    }
}
