package org.airshare.app.ui.receive

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.airshare.app.AirShareApplication
import org.airshare.app.data.model.DevicePeer
import org.airshare.app.databinding.ActivityReceiveBinding
import org.airshare.app.databinding.DialogIncomingRequestBinding
import org.airshare.app.engine.crypto.CryptoEngine
import org.airshare.app.engine.network.discovery.NsdDiscoveryManager
import org.airshare.app.engine.network.hotspot.HotspotManager
import org.airshare.app.engine.network.p2p.WifiDirectManager
import org.airshare.app.engine.network.server.AirShareServer
import org.airshare.app.ui.qr.QrCodeUtils
import org.airshare.app.ui.theme.ThemeManager
import org.airshare.app.ui.transfer.TransferProgressActivity

class ReceiveActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReceiveBinding
    private var server: AirShareServer? = null
    private var nsdManager: NsdDiscoveryManager? = null
    private var wifiDirectManager: WifiDirectManager? = null
    private var hotspotManager: HotspotManager? = null

    private var localIp: String = "127.0.0.1"
    private val port: Int = 8080

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val deviceName = AirShareApplication.instance.settingsRepository.deviceName
        val isPinRequired = AirShareApplication.instance.settingsRepository.isPinConfirmationRequired
        val pin = AirShareApplication.instance.settingsRepository.sessionPinCode

        binding.tvReceiverDeviceName.text = deviceName
        binding.btnReceiveBack.setOnClickListener { finish() }

        wifiDirectManager = WifiDirectManager(this).apply {
            register()
            createGroup()
        }

        hotspotManager = HotspotManager(this)

        startServerAndGenerateQr(deviceName, isPinRequired, pin)

        binding.btnToggleHotspotFallback.setOnClickListener {
            toggleHotspot()
        }

        lifecycleScope.launch {
            ThemeManager.activePresetFlow.collectLatest {
                applyDynamicReceiveTheme()
            }
        }
    }

    private fun applyDynamicReceiveTheme() {
        val activeColor = ThemeManager.getActiveColorInt(this)
        ThemeManager.applySubtlePillBackground(binding.layoutWaitingStatus)
        binding.pbReceiveRadar.indeterminateTintList = ColorStateList.valueOf(activeColor)
    }

    private fun startServerAndGenerateQr(deviceName: String, isPinRequired: Boolean, pin: String) {
        localIp = HotspotManager.getLocalIpAddress()
        binding.tvReceiverIpPort.text = "Listening on $localIp:$port"

        server = AirShareServer(
            context = this,
            hostDeviceName = deviceName,
            isPinRequired = isPinRequired,
            expectedPin = pin,
            port = port
        )

        try {
            server?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        nsdManager = NsdDiscoveryManager(this, deviceName, port).apply {
            registerService()
        }

        val pubKeyStr = CryptoEngine.encodePublicKey(server!!.serverKeyPair.public)
        val qrPayload = "AIRSHARE|$localIp|$port|$deviceName|$pubKeyStr"
        val qrBmp = QrCodeUtils.generateQrBitmap(qrPayload, 512, 512)
        binding.ivReceiverQrCode.setImageBitmap(qrBmp)

        lifecycleScope.launch {
            server?.liveConnectedPeersFlow?.collectLatest { peers ->
                if (peers.isNotEmpty()) {
                    val peer = peers.first()
                    showIncomingRequestPrompt(peer)
                }
            }
        }
    }

    private fun showIncomingRequestPrompt(peer: DevicePeer) {
        runOnUiThread {
            val dialogBinding = DialogIncomingRequestBinding.inflate(layoutInflater)
            dialogBinding.tvIncomingSenderName.text = peer.name
            dialogBinding.tvIncomingTransferStats.text = "Encrypted Peer Transfer"

            val activeColor = ThemeManager.getActiveColorInt(this)
            dialogBinding.btnAcceptTransfer.backgroundTintList = ColorStateList.valueOf(activeColor)

            val dialog = AlertDialog.Builder(this)
                .setView(dialogBinding.root)
                .setCancelable(false)
                .create()

            dialogBinding.btnAcceptTransfer.setOnClickListener {
                dialog.dismiss()
                val intent = Intent(this, TransferProgressActivity::class.java).apply {
                    putExtra(TransferProgressActivity.EXTRA_IS_SENDER, false)
                    putExtra(TransferProgressActivity.EXTRA_TARGET_PEER, peer)
                    putExtra(TransferProgressActivity.EXTRA_HOST_IP, peer.ipAddress.ifBlank { localIp })
                    putExtra(TransferProgressActivity.EXTRA_HOST_PORT, port)
                }
                startActivity(intent)
                finish()
            }

            dialogBinding.btnDeclineTransfer.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
        }
    }

    private fun toggleHotspot() {
        if (hotspotManager?.isHotspotActive?.value == true) {
            hotspotManager?.stopLocalHotspot()
            binding.btnToggleHotspotFallback.text = "📶 Switch to Direct Hotspot Mode"
            Toast.makeText(this, "Hotspot stopped, using standard WiFi", Toast.LENGTH_SHORT).show()
        } else {
            hotspotManager?.startLocalHotspot(onStarted = { ssid, _ ->
                binding.btnToggleHotspotFallback.text = "🛑 Stop Hotspot ($ssid)"
                Toast.makeText(this, "Hotspot created: $ssid", Toast.LENGTH_LONG).show()
            })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
        nsdManager?.unregisterService()
        wifiDirectManager?.unregister()
        wifiDirectManager?.removeGroup()
        hotspotManager?.stopLocalHotspot()
    }
}
