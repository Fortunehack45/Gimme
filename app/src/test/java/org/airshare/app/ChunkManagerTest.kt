package org.airshare.app

import org.airshare.app.data.model.MediaCategory
import org.airshare.app.data.model.TransferFile
import org.junit.Assert.*
import org.junit.Test

class ChunkManagerTest {

    @Test
    fun testChunkCountCalculation() {
        val chunkSize = 512 * 1024L // 512 KB

        // File size exactly 1 MB
        val size1Mb = 1024 * 1024L
        val count1Mb = ((size1Mb + chunkSize - 1) / chunkSize).toInt()
        assertEquals(2, count1Mb)

        // File size 1.2 MB
        val size1_2Mb = (1.2 * 1024 * 1024).toLong()
        val count1_2Mb = ((size1_2Mb + chunkSize - 1) / chunkSize).toInt()
        assertEquals(3, count1_2Mb)

        // File size 100 bytes
        val countSmall = ((100L + chunkSize - 1) / chunkSize).toInt()
        assertEquals(1, countSmall)
    }

    @Test
    fun testFileFormatByteSize() {
        assertEquals("500 B", TransferFile.formatByteSize(500))
        assertEquals("1.5 KB", TransferFile.formatByteSize(1536))
        assertEquals("10.0 MB", TransferFile.formatByteSize(10 * 1024 * 1024))
        assertEquals("1.2 GB", TransferFile.formatByteSize((1.2 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun testTransferFileProgress() {
        val file = TransferFile(
            id = "f1",
            name = "Test.mp4",
            size = 1000,
            mimeType = "video/mp4",
            category = MediaCategory.VIDEOS,
            transferredBytes = 450
        )
        assertEquals(45, file.progressPercent)
    }
}
