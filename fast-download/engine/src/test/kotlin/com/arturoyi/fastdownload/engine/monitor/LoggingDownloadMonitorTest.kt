/**
 * 失败日志里要能看到阶段和错误码。
 *
 * @file
 */
package com.arturoyi.fastdownload.engine.monitor

import com.arturoyi.fastdownload.core.error.ErrorCode
import com.arturoyi.fastdownload.core.monitor.Stage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * [LoggingDownloadMonitor] 的日志和计数。
 */
class LoggingDownloadMonitorTest {
    /**
     * 一次校验失败的终态日志同时包含阶段名和错误码。
     */
    @Test
    fun terminalLogNamesStageAndCode() {
        val lines = mutableListOf<String>()
        val logger = Logger.getLogger("monitor-test-" + System.nanoTime())
        logger.level = Level.ALL
        logger.useParentHandlers = false
        logger.addHandler(object : Handler() {
            override fun publish(record: LogRecord) {
                lines += record.message
            }

            override fun flush() = Unit

            override fun close() = Unit
        })
        val monitor = LoggingDownloadMonitor(debug = false, log = logger)
        monitor.onStage("task", Stage.VERIFY, 4)
        monitor.onTerminal("task", success = false, code = ErrorCode.INVALID_CONTENT_HASH)
        val text = lines.joinToString("\n")
        assertTrue(text.contains("stage=TERMINAL"))
        assertTrue(text.contains("INVALID_CONTENT_HASH"))
        assertEquals(1, monitor.snapshot().failedCount)
        assertEquals(1, monitor.snapshot().errorCounts[ErrorCode.INVALID_CONTENT_HASH])
    }
}
