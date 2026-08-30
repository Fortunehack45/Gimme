package org.airshare.app.ui.group

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.airshare.app.AirShareApplication
import org.airshare.app.R
import org.airshare.app.data.model.DevicePeer
import org.airshare.app.data.model.TransferFile
import org.airshare.app.databinding.ActivityGroupBinding
import org.airshare.app.engine.crypto.CryptoEngine
import org.airshare.app.engine.network.hotspot.HotspotManager
import org.airshare.app.engine.network.p2p.WifiDirectManager
import org.airshare.app.engine.network.server.AirShareServer
import org.airshare.app.ui.qr.QrCodeUtils
import org.airshare.app.ui.qr.QrScannerActivity
import org.airshare.app.ui.send.SendActivity
import org.airshare.app.ui.transfer.TransferProgressActivity

class GroupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupBinding
    private var server: AirShareServer? = null
    private var wifiDirectManager: WifiDirectManager? = null
    private lateinit var receiversAdapter: GroupReceiversAdapter

    private var groupPin: String = (1000..9999).random().toString()
    private var localIp: String = "127.0.0.1"
    private val port: Int = 8080
    private var selectedBroadcastFiles = mutableListOf<TransferFile>()

    private val qrScanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val qrText = result.data?.getStringExtra(QrScannerActivity.EXTRA_QR_RESULT)
            if (!qrText.isNullOrBlank()) {
                connectToGroupHost(qrText)
            }
        }
    }

    private val selectFilesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val files = result.data?.getSerializableExtra(TransferProgressActivity.EXTRA_FILES_LIST) as? ArrayList<TransferFile>
            if (files != null && files.isNotEmpty()) {
                selectedBroadcastFiles.clear()
                selectedBroadcastFiles.addAll(files)
                server?.setFiles(files)
                binding.btnGroupSelectFiles.text = "📁 ${files.size} files selected for broadcast"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val deviceName = AirShareApplication.instance.settingsRepository.deviceName
        localIp = HotspotManager.getLocalIpAddress()

        binding.btnGroupBack.setOnClickListener { finish() }

        receiversAdapter = GroupReceiversAdapter()
        binding.rvGroupReceivers.layoutManager = LinearLayoutManager(this)
        binding.rvGroupReceivers.adapter = receiversAdapter

        setupRoleToggle()
        startGroupHost(deviceName)

        binding.btnGroupSelectFiles.setOnClickListener {
            val intent = Intent(this, SendActivity::class.java)
            selectFilesLauncher.launch(intent)
        }

        binding.btnStartBroadcast.setOnClickListener {
            if (selectedBroadcastFiles.isEmpty()) {
                Toast.makeText(this, "Please select files to broadcast first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val peers = server?.connectedPeers?.values?.toList() ?: emptyList()
            if (peers.isEmpty()) {
                Toast.makeText(this, "Wait for at least one receiver to join", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, TransferProgressActivity::class.java).apply {
                putExtra(TransferProgressActivity.EXTRA_IS_SENDER, true)
                putExtra(TransferProgressActivity.EXTRA_IS_GROUP, true)
                putExtra(TransferProgressActivity.EXTRA_FILES_LIST, ArrayList(selectedBroadcastFiles))
            }
            startActivity(intent)
        }

        binding.btnJoinScanQr.setOnClickListener {
            startActivity(Intent(this, QrScannerActivity::class.java))
        }

        binding.btnJoinByIp.setOnClickListener {
            val input = binding.etHostIpOrPin.text.toString().trim()
            if (input.isNotBlank()) {
                val hostIp = if (input.contains(".")) input else "192.168.1.1"
                joinGroup(hostIp, 8080, input)
            }
        }
    }

    private fun setupRoleToggle() {
        binding.toggleGroupRole.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btnRoleHost) {
                    binding.layoutHostSection.visibility = View.VISIBLE
                    binding.layoutJoinSection.visibility = View.GONE
                } else {
                    binding.layoutHostSection.visibility = View.GONE
                    binding.layoutJoinSection.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun startGroupHost(deviceName: String) {
        wifiDirectManager = WifiDirectManager(this).apply {
            register()
            createGroup()
        }

        server = AirShareServer(
            context = this,
            hostDeviceName = deviceName,
            isPinRequired = true,
            expectedPin = groupPin,
            port = port
        )

        try {
            server?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val pubKeyStr = CryptoEngine.encodePublicKey(server!!.serverKeyPair.public)
        val qrPayload = "AIRSHARE_GROUP|$localIp|$port|$deviceName|$groupPin|$pubKeyStr"
        val qrBmp = QrCodeUtils.generateQrBitmap(qrPayload, 400, 400)
        binding.ivGroupQrCode.setImageBitmap(qrBmp)
        binding.tvGroupPinPrompt.text = "Group PIN: $groupPin"

        lifecycleScope.launch {
            server?.liveConnectedPeersFlow?.collectLatest { peers ->
                receiversAdapter.submitList(peers)
                binding.tvJoinedDevicesCount.text = "${peers.size} device${if (peers.size == 1) "" else "s"} joined"
            }
        }
    }

    private fun connectToGroupHost(qrPayload: String) {
        val parts = qrPayload.split("|")
        if (parts.size >= 4 && parts[0] == "AIRSHARE_GROUP") {
            val ip = parts[1]
            val p = parts[2].toIntOrNull() ?: 8080
            val pin = if (parts.size >= 5) parts[4] else ""
            joinGroup(ip, p, pin)
        }
    }

    private fun joinGroup(hostIp: String, hostPort: Int, pin: String) {
        val intent = Intent(this, TransferProgressActivity::class.java).apply {
            putExtra(TransferProgressActivity.EXTRA_IS_SENDER, false)
            putExtra(TransferProgressActivity.EXTRA_IS_GROUP, true)
            putExtra(TransferProgressActivity.EXTRA_HOST_IP, hostIp)
            putExtra(TransferProgressActivity.EXTRA_HOST_PORT, hostPort)
            putExtra(TransferProgressActivity.EXTRA_PIN_CODE, pin)
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
        wifiDirectManager?.unregister()
        wifiDirectManager?.removeGroup()
    }

    class GroupReceiversAdapter : RecyclerView.Adapter<GroupReceiversAdapter.ViewHolder>() {
        private var items = listOf<DevicePeer>()

        fun submitList(newList: List<DevicePeer>) {
            items = newList
            notifyDataSetChanged()
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val initials: TextView = itemView.findViewById(R.id.tvAvatarInitials)
            val name: TextView = itemView.findViewById(R.id.tvGroupPeerName)
            val status: TextView = itemView.findViewById(R.id.tvGroupPeerStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_group_peer_avatar, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.name.text = item.name
            holder.initials.text = item.name.firstOrNull()?.uppercase() ?: "D"
            holder.status.text = "${item.ipAddress} • Connected"
        }

        override fun getItemCount(): Int = items.size
    }
}
