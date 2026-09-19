package melox

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import melox.ui.MeloXApp

fun main() {
    application {
        Window(
            title = "MeloX Desktop",
            onCloseRequest = ::exitApplication,
        ) {
            MeloXApp()
        }
    }
}
