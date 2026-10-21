package org.torproject.vpn

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.torproject.vpn.ui.navigation.Destination
import org.torproject.vpn.ui.navigation.NavDisplay
import org.torproject.vpn.ui.theme.AppTheme
import org.torproject.vpn.utils.Json

/**
 * Activity to host compose-related UI components
 */
class ComposeActivity : ComponentActivity() {
    private val appContainer by lazy { (applicationContext as TorApplication).appContainer }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(scrim = Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(scrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b))
        )

        setContent {
            AppTheme {
                NavDisplay(
                    // TODO: Change when everything is in compose and this is the primary activity
                    startDestination = Json.decodeFromString<Destination>(
                        intent.getStringExtra(Constants.INTENT_SCREEN_KEY)!!
                    ),
                    onCloseRequest = ::finish,
                    appContainer = appContainer
                )
            }
        }
    }

    override fun finish() {
        super.finish()
        Intent(this, MainActivity::class.java).also { intent ->
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }
    }
}
