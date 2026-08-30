package org.airshare.app.engine.network.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.airshare.app.data.model.DevicePeer
import org.airshare.app.data.model.PeerConnectionState
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class NsdDiscoveryManager(private val context: Context, private val myDeviceName: String, private val myPort: Int = 8080) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val SERVICE_TYPE = "_airshare._tcp."

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val discoveredMap = ConcurrentHashMap<String, DevicePeer>()
    private val _discoveredPeersFlow = MutableStateFlow<List<DevicePeer>>(emptyList())
    val discoveredPeersFlow: StateFlow<List<DevicePeer>> = _discoveredPeersFlow.asStateFlow()

    private var beaconJob: Job? = null
    private var udpListenJob: Job? = null

    fun registerService() {
        unregisterService()
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = myDeviceName
            serviceType = SERVICE_TYPE
            port = myPort
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }

        try {
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        startUdpBeacon()
    }

    fun startDiscovery() {
        stopDiscovery()
        discoveredMap.clear()
        _discoveredPeersFlow.value = emptyList()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager?.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.contains("airshare")) {
                    resolveService(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                discoveredMap.remove(serviceInfo.serviceName)
                _discoveredPeersFlow.value = discoveredMap.values.toList()
            }
        }

        try {
            nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        startUdpListener()
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

            override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                val host = resolvedInfo.host?.hostAddress ?: return
                val peer = DevicePeer(
                    id = resolvedInfo.serviceName,
                    name = resolvedInfo.serviceName,
                    ipAddress = host,
                    port = resolvedInfo.port,
                    state = PeerConnectionState.DISCOVERED
                )
                discoveredMap[peer.id] = peer
                _discoveredPeersFlow.value = discoveredMap.values.toList()
            }
        })
    }

    fun unregisterService() {
        stopUdpBeacon()
        registrationListener?.let {
            runCatching { nsdManager?.unregisterService(it) }
            registrationListener = null
        }
    }

    fun stopDiscovery() {
        stopUdpListener()
        discoveryListener?.let {
            runCatching { nsdManager?.stopServiceDiscovery(it) }
            discoveryListener = null
        }
    }

    // Supplementary UDP broadcast beacon for networks where multicast/mDNS is filtered
    private fun startUdpBeacon() {
        beaconJob?.cancel()
        beaconJob = CoroutineScope(Dispatchers.IO).launch {
            val socket = DatagramSocket()
            socket.broadcast = true
            val msg = "AIRSHARE_BEACON|$myDeviceName|$myPort".toByteArray()
            val broadcastAddr = InetAddress.getByName("255.255.255.255")
            val packet = DatagramPacket(msg, msg.size, broadcastAddr, 8888)

            while (isActive) {
                try {
                    socket.send(packet)
                } catch (e: Exception) {
                    // ignore
                }
                delay(2000)
            }
            socket.close()
        }
    }

    private fun stopUdpBeacon() {
        beaconJob?.cancel()
        beaconJob = null
    }

    private fun startUdpListener() {
        udpListenJob?.cancel()
        udpListenJob = CoroutineScope(Dispatchers.IO).launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(8888)
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive) {
                    socket.receive(packet)
                    val text = String(packet.data, 0, packet.length)
                    val parts = text.split("|")
                    if (parts.size >= 3 && parts[0] == "AIRSHARE_BEACON") {
                        val senderName = parts[1]
                        val senderPort = parts[2].toIntOrNull() ?: 8080
                        val senderIp = packet.address.hostAddress ?: ""
                        if (senderName != myDeviceName && senderIp.isNotBlank()) {
                            val peer = DevicePeer(
                                id = "udp_$senderIp",
                                name = senderName,
                                ipAddress = senderIp,
                                port = senderPort,
                                state = PeerConnectionState.DISCOVERED
                            )
                            discoveredMap[peer.id] = peer
                            _discoveredPeersFlow.value = discoveredMap.values.toList()
                        }
                    }
                }
            } catch (e: Exception) {
                // ignore
            } finally {
                socket?.close()
            }
        }
    }

    private fun stopUdpListener() {
        udpListenJob?.cancel()
        udpListenJob = null
    }
}
