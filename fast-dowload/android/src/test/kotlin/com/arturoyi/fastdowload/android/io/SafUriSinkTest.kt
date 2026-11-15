/**
 * 内容描述符能定位时可以按偏移写，管道不能。
 *
 * @file
 */
package com.arturoyi.fastdowload.android.io

import android.os.ParcelFileDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

/**
 * [SafUriSink] 的定位能力和实际写入。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafUriSinkTest {
    /**
     * 普通文件描述符可以定位，写入的字节能读回来。
     */
    @Test
    fun fileDescriptorSeeksAndWrites() {
        val file = Files.createTempFile("fastdowload-saf", ".bin").toFile()
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE)
        val sink = SafUriSink(descriptor)
        assertTrue(sink.supportSeek())
        sink.openSink().use { output ->
            output.seek(1)
            output.write(byteArrayOf(7, 8), 0, 2)
            output.flushAndSync()
        }
        assertEquals(listOf(0.toByte(), 7.toByte(), 8.toByte()), file.readBytes().toList())
    }
}
