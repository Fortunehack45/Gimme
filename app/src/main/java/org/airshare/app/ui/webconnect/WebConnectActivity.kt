package org.airshare.app.ui.webconnect

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.airshare.app.AirShareApplication
import org.airshare.app.data.model.MediaCategory
import org.airshare.app.data.model.TransferFile
import org.airshare.app.databinding.ActivityWebConnectBinding
import org.airshare.app.engine.network.hotspot.HotspotManager
import org.airshare.app.engine.network.server.AirShareServer
import org.airshare.app.ui.qr.QrCodeUtils
import org.airshare.app.ui.send.SendActivity
import org.airshare.app.ui.transfer.TransferProgressActivity

class WebConnectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebConnectBinding
    private var server: AirShareServer? = null
    private var localIp: String = "127.0.0.1"
    private val port: Int = 8080

    private val selectFilesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val files = result.data?.getSerializableExtra(TransferProgressActivity.EXTRA_FILES_LIST) as? ArrayList<TransferFile>
            if (files != null && files.isNotEmpty()) {
                server?.setFiles(files)
                binding.btnSelectFilesForWeb.text = "📁 ${files.size} files shared with browser"
                Toast.makeText(this, "${files.size} files ready for browser download", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebConnectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val deviceName = AirShareApplication.instance.settingsRepository.deviceName
        localIp = HotspotManager.getLocalIpAddress()
        val webUrl = "http://$localIp:$port"

        binding.btnWebBack.setOnClickListener { finish() }
        binding.tvWebUrl.text = webUrl

        server = AirShareServer(
            context = this,
            hostDeviceName = deviceName,
            port = port
        ).apply {
            onUploadCompleted = { file, name ->
                runOnUiThread {
                    Toast.makeText(this@WebConnectActivity, "Received '$name' from browser", Toast.LENGTH_SHORT).show()
                    lifecycleScope.launch {
                        AirShareApplication.instance.transferRepository.recordTransfer(
                            sessionId = "web_upload_${System.currentTimeMillis()}",
                            file = TransferFile(
                                id = "upload_${System.currentTimeMillis()}",
                                name = name,
                                size = file.length(),
                                mimeType = "application/octet-stream",
                                category = MediaCategory.FILES,
                                localFilePath = file.absolutePath
                            ),
                            isIncoming = true,
                            peerName = "Browser Web Client"
                        )
                    }
                }
            }
        }

        try {
            server?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val qrBmp = QrCodeUtils.generateQrBitmap(webUrl, 450, 450)
        binding.ivWebQrCode.setImageBitmap(qrBmp)

        binding.btnCopyUrl.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("AirShare Web URL", webUrl)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        binding.btnSelectFilesForWeb.setOnClickListener {
            val intent = Intent(this, SendActivity::class.java)
            selectFilesLauncher.launch(intent)
        }

        lifecycleScope.launch {
            server?.liveConnectedPeersFlow?.collectLatest { peers ->
                binding.tvWebClientCount.text = "Connected browsers: ${peers.size}"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
    }
}
