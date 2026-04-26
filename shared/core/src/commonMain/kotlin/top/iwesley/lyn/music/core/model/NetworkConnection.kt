package top.iwesley.lyn.music.core.model

enum class NetworkConnectionType {
    WIFI,
    MOBILE,
}

interface NetworkConnectionTypeProvider {
    fun currentNetworkConnectionType(): NetworkConnectionType
}

object MobileNetworkConnectionTypeProvider : NetworkConnectionTypeProvider {
    override fun currentNetworkConnectionType(): NetworkConnectionType = NetworkConnectionType.MOBILE
}

object WifiNetworkConnectionTypeProvider : NetworkConnectionTypeProvider {
    override fun currentNetworkConnectionType(): NetworkConnectionType = NetworkConnectionType.WIFI
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
