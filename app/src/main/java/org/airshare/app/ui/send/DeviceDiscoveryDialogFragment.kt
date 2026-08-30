package org.airshare.app.ui.send

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.airshare.app.R
import org.airshare.app.data.model.DevicePeer
import org.airshare.app.databinding.DialogDeviceDiscoveryBinding
import org.airshare.app.ui.qr.QrScannerActivity
import org.airshare.app.ui.transfer.TransferProgressActivity
import java.io.Serializable

class DeviceDiscoveryDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogDeviceDiscoveryBinding? = null
    private val binding get() = _binding!!

    private val sendViewModel: SendViewModel by activityViewModels()
    private lateinit var peersAdapter: DiscoveredPeersAdapter

    private val qrScanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val qrText = result.data?.getStringExtra(QrScannerActivity.EXTRA_QR_RESULT)
            if (!qrText.isNullOrBlank()) {
                connectByQrPayload(qrText)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogDeviceDiscoveryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        peersAdapter = DiscoveredPeersAdapter { peer ->
            startTransferWithPeer(peer)
        }

        binding.rvDiscoveredPeers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDiscoveredPeers.adapter = peersAdapter

        sendViewModel.startDiscovery()

        // Combine WifiDirect peers & NSD peers
        lifecycleScope.launch {
            combine(
                sendViewModel.wifiDirectManager.discoveredPeers,
                sendViewModel.nsdDiscoveryManager.discoveredPeersFlow
            ) { p2pList, nsdList ->
                val combined = mutableListOf<DevicePeer>()
                combined.addAll(p2pList)
                combined.addAll(nsdList)
                combined.distinctBy { it.id }
            }.collectLatest { list ->
                peersAdapter.submitList(list)
            }
        }

        binding.btnScanQrToPair.setOnClickListener {
            val intent = Intent(requireContext(), QrScannerActivity::class.java)
            qrScanLauncher.launch(intent)
        }

        binding.btnCancelDiscovery.setOnClickListener {
            dismiss()
        }
    }

    private fun connectByQrPayload(qrPayload: String) {
        // QR payload format: "AIRSHARE|ip|port|deviceName|publicKeyBase64"
        val parts = qrPayload.split("|")
        if (parts.size >= 3 && parts[0] == "AIRSHARE") {
            val ip = parts[1]
            val port = parts[2].toIntOrNull() ?: 8080
            val name = if (parts.size >= 4) parts[3] else "Receiver Device"
            val pubKey = if (parts.size >= 5) parts[4] else ""

            val peer = DevicePeer(
                id = "qr_$ip",
                name = name,
                ipAddress = ip,
                port = port,
                publicKeyBase64 = pubKey
            )
            startTransferWithPeer(peer)
        }
    }

    private fun startTransferWithPeer(peer: DevicePeer) {
        val files = sendViewModel.selectedFiles.value.toList()
        val intent = Intent(requireContext(), TransferProgressActivity::class.java).apply {
            putExtra(TransferProgressActivity.EXTRA_IS_SENDER, true)
            putExtra(TransferProgressActivity.EXTRA_TARGET_PEER, peer)
            putExtra(TransferProgressActivity.EXTRA_FILES_LIST, ArrayList(files))
        }
        startActivity(intent)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sendViewModel.stopDiscovery()
        _binding = null
    }

    class DiscoveredPeersAdapter(
        private val onPeerClicked: (DevicePeer) -> Unit
    ) : RecyclerView.Adapter<DiscoveredPeersAdapter.PeerViewHolder>() {

        private var items = listOf<DevicePeer>()

        fun submitList(newItems: List<DevicePeer>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class PeerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val name: TextView = itemView.findViewById(R.id.tvPeerName)
            val address: TextView = itemView.findViewById(R.id.tvPeerAddress)
            val icon: ImageView = itemView.findViewById(R.id.ivPeerIcon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PeerViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device_peer, parent, false)
            return PeerViewHolder(view)
        }

        override fun onBindViewHolder(holder: PeerViewHolder, position: Int) {
            val item = items[position]
            holder.name.text = item.name
            holder.address.text = "${item.ipAddress} • Ready"
            holder.itemView.setOnClickListener { onPeerClicked(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
