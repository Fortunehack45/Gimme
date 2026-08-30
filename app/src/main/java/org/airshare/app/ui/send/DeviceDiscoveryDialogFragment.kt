package org.airshare.app.ui.send

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
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
import org.airshare.app.ui.theme.ThemeManager
import org.airshare.app.ui.transfer.TransferProgressActivity

class DeviceDiscoveryDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogDeviceDiscoveryBinding? = null
    private val binding get() = _binding!!

    private val sendViewModel: SendViewModel by activityViewModels()
    private lateinit var adapter: DiscoveredDevicesAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogDeviceDiscoveryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DiscoveredDevicesAdapter { peer ->
            connectToPeerAndSend(peer)
        }

        binding.rvDiscoveredDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDiscoveredDevices.adapter = adapter

        sendViewModel.startDiscovery()

        binding.btnScanQrAction.setOnClickListener {
            dismiss()
            val intent = Intent(requireContext(), QrScannerActivity::class.java)
            startActivity(intent)
        }

        binding.btnCancelDiscovery.setOnClickListener {
            dismiss()
        }

        // Combine WiFi Direct peers and NSD peers
        lifecycleScope.launch {
            combine(
                sendViewModel.wifiDirectManager.discoveredPeers,
                sendViewModel.nsdDiscoveryManager.discoveredPeersFlow
            ) { wifiPeers: List<DevicePeer>, nsdPeers: List<DevicePeer> ->
                val combined = mutableListOf<DevicePeer>()
                combined.addAll(wifiPeers)
                for (nsd in nsdPeers) {
                    if (combined.none { it.name == nsd.name || (it.ipAddress == nsd.ipAddress && it.ipAddress.isNotBlank()) }) {
                        combined.add(nsd)
                    }
                }
                combined
            }.collectLatest { list: List<DevicePeer> ->
                adapter.submitList(list)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            ThemeManager.activePresetFlow.collectLatest {
                applyDynamicDiscoveryTheme()
            }
        }
    }

    private fun applyDynamicDiscoveryTheme() {
        if (_binding == null) return
        val activeColor = ThemeManager.getActiveColorInt(requireContext())
        binding.pbDiscoveryRadar.indeterminateTintList = ColorStateList.valueOf(activeColor)
    }

    private fun connectToPeerAndSend(peer: DevicePeer) {
        val selected = sendViewModel.selectedFiles.value.toList()
        dismiss()

        val intent = Intent(requireContext(), TransferProgressActivity::class.java).apply {
            putExtra(TransferProgressActivity.EXTRA_IS_SENDER, true)
            putExtra(TransferProgressActivity.EXTRA_TARGET_PEER, peer)
            putExtra(TransferProgressActivity.EXTRA_FILES_LIST, ArrayList(selected))
            putExtra(TransferProgressActivity.EXTRA_HOST_IP, peer.ipAddress.ifBlank { "127.0.0.1" })
            putExtra(TransferProgressActivity.EXTRA_HOST_PORT, peer.port)
        }
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sendViewModel.stopDiscovery()
        _binding = null
    }

    class DiscoveredDevicesAdapter(
        private val onDeviceSelected: (DevicePeer) -> Unit
    ) : RecyclerView.Adapter<DiscoveredDevicesAdapter.DeviceViewHolder>() {

        private var list = listOf<DevicePeer>()

        fun submitList(newList: List<DevicePeer>) {
            list = newList
            notifyDataSetChanged()
        }

        inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val icon: ImageView = itemView.findViewById(R.id.ivPeerIcon)
            val name: TextView = itemView.findViewById(R.id.tvPeerName)
            val ip: TextView = itemView.findViewById(R.id.tvPeerAddress)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device_peer, parent, false)
            return DeviceViewHolder(view)
        }

        override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
            val peer = list[position]
            holder.name.text = peer.name
            holder.ip.text = if (peer.ipAddress.isNotBlank()) "${peer.ipAddress} • Ready" else "Wi-Fi Direct Peer • Ready"

            holder.itemView.setOnClickListener { onDeviceSelected(peer) }
        }

        override fun getItemCount(): Int = list.size
    }
}
