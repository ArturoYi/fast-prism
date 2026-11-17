/**
 * 暂停广播的注册和注销使用同一个接收器。
 *
 * @file
 */
package com.arturoyi.fastdownload.api

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

/**
 * [ReceiverSlot] 不会因为新的接收器对象导致注销失败。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReceiverSlotTest {
    /**
     * 注销后不再收到广播，再次注册仍然是原来的实例。
     */
    @Test
    fun sameReceiverUnregistersCleanly() {
        val context = RuntimeEnvironment.getApplication()
        val hits = mutableListOf<String>()
        val slot = ReceiverSlot { id -> hits += id }
        val first = slot.receiver
        slot.register(context)
        context.sendBroadcast(
            Intent(DownloadActionReceiver.ACTION_PAUSE).putExtra(DownloadActionReceiver.EXTRA_ID, "task"),
        )
        shadowOf(Looper.getMainLooper()).idle()
        slot.unregister(context)
        context.sendBroadcast(
            Intent(DownloadActionReceiver.ACTION_PAUSE).putExtra(DownloadActionReceiver.EXTRA_ID, "again"),
        )
        shadowOf(Looper.getMainLooper()).idle()
        slot.register(context)
        assertEquals(first, slot.receiver)
        context.sendBroadcast(
            Intent(DownloadActionReceiver.ACTION_PAUSE).putExtra(DownloadActionReceiver.EXTRA_ID, "third"),
        )
        shadowOf(Looper.getMainLooper()).idle()
        slot.unregister(context)
        assertEquals(listOf("task", "third"), hits)
    }
}
