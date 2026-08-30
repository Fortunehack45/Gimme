package org.airshare.app.engine.network.hotspot

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

class HotspotManager(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    private val _hotspotSsid = MutableStateFlow<String?>(null)
    val hotspotSsid: StateFlow<String?> = _hotspotSsid.asStateFlow()

    private val _hotspotPassword = MutableStateFlow<String?>(null)
    val hotspotPassword: StateFlow<String?> = _hotspotPassword.asStateFlow()

    private val _isHotspotActive = MutableStateFlow(false)
    val isHotspotActive: StateFlow<Boolean> = _isHotspotActive.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startLocalHotspot(onStarted: ((ssid: String, pass: String) -> Unit)? = null, onFailure: ((Int) -> Unit)? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                wifiManager?.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                        super.onStarted(reservation)
                        this@HotspotManager.reservation = reservation
                        val config = reservation.wifiConfiguration
                        val ssid = config?.SSID ?: "AirShare-Direct"
                        val password = config?.preSharedKey ?: ""
                        _hotspotSsid.value = ssid
                        _hotspotPassword.value = password
                        _isHotspotActive.value = true
                        onStarted?.invoke(ssid, password)
                    }

                    override fun onStopped() {
                        super.onStopped()
                        _isHotspotActive.value = false
                        _hotspotSsid.value = null
                        _hotspotPassword.value = null
                    }

                    override fun onFailed(reason: Int) {
                        super.onFailed(reason)
                        _isHotspotActive.value = false
                        onFailure?.invoke(reason)
                    }
                }, Handler(Looper.getMainLooper()))
            } catch (e: Exception) {
                e.printStackTrace()
                _isHotspotActive.value = false
                onFailure?.invoke(-1)
            }
        }
    }

    fun stopLocalHotspot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                reservation?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            reservation = null
            _isHotspotActive.value = false
            _hotspotSsid.value = null
            _hotspotPassword.value = null
        }
    }

    companion object {
        fun getLocalIpAddress(): String {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
                val interfaceList = interfaces.toList()

                // Sort: prioritize wlan, p2p, ap interfaces first
                val sorted = interfaceList.sortedByDescending { iface ->
                    when {
                        iface.name.startsWith("wlan", ignoreCase = true) -> 4
                        iface.name.startsWith("p2p", ignoreCase = true) -> 3
                        iface.name.startsWith("ap", ignoreCase = true) -> 2
                        iface.name.startsWith("eth", ignoreCase = true) -> 1
                        else -> 0
                    }
                }

                for (networkInterface in sorted) {
                    if (!networkInterface.isUp || networkInterface.isLoopback) continue
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val inetAddress = addresses.nextElement()
                        if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                            val hostAddress = inetAddress.hostAddress
                            if (hostAddress != null && hostAddress.isNotBlank()) {
                                return hostAddress
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return "127.0.0.1"
        }
    }
}
