/**
 * 通知接收器注册和注销使用同一个实例。
 *
 * @file
 */
package com.arturoyi.fastdownload.notify.notification

import android.content.Intent
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 场景 19：注销后不再收到暂停广播，再次注册仍是原来的接收器。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationReceiverSlotTest {
    /**
     * 注销漏掉的广播不会进回调，第三个广播仍然进同一个实例。
     */
    @Test
    fun sameReceiverUnregistersCleanly() {
        val context = RuntimeEnvironment.getApplication()
        val hits = mutableListOf<String>()
        val slot = NotificationReceiverSlot { id -> hits += id }
        val first = slot.receiver
        slot.register(context)
        context.sendBroadcast(pause("task"))
        shadowOf(Looper.getMainLooper()).idle()
        slot.unregister(context)
        context.sendBroadcast(pause("again"))
        shadowOf(Looper.getMainLooper()).idle()
        slot.register(context)
        assertEquals(first, slot.receiver)
        context.sendBroadcast(pause("third"))
        shadowOf(Looper.getMainLooper()).idle()
        slot.unregister(context)
        assertEquals(listOf("task", "third"), hits)
    }

    private fun pause(id: String): Intent {
        return Intent(NotificationActionReceiver.ACTION_PAUSE)
            .setPackage(RuntimeEnvironment.getApplication().packageName)
            .putExtra(NotificationActionReceiver.EXTRA_ID, id)
    }
}
