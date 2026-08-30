package org.airshare.app.ui.transfer

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.airshare.app.AirShareApplication
import org.airshare.app.R
import org.airshare.app.data.model.*
import org.airshare.app.databinding.ActivityTransferProgressBinding
import org.airshare.app.engine.network.client.AirShareClient
import org.airshare.app.engine.network.server.AirShareServer
import org.airshare.app.service.TransferForegroundService
import org.airshare.app.ui.theme.ThemeManager
import java.io.File
import java.util.UUID

class TransferProgressActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTransferProgressBinding
    private lateinit var filesAdapter: TransferFilesProgressAdapter

    private var isSender: Boolean = false
    private var isGroup: Boolean = false
    private var targetPeer: DevicePeer? = null
    private var hostIp: String = "127.0.0.1"
    private var hostPort: Int = 8080
    private var pinCode: String = ""

    private var server: AirShareServer? = null
    private var client: AirShareClient? = null

    private var filesList = mutableListOf<TransferFile>()
    private var isPaused = false
    private var isCompleted = false
    private val sessionId = "session_${UUID.randomUUID()}"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransferProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isSender = intent.getBooleanExtra(EXTRA_IS_SENDER, false)
        isGroup = intent.getBooleanExtra(EXTRA_IS_GROUP, false)
        targetPeer = intent.getSerializableExtra(EXTRA_TARGET_PEER) as? DevicePeer
        hostIp = intent.getStringExtra(EXTRA_HOST_IP) ?: targetPeer?.ipAddress ?: "127.0.0.1"
        hostPort = intent.getIntExtra(EXTRA_HOST_PORT, 8080)
        pinCode = intent.getStringExtra(EXTRA_PIN_CODE) ?: ""

        val passedFiles = intent.getSerializableExtra(EXTRA_FILES_LIST) as? ArrayList<TransferFile>
        if (passedFiles != null) {
            filesList.addAll(passedFiles)
        }

        filesAdapter = TransferFilesProgressAdapter(filesList) { file ->
            handleFileClick(file)
        }
        binding.rvTransferFiles.layoutManager = LinearLayoutManager(this)
        binding.rvTransferFiles.adapter = filesAdapter

        val sessionName = if (isSender) "Sending Files" else "Receiving Files"
        binding.tvTransferHeader.text = sessionName
        TransferForegroundService.startService(this, sessionName)

        setupControls()
        applyDynamicTransferTheme()

        if (isSender) {
            startSenderSession()
        } else {
            startReceiverSession()
        }

        lifecycleScope.launch {
            ThemeManager.activePresetFlow.collectLatest {
                applyDynamicTransferTheme()
            }
        }
    }

    private fun handleFileClick(file: TransferFile) {
        val path = file.localFilePath
        if (path == null) {
            Toast.makeText(this, "File is still transferring...", Toast.LENGTH_SHORT).show()
            return
        }

        val diskFile = File(path)
        if (!diskFile.exists()) {
            Toast.makeText(this, "File not yet on disk", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", diskFile)
            if (file.category == MediaCategory.APPS || file.name.endsWith(".apk", ignoreCase = true)) {
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(installIntent)
            } else {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, file.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Open file with"))
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyDynamicTransferTheme() {
        val activeColor = ThemeManager.getActiveColorInt(this)
        binding.tvTotalProgressPercent.setTextColor(activeColor)
        binding.tvEncryptionStatusPill.setTextColor(activeColor)
        binding.indicatorTotalProgress.setIndicatorColor(activeColor)
        binding.btnCancelOrDone.backgroundTintList = ColorStateList.valueOf(activeColor)
        ThemeManager.applySubtlePillBackground(binding.tvEncryptionStatusPill)
        filesAdapter.notifyDataSetChanged()
    }

    private fun setupControls() {
        binding.btnPauseResume.setOnClickListener {
            if (isCompleted) return@setOnClickListener
            isPaused = !isPaused
            if (isPaused) {
                client?.pause()
                binding.btnPauseResume.text = getString(R.string.btn_resume)
                binding.tvTransferSpeed.text = "Paused"
            } else {
                client?.resume()
                binding.btnPauseResume.text = getString(R.string.btn_pause)
            }
        }

        binding.btnCancelOrDone.setOnClickListener {
            if (isCompleted) {
                finish()
            } else {
                client?.cancel()
                TransferForegroundService.stopService(this)
                finish()
            }
        }
    }

    private fun startSenderSession() {
        val deviceName = AirShareApplication.instance.settingsRepository.deviceName
        try {
            server = AirShareServer(
                context = this,
                hostDeviceName = deviceName,
                port = hostPort
            ).apply {
                setFiles(filesList)
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Server notice: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        binding.tvTotalFilesSummary.text = "0 / ${filesList.size} files transferred"

        lifecycleScope.launch {
            try {
                server?.liveConnectedPeersFlow?.collectLatest { peers ->
                    if (peers.isNotEmpty()) {
                        val peer = peers.first()
                        binding.indicatorTotalProgress.progress = peer.progressPercent
                        binding.tvTotalProgressPercent.text = "${peer.progressPercent}%"
                        binding.tvTransferSpeed.text = "%.2f MB/s".format(peer.speedBytesPerSec / (1024.0 * 1024.0))

                        if (peer.progressPercent >= 100) {
                            onTransferComplete()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startReceiverSession() {
        val deviceName = AirShareApplication.instance.settingsRepository.deviceName
        val clientId = "client_${System.currentTimeMillis()}"
        client = AirShareClient(this, clientId, deviceName)

        val targetDir = AirShareApplication.instance.settingsRepository.getDefaultDownloadDirectory()

        lifecycleScope.launch {
            try {
                binding.tvTransferSpeed.text = "Connecting..."
                val handshake = client?.performHandshake(hostIp, hostPort, pinCode)
                if (handshake?.accepted != true) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@TransferProgressActivity, "Connection: ${handshake?.message ?: "Host unreachable"}", Toast.LENGTH_LONG).show()
                        binding.tvTransferSpeed.text = "Failed to connect"
                        binding.btnCancelOrDone.text = "Close"
                    }
                    return@launch
                }

                val manifestFiles = client?.fetchManifest(hostIp, hostPort) ?: emptyList()
                withContext(Dispatchers.Main) {
                    filesList.clear()
                    filesList.addAll(manifestFiles)
                    filesAdapter.notifyDataSetChanged()
                    binding.tvTotalFilesSummary.text = "0 / ${filesList.size} files"
                }

                if (manifestFiles.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        binding.tvTransferSpeed.text = "No files in session"
                        binding.btnCancelOrDone.text = "Close"
                    }
                    return@launch
                }

                val success = client?.downloadFiles(
                    hostIp = hostIp,
                    port = hostPort,
                    files = filesList,
                    targetDirectory = targetDir,
                    onSessionProgress = { speedMBs, etaSec, totalProgress ->
                        runOnUiThread {
                            binding.indicatorTotalProgress.progress = totalProgress
                            binding.tvTotalProgressPercent.text = "$totalProgress%"
                            binding.tvTransferSpeed.text = "%.2f MB/s".format(speedMBs)
                            binding.tvTransferEta.text = if (etaSec > 0) "ETA ~${etaSec}s" else "Almost done"
                            filesAdapter.notifyDataSetChanged()
                            TransferForegroundService.updateProgress(this@TransferProgressActivity, "AirShare Transfer", totalProgress, speedMBs)
                        }
                    },
                    onFileFinished = { transferFile, file ->
                        runOnUiThread {
                            val completedCount = filesList.count { it.status == TransferStatus.COMPLETED }
                            binding.tvTotalFilesSummary.text = "$completedCount / ${filesList.size} files transferred"
                            filesAdapter.notifyDataSetChanged()
                        }
                        lifecycleScope.launch {
                            AirShareApplication.instance.transferRepository.recordTransfer(
                                sessionId = sessionId,
                                file = transferFile.copy(localFilePath = file.absolutePath),
                                isIncoming = true,
                                peerName = targetPeer?.name ?: "Host Peer"
                            )
                        }
                    }
                )

                withContext(Dispatchers.Main) {
                    if (success == true) {
                        onTransferComplete()
                    } else {
                        binding.tvTransferSpeed.text = "Interrupted"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.tvTransferSpeed.text = "Transfer stopped"
                    Toast.makeText(this@TransferProgressActivity, "Transfer error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun onTransferComplete() {
        isCompleted = true
        binding.indicatorTotalProgress.progress = 100
        binding.tvTotalProgressPercent.text = "100%"
        binding.tvTransferSpeed.text = "Completed"
        binding.tvTransferEta.text = "All files transferred safely"
        binding.btnPauseResume.visibility = View.GONE
        binding.btnCancelOrDone.text = getString(R.string.btn_done)
        TransferForegroundService.stopService(this)

        if (isSender) {
            lifecycleScope.launch {
                AirShareApplication.instance.transferRepository.recordBatchTransfers(
                    sessionId = sessionId,
                    files = filesList,
                    isIncoming = false,
                    peerName = targetPeer?.name ?: "Receiver Peer"
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
        client?.cancel()
        TransferForegroundService.stopService(this)
    }

    companion object {
        const val EXTRA_IS_SENDER = "extra_is_sender"
        const val EXTRA_IS_GROUP = "extra_is_group"
        const val EXTRA_TARGET_PEER = "extra_target_peer"
        const val EXTRA_FILES_LIST = "extra_files_list"
        const val EXTRA_HOST_IP = "extra_host_ip"
        const val EXTRA_HOST_PORT = "extra_host_port"
        const val EXTRA_PIN_CODE = "extra_pin_code"
    }

    class TransferFilesProgressAdapter(
        private val files: List<TransferFile>,
        private val onItemClick: (TransferFile) -> Unit
    ) : RecyclerView.Adapter<TransferFilesProgressAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val icon: ImageView = itemView.findViewById(R.id.ivItemFileIcon)
            val name: TextView = itemView.findViewById(R.id.tvProgressFileName)
            val badge: TextView = itemView.findViewById(R.id.tvProgressStatusBadge)
            val progressIndicator: LinearProgressIndicator = itemView.findViewById(R.id.indicatorFileProgress)
            val transferredBytes: TextView = itemView.findViewById(R.id.tvFileTransferredBytes)
            val chunkInfo: TextView = itemView.findViewById(R.id.tvFileChunkInfo)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transfer_progress_file, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            val context = holder.itemView.context
            val activeColor = ThemeManager.getActiveColorInt(context)

            holder.name.text = file.name
            val percent = file.progressPercent

            if (file.status == TransferStatus.COMPLETED) {
                if (file.category == MediaCategory.APPS || file.name.endsWith(".apk", ignoreCase = true)) {
                    holder.badge.text = "INSTALL 📱"
                } else {
                    holder.badge.text = "READY ✓"
                }
            } else {
                holder.badge.text = "$percent%"
            }

            holder.badge.setTextColor(activeColor)
            ThemeManager.applySubtlePillBackground(holder.badge)
            holder.progressIndicator.progress = percent
            holder.progressIndicator.setIndicatorColor(activeColor)

            val transferredStr = TransferFile.formatByteSize(file.transferredBytes)
            val totalStr = file.formattedSize
            holder.transferredBytes.text = "$transferredStr / $totalStr"
            holder.chunkInfo.text = "Status: ${file.status.name}"

            if (file.category == MediaCategory.APPS && file.packageName != null) {
                try {
                    val appIcon = context.packageManager.getApplicationIcon(file.packageName)
                    holder.icon.setImageDrawable(appIcon)
                } catch (e: Exception) {
                    holder.icon.setImageResource(R.drawable.ic_category_apk)
                }
            } else {
                val iconRes = when (file.category) {
                    MediaCategory.PHOTOS -> R.drawable.ic_category_photo
                    MediaCategory.VIDEOS -> R.drawable.ic_category_video
                    MediaCategory.MUSIC -> R.drawable.ic_category_music
                    MediaCategory.APPS -> R.drawable.ic_category_apk
                    MediaCategory.DOCS -> R.drawable.ic_category_doc
                    else -> R.drawable.ic_category_file
                }
                holder.icon.setImageResource(iconRes)
            }

            holder.itemView.setOnClickListener { onItemClick(file) }
        }

        override fun getItemCount(): Int = files.size
    }
}
