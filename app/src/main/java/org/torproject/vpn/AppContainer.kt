package org.torproject.vpn

import android.app.Application
import android.speech.SpeechRecognizer
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.SavedStateHandle
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import org.torproject.vpn.ui.appdetail.model.AppDetailViewModel
import org.torproject.vpn.ui.approuting.data.AppManager
import org.torproject.vpn.ui.approuting.model.AppRoutingViewModel
import org.torproject.vpn.ui.appsearch.model.AppSearchViewModel
import org.torproject.vpn.ui.bridgebot.model.BridgeBotViewModel
import org.torproject.vpn.ui.exitselection.model.ExitSelectionViewModel
import org.torproject.vpn.ui.iconsetting.model.IconSettingViewModel
import org.torproject.vpn.utils.AppLauncher
import org.torproject.vpn.utils.PreferenceHelper
import org.torproject.vpn.utils.SetLauncherIcon
import org.torproject.vpn.vpn.GetBridgeLines
import org.torproject.vpn.vpn.VpnServiceCommand

class AppContainer(
    val app: Application
) {

    var backgroundContext: CoroutineContext = Dispatchers.IO

    val snackBarHostState by lazy { SnackbarHostState() }

    val getBridgeLines by lazy { GetBridgeLines() }

    val appLauncher by lazy {
        AppLauncher(
            context = app,
            snackBarHostState = snackBarHostState,
            backgroundContext = backgroundContext
        )
    }

    val appManager by lazy { AppManager(app) }

    val preferenceHelper by lazy { PreferenceHelper(app) }

    val setLauncherIcon by lazy {
        SetLauncherIcon(
            application = app,
            preferenceHelper = preferenceHelper
        )
    }

    fun appDetailViewModel(packageName: String) = AppDetailViewModel(
        appManager = appManager,
        packageName = packageName,
        appLauncher = appLauncher,
        startVpn = { VpnServiceCommand.startVpn(app, onlyIfActive = true) }
    )

    fun appRoutingViewModel() = AppRoutingViewModel(
        appManager = appManager,
        startVpn = { VpnServiceCommand.startVpn(app, onlyIfActive = true) }
    )

    fun exitSelectionViewModel() = ExitSelectionViewModel(preferenceHelper)

    fun iconSettingViewModel() = IconSettingViewModel(
        preferenceHelper = preferenceHelper,
        setLauncherIcon = setLauncherIcon::invoke
    )

    fun appSearchViewModel(savedStateHandle: SavedStateHandle) = AppSearchViewModel(
        appManager = appManager,
        preferenceHelper = preferenceHelper,
        startVpn = { VpnServiceCommand.startVpn(app, onlyIfActive = true) },
        isVoiceSearchAvailable = SpeechRecognizer.isRecognitionAvailable(app),
        savedStateHandle = savedStateHandle
    )

    fun bridgeBotViewModel() = BridgeBotViewModel(
        preferenceHelper = preferenceHelper,
        getBridgeLines = getBridgeLines
    )
}
