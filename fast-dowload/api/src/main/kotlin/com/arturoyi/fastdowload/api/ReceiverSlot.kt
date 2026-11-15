/**
 * 用同一个接收器实例注册和注销。
 *
 * 每次取接收器都 new 一个新对象时，注销对不上已经注册的那个。
 *
 * @file
 */
package com.arturoyi.fastdowload.api

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * 接收暂停广播，并把任务标识交给调用方。
 *
 * @param onPause 收到暂停动作时调用，参数是任务标识。
 */
public class DownloadActionReceiver(
    private val onPause: (String) -> Unit,
) : BroadcastReceiver() {
    /**
     * 处理 [ACTION_PAUSE]。
     *
     * 没有任务标识时忽略。
     *
     * @param context 收到广播的上下文。
     * @param intent 广播内容。
     */
    public override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PAUSE) return
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        onPause(id)
    }

    public companion object {
        /** 暂停一条任务的动作。 */
        public const val ACTION_PAUSE: String = "com.arturoyi.fastdowload.action.PAUSE"

        /** 任务标识在 Intent 里的键。 */
        public const val EXTRA_ID: String = "id"
    }
}

/**
 * 持有唯一的 [DownloadActionReceiver]，注册和注销都用它。
 *
 * @param onPause 暂停广播的处理。
 */
public class ReceiverSlot(
    onPause: (String) -> Unit,
) {
    /** 注册和注销共用的接收器。 */
    public val receiver: DownloadActionReceiver = DownloadActionReceiver(onPause)

    private var registered = false

    /**
     * 注册 [receiver]。已经注册时不再注册第二次。
     *
     * @param context 用来注册的上下文。
     */
    public fun register(context: Context) {
        if (registered) return
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadActionReceiver.ACTION_PAUSE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        registered = true
    }

    /**
     * 注销 [receiver]。没有注册时什么也不做。
     *
     * @param context 当时注册用的上下文。
     */
    public fun unregister(context: Context) {
        if (!registered) return
        context.unregisterReceiver(receiver)
        registered = false
    }
}
