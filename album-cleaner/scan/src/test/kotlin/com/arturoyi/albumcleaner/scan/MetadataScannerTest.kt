/**
 * 元数据扫描的分页、断点、取消和让出。
 *
 * @file
 */
package com.arturoyi.albumcleaner.scan

import android.os.CancellationSignal
import android.os.OperationCanceledException
import com.arturoyi.albumcleaner.storage.AlbumIndex
import com.arturoyi.albumcleaner.storage.FeatureRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * [MetadataScanner] 用真实索引和假的媒体库。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MetadataScannerTest {
    /**
     * 五条记录按两页写完。进度按批发出，空库直接结束。
     */
    @Test
    fun pagesMetadataAndEmitsEachBatch() = runBlocking {
        val rows = (1L..5L).map { id -> media(id) }
        AlbumIndex.open(context(), "pages.db").use { index ->
            val catalog = ListCatalog(rows)
            val seen = mutableListOf<MetadataProgress>()
            scanner(catalog, index).progress(2, CancellationSignal()).collect { seen += it }
            assertEquals(listOf(1L, 2L, 3L, 4L, 5L), index.assetsAfter(-1L, 10).map { it.assetId })
            assertEquals(listOf(-1L, 2L, 4L, 5L), catalog.cursors)
            assertEquals(
                listOf(0, 2, 4, 5, 5),
                seen.map { it.scanned },
            )
            assertEquals(MetadataPhase.DONE, seen.last().phase)
            assertEquals(SESSION_DONE, index.session(METADATA_SESSION_ID)?.status)
            assertEquals(5L, index.session(METADATA_SESSION_ID)?.cursorId)
        }
    }

    /**
     * 没有照片时只报告开始和结束，不写断点游标。
     */
    @Test
    fun emptyLibraryFinishesWithoutRows() = runBlocking {
        AlbumIndex.open(context(), "empty.db").use { index ->
            val seen = mutableListOf<MetadataPhase>()
            scanner(ListCatalog(emptyList()), index).progress(10, CancellationSignal()).collect { seen += it.phase }
            assertEquals(listOf(MetadataPhase.QUERY, MetadataPhase.DONE), seen)
            assertEquals(SESSION_DONE, index.session(METADATA_SESSION_ID)?.status)
            assertNull(index.session(METADATA_SESSION_ID)?.cursorId)
            assertTrue(index.assetsAfter(-1L, 10).isEmpty())
        }
    }

    /**
     * 页大小不是正数时不写索引。
     */
    @Test
    fun pageSizeMustBePositive() {
        AlbumIndex.open(context(), "size.db").use { index ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    scanner(ListCatalog(listOf(media(1L))), index).progress(0, CancellationSignal()).collect { }
                }
            }
            assertNull(index.session(METADATA_SESSION_ID))
        }
    }

    /**
     * 取消发生在下一页查询里。已经写入的行保留，下一页不再入库。
     */
    @Test
    fun cancelStopsBeforeTheNextPage() = runBlocking {
        val rows = (1L..6L).map { id -> media(id) }
        AlbumIndex.open(context(), "cancel.db").use { index ->
            val catalog = GatedCatalog(rows)
            val signal = CancellationSignal()
            val outcome = kotlinx.coroutines.CompletableDeferred<Throwable?>()
            val job = launch(Dispatchers.Default) {
                try {
                    scanner(catalog, index).progress(2, signal).collect { }
                    outcome.complete(null)
                } catch (error: Throwable) {
                    outcome.complete(error)
                }
            }
            assertTrue(catalog.secondPageEntered.await(2, TimeUnit.SECONDS))
            val startedAt = System.nanoTime()
            signal.cancel()
            val error = withTimeout(2_000) { outcome.await() }
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            job.join()
            assertTrue(error is ScanCancelledException)
            assertTrue("取消耗时 ${elapsedMs}ms", elapsedMs < 1_000)
            assertEquals(listOf(1L, 2L), index.assetsAfter(-1L, 10).map { it.assetId })
            assertEquals(2L, index.session(METADATA_SESSION_ID)?.cursorId)
            assertEquals(SESSION_RUNNING, index.session(METADATA_SESSION_ID)?.status)
            assertEquals(2, catalog.pageCalls)
        }
    }

    /**
     * 断点之前的行不重写。后面的页继续写入。
     */
    @Test
    fun resumeDoesNotRewriteCheckpointedRows() = runBlocking {
        val rows = (1L..5L).map { id -> media(id) }
        AlbumIndex.open(context(), "resume.db").use { index ->
            var now = 100L
            val first = ListCatalog(rows)
            val signal = CancellationSignal()
            val error = runCatching {
                scanner(first, index, now = { now }).progress(2, signal).collect {
                    if (it.phase == MetadataPhase.INDEX) signal.cancel()
                }
            }.exceptionOrNull()
            assertTrue(error is ScanCancelledException)
            assertEquals(100L, index.asset(1L)?.indexedAt)
            index.upsertFeatures(listOf(feature(1L)))

            now = 900L
            val second = ListCatalog(rows)
            scanner(second, index, now = { now }).progress(2, CancellationSignal()).collect { }
            assertEquals(2L, second.cursors.first())
            assertEquals(100L, index.asset(1L)?.indexedAt)
            assertEquals(900L, index.asset(3L)?.indexedAt)
            assertEquals("finger-1", index.feature(1L)?.contentFingerprint)
            assertEquals(SESSION_DONE, index.session(METADATA_SESSION_ID)?.status)
            assertEquals(5, index.assetsAfter(-1L, 10).size)
        }
    }

    /**
     * 上一轮已经完成时，下一次从第一页重读。已有特征还在。
     */
    @Test
    fun completedScanStartsAgain() = runBlocking {
        val rows = listOf(media(1L), media(2L))
        AlbumIndex.open(context(), "again.db").use { index ->
            var now = 10L
            scanner(ListCatalog(rows), index, now = { now }).progress(10, CancellationSignal()).collect { }
            index.upsertFeatures(listOf(feature(1L)))
            now = 80L
            val again = ListCatalog(rows)
            scanner(again, index, now = { now }).progress(10, CancellationSignal()).collect { }
            assertEquals(-1L, again.cursors.first())
            assertEquals(80L, index.asset(1L)?.indexedAt)
            assertEquals("finger-1", index.feature(1L)?.contentFingerprint)
        }
    }

    /**
     * 每满一页的条数预算就让出一次。
     */
    @Test
    fun yieldsAfterEachItemBudget() = runBlocking {
        var pauses = 0
        val pacer = YieldPacer(
            itemBudget = 2,
            timeBudgetNs = Long.MAX_VALUE,
            pause = { pauses += 1 },
        )
        val rows = (1L..5L).map { id -> media(id) }
        AlbumIndex.open(context(), "yield.db").use { index ->
            scanner(ListCatalog(rows), index, pacer = pacer).progress(2, CancellationSignal()).collect { }
        }
        assertEquals(2, pauses)
    }

    /**
     * 元数据结束但指纹还没算时，会话保持进行中，进度里没有完成。
     */
    @Test
    fun handsOffToFingerprintWithoutClosing() = runBlocking {
        val rows = listOf(media(1L), media(2L))
        AlbumIndex.open(context(), "handoff.db").use { index ->
            val seen = mutableListOf<MetadataPhase>()
            scanner(ListCatalog(rows), index).progress(10, CancellationSignal(), closeSession = false).collect {
                seen += it.phase
            }
            assertEquals(MetadataPhase.INDEX, seen.last())
            assertEquals(SESSION_RUNNING, index.session(METADATA_SESSION_ID)?.status)
            assertEquals("FINGERPRINT", index.session(METADATA_SESSION_ID)?.phase)
            assertEquals(2L, index.session(METADATA_SESSION_ID)?.cursorId)
        }
    }
}

/**
 * 让出只看条数和时间，不碰索引。
 */
class YieldPacerTest {
    /**
     * 连续计算满 8 毫秒就让出，不到 30 条也一样。
     */
    @Test
    fun pausesWhenTheTimeBudgetIsSpent() = runBlocking {
        var now = 0L
        var pauses = 0
        val pacer = YieldPacer(
            itemBudget = 30,
            timeBudgetNs = 8_000_000L,
            nanoTime = { now },
            pause = { pauses += 1 },
        )
        pacer.open()
        now = 8_000_000L
        pacer.after(1)
        assertEquals(1, pauses)
    }

    /**
     * 没到条数，也没到时间，就不让出。
     */
    @Test
    fun staysQuietInsideTheBudget() = runBlocking {
        var pauses = 0
        val pacer = YieldPacer(
            itemBudget = 30,
            timeBudgetNs = 8_000_000L,
            nanoTime = { 0L },
            pause = { pauses += 1 },
        )
        pacer.open()
        pacer.after(29)
        assertEquals(0, pauses)
    }
}

private fun context() = RuntimeEnvironment.getApplication()

private fun scanner(
    catalog: MediaCatalog,
    index: AlbumIndex,
    now: () -> Long = { 1L },
    pacer: YieldPacer = YieldPacer(itemBudget = Int.MAX_VALUE, timeBudgetNs = Long.MAX_VALUE, pause = {}),
): MetadataScanner {
    return MetadataScanner(catalog, index, now = now, pacer = pacer)
}

private fun media(id: Long): MediaRow {
    return MediaRow(
        assetId = id,
        uri = "content://photo/$id",
        sizeBytes = id,
        width = 8,
        height = 8,
        dateAdded = 1L,
        dateModified = 2L,
        dateTaken = null,
        bucketId = "bucket",
        relativePath = "DCIM/$id.jpg",
        isFavorite = false,
        mediaVersion = null,
    )
}

private fun feature(id: Long): FeatureRecord {
    return FeatureRecord(
        assetId = id,
        byteHash = null,
        phash = null,
        dhash = null,
        blurScore = null,
        contentFingerprint = "finger-$id",
        algorithmVersion = 1,
        computedAt = 4L,
    )
}

private class ListCatalog(
    private val rows: List<MediaRow>,
) : MediaCatalog {
    val cursors = mutableListOf<Long>()

    override fun pageAfter(cursorId: Long, limit: Int, signal: CancellationSignal): List<MediaRow> {
        if (signal.isCanceled) throw OperationCanceledException()
        cursors += cursorId
        return rows.filter { it.assetId > cursorId }.sortedBy { it.assetId }.take(limit)
    }

    override fun total(signal: CancellationSignal): Int {
        if (signal.isCanceled) throw OperationCanceledException()
        return rows.size
    }

    override fun countThrough(cursorId: Long, signal: CancellationSignal): Int {
        if (signal.isCanceled) throw OperationCanceledException()
        if (cursorId < 0L) return 0
        return rows.count { it.assetId <= cursorId }
    }
}

private class GatedCatalog(
    private val rows: List<MediaRow>,
) : MediaCatalog {
    var pageCalls: Int = 0
    val secondPageEntered = CountDownLatch(1)

    override fun pageAfter(cursorId: Long, limit: Int, signal: CancellationSignal): List<MediaRow> {
        if (signal.isCanceled) throw OperationCanceledException()
        pageCalls += 1
        if (pageCalls >= 2) {
            secondPageEntered.countDown()
            waitUntilCancelled(signal)
            throw OperationCanceledException()
        }
        return rows.filter { it.assetId > cursorId }.sortedBy { it.assetId }.take(limit)
    }

    override fun total(signal: CancellationSignal): Int = rows.size

    override fun countThrough(cursorId: Long, signal: CancellationSignal): Int {
        if (cursorId < 0L) return 0
        return rows.count { it.assetId <= cursorId }
    }
}

private fun waitUntilCancelled(signal: CancellationSignal) {
    val lock = Object()
    val listener = CancellationSignal.OnCancelListener {
        synchronized(lock) { lock.notifyAll() }
    }
    signal.setOnCancelListener(listener)
    val deadline = System.nanoTime() + 2_000_000_000L
    synchronized(lock) {
        while (!signal.isCanceled) {
            val remainingMs = (deadline - System.nanoTime()) / 1_000_000
            if (remainingMs <= 0L) error("取消没有传到查询")
            lock.wait(remainingMs)
        }
    }
}
