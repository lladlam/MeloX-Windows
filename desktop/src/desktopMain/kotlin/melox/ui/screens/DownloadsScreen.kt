package melox.ui.screens

import androidx.compose.runtime.Composable
import melox.ui.navigation.MeloXNavState

@Composable
fun DownloadsScreen(
    navState: MeloXNavState? = null,
) {
    MeloXLibraryDownloadsPage()
}
