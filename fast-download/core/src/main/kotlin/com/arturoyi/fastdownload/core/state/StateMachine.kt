/**
 * 状态迁移白名单。
 *
 * @file
 */
package com.arturoyi.fastdownload.core.state

import java.util.logging.Level
import java.util.logging.Logger

/**
 * 检查一次状态迁移是否在白名单里。
 *
 * 不合法时先写错误日志，再抛出异常。调用方不能把异常吃掉后继续沿用旧状态以外的写法。
 */
public object StateMachine {
    /**
     * 判断 [from] 到 [to] 是否允许。
     *
     * 相同状态只有排队和等待网络可以再次写入。其余状态没有自环。
     *
     * @param from 当前状态。
     * @param to 目标状态。
     * @return 白名单包含这对状态时为 true。
     */
    public fun isLegal(from: Status, to: Status): Boolean = to in allowed(from)

    /**
     * 要求 [from] 到 [to] 合法。
     *
     * @param from 当前状态。
     * @param to 目标状态。
     * @throws IllegalStateException 这对状态不在白名单里。
     */
    public fun requireTransition(from: Status, to: Status) {
        if (isLegal(from, to)) return
        val message = "非法状态迁移 $from -> $to"
        logger.log(Level.SEVERE, message)
        throw IllegalStateException(message)
    }

    /**
     * 某个状态可以到达的目标。
     *
     * @param from 当前状态。
     * @return 允许的目标状态。终态移除和删除返回空集。
     */
    public fun allowed(from: Status): Set<Status> {
        return when (from) {
            Status.ADDED -> setOf(Status.QUEUED, Status.REMOVED, Status.DELETED)
            Status.QUEUED -> setOf(
                Status.QUEUED,
                Status.WAITING_NETWORK,
                Status.DOWNLOADING,
                Status.PAUSED,
                Status.CANCELLED,
                Status.REMOVED,
                Status.DELETED,
            )
            Status.WAITING_NETWORK -> setOf(
                Status.QUEUED,
                Status.WAITING_NETWORK,
                Status.DOWNLOADING,
                Status.PAUSED,
                Status.CANCELLED,
                Status.REMOVED,
                Status.DELETED,
            )
            Status.DOWNLOADING -> setOf(
                Status.QUEUED,
                Status.WAITING_NETWORK,
                Status.PAUSED,
                Status.COMPLETED,
                Status.FAILED,
                Status.CANCELLED,
                Status.REMOVED,
                Status.DELETED,
            )
            Status.PAUSED -> setOf(
                Status.QUEUED,
                Status.WAITING_NETWORK,
                Status.CANCELLED,
                Status.REMOVED,
                Status.DELETED,
            )
            Status.FAILED -> setOf(
                Status.QUEUED,
                Status.WAITING_NETWORK,
                Status.CANCELLED,
                Status.REMOVED,
                Status.DELETED,
            )
            Status.CANCELLED -> setOf(Status.QUEUED, Status.REMOVED, Status.DELETED)
            Status.COMPLETED -> setOf(Status.REMOVED, Status.DELETED)
            Status.REMOVED, Status.DELETED -> emptySet()
        }
    }

    private val logger: Logger = Logger.getLogger("com.arturoyi.fastdownload.core.state.StateMachine")
}
