package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetworkConnectionType {
    WIFI,
    MOBILE,
}

data class NetworkConnectionState(
    val type: NetworkConnectionType,
    val version: Long = 0L,
    /** False while the device has no usable network, e.g. during a Wi-Fi drop or a handover gap. */
    val isConnected: Boolean = true,
)

interface NetworkConnectionTypeProvider {
    val networkConnectionState: StateFlow<NetworkConnectionState>

    fun currentNetworkConnectionType(): NetworkConnectionType = networkConnectionState.value.type

    fun currentNetworkVersion(): Long = networkConnectionState.value.version
}

object MobileNetworkConnectionTypeProvider : NetworkConnectionTypeProvider {
    private val state = MutableStateFlow(NetworkConnectionState(NetworkConnectionType.MOBILE))
    override val networkConnectionState: StateFlow<NetworkConnectionState> = state.asStateFlow()
}

object WifiNetworkConnectionTypeProvider : NetworkConnectionTypeProvider {
    private val state = MutableStateFlow(NetworkConnectionState(NetworkConnectionType.WIFI))
    override val networkConnectionState: StateFlow<NetworkConnectionState> = state.asStateFlow()
}

fun resolveNavidromeAudioQualityForCurrentNetwork(
    preferencesStore: NavidromeAudioQualityPreferencesStore,
    networkConnectionTypeProvider: NetworkConnectionTypeProvider,
): NavidromeAudioQuality {
    return when (networkConnectionTypeProvider.currentNetworkConnectionType()) {
        NetworkConnectionType.WIFI -> preferencesStore.navidromeWifiAudioQuality.value
        NetworkConnectionType.MOBILE -> preferencesStore.navidromeMobileAudioQuality.value
    }
}
