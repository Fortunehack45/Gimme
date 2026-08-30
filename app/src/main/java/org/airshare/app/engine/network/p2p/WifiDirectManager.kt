package org.airshare.app.engine.network.p2p

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.*
import android.os.Build
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.airshare.app.data.model.DevicePeer
import org.airshare.app.data.model.PeerConnectionState

class WifiDirectManager(private val context: Context) {

    private val p2pManager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: WifiDirectReceiver? = null

    private val _discoveredPeers = MutableStateFlow<List<DevicePeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DevicePeer>> = _discoveredPeers.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()

    private val _isP2pEnabled = MutableStateFlow(false)
    val isP2pEnabled: StateFlow<Boolean> = _isP2pEnabled.asStateFlow()

    init {
        p2pManager?.let { manager ->
            channel = manager.initialize(context, Looper.getMainLooper()) {
                // Channel disconnected
            }
        }
    }

    fun register() {
        if (receiver != null) return
        receiver = WifiDirectReceiver()
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        context.registerReceiver(receiver, intentFilter)
    }

    fun unregister() {
        receiver?.let {
            runCatching { context.unregisterReceiver(it) }
            receiver = null
        }
    }

    @SuppressLint("MissingPermission")
    fun discoverPeers(onSuccess: (() -> Unit)? = null, onFailure: ((Int) -> Unit)? = null) {
        val ch = channel ?: return
        p2pManager?.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onSuccess?.invoke()
            }

            override fun onFailure(reason: Int) {
                onFailure?.invoke(reason)
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun createGroup(onSuccess: (() -> Unit)? = null, onFailure: ((Int) -> Unit)? = null) {
        val ch = channel ?: return
        p2pManager?.createGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onSuccess?.invoke()
            }

            override fun onFailure(reason: Int) {
                onFailure?.invoke(reason)
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun connectToPeer(deviceAddress: String, onSuccess: (() -> Unit)? = null, onFailure: ((Int) -> Unit)? = null) {
        val ch = channel ?: return
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
        }
        p2pManager?.connect(ch, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onSuccess?.invoke()
            }

            override fun onFailure(reason: Int) {
                onFailure?.invoke(reason)
            }
        })
    }

    fun removeGroup(onComplete: (() -> Unit)? = null) {
        val ch = channel ?: return
        p2pManager?.removeGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _connectionInfo.value = null
                onComplete?.invoke()
            }

            override fun onFailure(reason: Int) {
                onComplete?.invoke()
            }
        })
    }

    inner class WifiDirectReceiver : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    _isP2pEnabled.value = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    val ch = channel ?: return
                    p2pManager?.requestPeers(ch) { peersList ->
                        val mapped = peersList.deviceList.map { device ->
                            DevicePeer(
                                id = device.deviceAddress,
                                name = device.deviceName.ifBlank { "Unknown Device" },
                                ipAddress = device.deviceAddress,
                                state = PeerConnectionState.DISCOVERED
                            )
                        }
                        _discoveredPeers.value = mapped
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        val ch = channel ?: return
                        p2pManager?.requestConnectionInfo(ch) { info ->
                            _connectionInfo.value = info
                        }
                    } else {
                        _connectionInfo.value = null
                    }
                }
            }
        }
    }
}
