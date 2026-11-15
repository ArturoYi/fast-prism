/**
 * 串行处理入队、调度和下载结果。
 *
 * 下载线程只返回结果。状态只在名为 `fastdowload-command` 的线程上修改。
 *
 * @file
 */
package com.arturoyi.fastdowload.engine.dispatch

import com.arturoyi.fastdowload.core.error.DownloadError
import com.arturoyi.fastdowload.core.error.ErrorCode
import com.arturoyi.fastdowload.core.model.Defaults
import com.arturoyi.fastdowload.core.model.Destination
import com.arturoyi.fastdowload.core.model.DownloadRecord
import com.arturoyi.fastdowload.core.model.DownloadRequest
import com.arturoyi.fastdowload.core.model.EnqueueAction
import com.arturoyi.fastdowload.core.model.NetworkType
import com.arturoyi.fastdowload.core.model.PublicIds
import com.arturoyi.fastdowload.core.monitor.DownloadMonitor
import com.arturoyi.fastdowload.core.model.downloadPartFile
import com.arturoyi.fastdowload.core.model.nextFreeDestination
import com.arturoyi.fastdowload.core.spi.DownloadFilter
import com.arturoyi.fastdowload.core.spi.DownloadRepository
import com.arturoyi.fastdowload.core.spi.TaskScheduler
import com.arturoyi.fastdowload.core.state.StateMachine
import com.arturoyi.fastdowload.core.state.Status
import com.arturoyi.fastdowload.engine.DownloadResult
import com.arturoyi.fastdowload.engine.InterruptToken
import com.arturoyi.fastdowload.engine.retry.TaskRetry
import com.arturoyi.fastdowload.engine.retry.planRetry
import com.arturoyi.fastdowload.engine.schedule.AdjustableNetworkGate
import com.arturoyi.fastdowload.engine.schedule.NetworkGate
import com.arturoyi.fastdowload.engine.schedule.NetworkSnapshot
import com.arturoyi.fastdowload.engine.schedule.allows
import java.io.Closeable
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Level
import java.util.logging.Logger

/**
 * 空闲时拉长等待，有新命令时立刻恢复。
 *
 * 第一次等待 [initialMs]，之后翻倍，直到 [maxMs]。不是固定的 500 毫秒轮询。
 *
 * @param initialMs 第一次空闲等待，单位毫秒。
 * @param maxMs 等待上限，单位毫秒。
 */
public class IdleBackoff(
    private val initialMs: Long = 1_000L,
    private val maxMs: Long = 60_000L,
) {
    /** 下一次空闲等待的毫秒数。被 [reset] 之前会越来越长。 */
    public var currentMs: Long = initialMs
        private set

    /**
     * 取出当前等待并加长下一次。
     *
     * @return 这次应该等待的毫秒数。
     */
    public fun next(): Long {
        val value = currentMs
        currentMs = minOf(currentMs * 2, maxMs)
        return value
    }

    /**
     * 有新命令时回到第一次的等待长度。
     */
    public fun reset() {
        currentMs = initialMs
    }
}

/**
 * 入队、开始、结束和移除都进同一条队列。
 *
 * 标识按地址、相对路径和 [namespace] 计算，和任务分组无关。
 * `INCREMENT_FILE_NAME` 会先挑一个还不存在的文件名，因此 [enqueue] 要等命令线程算完再返回。
 *
 * @param repository 任务存储。
 * @param runner 真正写文件的动作。
 * @param network 当前网络是否允许下载。默认始终允许。
 * @param concurrentLimit 同时下载的任务数。
 * @param backoff 空闲等待。测试可以换成很短的间隔。
 * @param namespace 参与任务标识的命名空间。
 * @param storageRoot 应用内部存储根目录。删除文件和避开已有文件时使用。null 时只看仓库里的路径。
 * @param idleWaitListener 进入空闲等待时收到这一次要等的毫秒数。
 * @param startFrozen 为 true 时先不启动排队中的任务。
 * @param clock 当前时间，Unix 纪元毫秒。测试用来跳过退避等待。
 * @param retryWake 到了重试时间时如何叫醒命令线程。null 时自己按延迟投递。
 * @param monitor 重试和终态指标。默认不记录。
 */
public class CommandDispatcher(
    private val repository: DownloadRepository,
    private val runner: DownloadRunner,
    network: NetworkGate = NetworkGate { NetworkSnapshot(connected = true, wifi = true, unmetered = true).allows(it) },
    concurrentLimit: Int = Defaults.CONCURRENT_LIMIT,
    private val backoff: IdleBackoff = IdleBackoff(),
    private val namespace: String = Defaults.GROUP,
    private val storageRoot: File? = null,
    private val idleWaitListener: ((Long) -> Unit)? = null,
    startFrozen: Boolean = false,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retryWake: ((Long) -> Unit)? = null,
    private val monitor: DownloadMonitor = DownloadMonitor.NONE,
) : Closeable, TaskScheduler {
    private val networkGate = AdjustableNetworkGate(network)
    private val commands = LinkedBlockingQueue<Command>()
    private val active = ConcurrentHashMap.newKeySet<String>()
    private val stopping = ConcurrentHashMap.newKeySet<String>()
    private val running = ConcurrentHashMap<String, Long>()
    private val tokens = ConcurrentHashMap<String, InterruptToken>()
    private val retryNotBefore = ConcurrentHashMap<String, Long>()
    private val holdForNetwork = ConcurrentHashMap.newKeySet<String>()
    private val generation = AtomicLong()
    private val listeners = CopyOnWriteArrayList<(List<DownloadRecord>) -> Unit>()
    private val workers = Executors.newFixedThreadPool(POOL_SIZE) { runnable ->
        Thread(runnable, "fastdowload-task").apply { isDaemon = true }
    }
    private val retryScheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "fastdowload-retry").apply { isDaemon = true }
    }
    private val stopped = AtomicBoolean(false)
    private val frozen = AtomicBoolean(startFrozen)
    private val thread = Thread(::loop, COMMAND_THREAD).apply { isDaemon = true }

    @Volatile
    private var limit: Int = concurrentLimit.coerceIn(1, POOL_SIZE)

    private var published: List<DownloadRecord> = emptyList()

    init {
        require(namespace.isNotBlank()) { "namespace 不能为空" }
        thread.start()
    }

    /**
     * 登记任务并返回标识。
     *
     * 会等到命令线程处理完这一次入队。文件名递增时，返回的标识对应该次实际选中的路径。
     * 不要在命令线程之外再假设标识一定等于原始路径的哈希。
     *
     * @param request 下载请求。
     * @return 任务标识。
     */
    public override fun enqueue(request: DownloadRequest): String {
        val box = AtomicReference<String>()
        val failure = AtomicReference<Throwable>()
        runCommand {
            try {
                box.set(enqueueLocked(request))
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        failure.get()?.let { throw it }
        return box.get()
    }

    /**
     * 按顺序登记多条任务，放在同一个存储事务里。
     *
     * @param requests 要入队的请求。
     * @return 与 [requests] 顺序相同的标识。
     */
    public fun enqueueAll(requests: List<DownloadRequest>): List<String> {
        val box = AtomicReference<List<String>>(emptyList())
        val failure = AtomicReference<Throwable>()
        runCommand {
            try {
                box.set(
                    repository.transaction {
                        requests.map { request -> enqueueLocked(request) }
                    },
                )
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        failure.get()?.let { throw it }
        return box.get()
    }

    /**
     * 把已登记但还没排队的任务放进队列。
     *
     * @param publicId 任务标识。
     */
    public fun start(publicId: String) {
        commands.put(Command.Move(publicId, Status.QUEUED))
    }

    /**
     * 把失败或取消的任务重新排队，并清掉已经用掉的自动重试次数。
     *
     * 自动重试不会走这里。调用方明确要求再试一次时，重新获得完整次数。
     *
     * @param publicId 任务标识。
     */
    public fun retry(publicId: String) {
        commands.put(Command.Retry(publicId))
    }

    /**
     * 把已经用掉的自动重试次数清零，不改变状态。
     *
     * 次数会写进仓库。进程重启后不会回到清零之前。
     *
     * @param publicId 任务标识。
     */
    public fun resetAutoRetryAttempts(publicId: String) {
        commands.put(Command.ResetAttempts(publicId))
    }

    /**
     * 暂停任务，并让正在写的线程停下来。断点保留。
     *
     * @param publicId 任务标识。
     */
    public fun pause(publicId: String) {
        commands.put(Command.Move(publicId, Status.PAUSED))
    }

    /**
     * 继续已暂停、已登记或等待网络的任务。
     *
     * @param publicId 任务标识。
     */
    public fun resume(publicId: String) {
        commands.put(Command.Move(publicId, Status.QUEUED))
    }

    /**
     * 取消任务，保留文件和断点。
     *
     * @param publicId 任务标识。
     */
    public override fun cancel(publicId: String) {
        commands.put(Command.Move(publicId, Status.CANCELLED))
    }

    /**
     * 移出队列并保留目标文件。临时文件和断点会删掉。
     *
     * @param publicId 任务标识。
     */
    public fun remove(publicId: String) {
        commands.put(Command.Move(publicId, Status.REMOVED))
    }

    /**
     * 删除记录、断点、临时文件和目标文件。
     *
     * @param publicId 任务标识。
     */
    public fun delete(publicId: String) {
        commands.put(Command.Move(publicId, Status.DELETED))
    }

    /**
     * 暂停所有还没结束的任务。
     */
    public fun pauseAll() {
        commands.put(Command.Apply(ACTIVE_STATUSES, Status.PAUSED, groupId = null))
    }

    /**
     * 把已暂停和已登记的任务重新排队。
     */
    public fun resumeAll() {
        commands.put(Command.Apply(setOf(Status.PAUSED, Status.ADDED), Status.QUEUED, groupId = null))
    }

    /**
     * 取消所有还没结束的任务。
     */
    public fun cancelAll() {
        commands.put(Command.Apply(ACTIVE_STATUSES, Status.CANCELLED, groupId = null))
    }

    /**
     * 暂停一个分组里还没结束的任务。
     *
     * @param groupId 分组名。
     */
    public fun pauseGroup(groupId: String) {
        commands.put(Command.Apply(ACTIVE_STATUSES, Status.PAUSED, groupId))
    }

    /**
     * 继续一个分组里已暂停或已登记的任务。
     *
     * @param groupId 分组名。
     */
    public fun resumeGroup(groupId: String) {
        commands.put(Command.Apply(setOf(Status.PAUSED, Status.ADDED), Status.QUEUED, groupId))
    }

    /**
     * 取消一个分组里还没结束的任务。
     *
     * @param groupId 分组名。
     */
    public fun cancelGroup(groupId: String) {
        commands.put(Command.Apply(ACTIVE_STATUSES, Status.CANCELLED, groupId))
    }

    /**
     * 删除一个分组里的任务和目标文件。
     *
     * @param groupId 分组名。
     */
    public fun deleteGroup(groupId: String) {
        commands.put(Command.Apply(Status.entries.toSet(), Status.DELETED, groupId))
    }

    /**
     * 暂时不再启动新的下载。已经在写的任务会继续。
     */
    public fun freeze() {
        commands.put(Command.Freeze)
    }

    /**
     * 允许再次启动排队中的任务。
     */
    public fun unfreeze() {
        commands.put(Command.Unfreeze)
    }

    /**
     * 修改同时下载的任务数。
     *
     * @param limit 至少 1，最多 [POOL_SIZE]。
     */
    public override fun setConcurrentLimit(limit: Int) {
        commands.put(Command.Limit(limit.coerceIn(1, POOL_SIZE)))
    }

    /**
     * 给所有任务再加一条网络要求。
     *
     * null 表示取消这层额外要求。
     *
     * @param type 全局网络要求。
     */
    public fun setRequiredNetwork(type: NetworkType?) {
        commands.put(Command.Network(type))
    }

    /**
     * 让命令线程再看一遍等待网络和排队中的任务。
     */
    public fun wake() {
        commands.put(Command.Wake)
    }

    /**
     * 任务列表变化时回调。回调在命令线程上。
     *
     * @param listener 接收当前全部任务。
     */
    public fun addListener(listener: (List<DownloadRecord>) -> Unit) {
        listeners.add(listener)
    }

    /**
     * 去掉 [addListener] 登记的回调。
     *
     * 必须传入当时的同一个实例。
     *
     * @param listener 要去掉的回调。
     */
    public fun removeListener(listener: (List<DownloadRecord>) -> Unit) {
        listeners.remove(listener)
    }

    /**
     * 等到已经提交的命令都处理完。
     *
     * 测试用来观察状态。新的下载可能仍在工作线程里跑。
     */
    public fun drain() {
        val latch = CountDownLatch(1)
        commands.put(Command.Drain(latch))
        check(latch.await(5, TimeUnit.SECONDS)) { "命令没有在时限内排空" }
    }

    /**
     * 停止命令线程和下载线程。
     */
    public override fun close() {
        stopped.set(true)
        thread.interrupt()
        thread.join(2_000)
        workers.shutdownNow()
        retryScheduler.shutdownNow()
    }

    private fun runCommand(body: () -> Unit) {
        if (Thread.currentThread().name == COMMAND_THREAD) {
            body()
            return
        }
        val done = CountDownLatch(1)
        commands.put(
            Command.Invoke {
                try {
                    body()
                } finally {
                    done.countDown()
                }
            },
        )
        check(done.await(10, TimeUnit.SECONDS)) { "命令没有在时限内完成" }
    }

    private fun loop() {
        while (!stopped.get()) {
            val command = try {
                if (commands.isEmpty() && active.isEmpty()) {
                    val wait = backoff.next()
                    idleWaitListener?.invoke(wait)
                    commands.poll(wait, TimeUnit.MILLISECONDS)
                } else {
                    backoff.reset()
                    commands.take()
                }
            } catch (_: InterruptedException) {
                if (stopped.get()) break
                continue
            }
            if (command != null) backoff.reset()
            try {
                if (command != null) handle(command)
                promoteWaits(command is Command.Wake || command is Command.Network)
                startReady()
                publish()
            } catch (error: IllegalStateException) {
                logger.log(Level.SEVERE, error.message, error)
            }
        }
    }

    private fun handle(command: Command) {
        when (command) {
            is Command.Invoke -> command.block()
            is Command.Move -> move(command.publicId, command.to)
            is Command.Retry -> {
                retryNotBefore.remove(command.publicId)
                zeroAttempts(command.publicId)
                move(command.publicId, Status.QUEUED)
            }
            is Command.ResetAttempts -> zeroAttempts(command.publicId)
            is Command.Apply -> apply(command.statuses, command.to, command.groupId)
            is Command.Finished -> finish(command.publicId, command.generation, command.result)
            is Command.Limit -> limit = command.value
            is Command.Network -> networkGate.required = command.type
            Command.Freeze -> frozen.set(true)
            Command.Unfreeze -> frozen.set(false)
            Command.Wake -> Unit
            is Command.Drain -> command.latch.countDown()
        }
    }

    private fun enqueueLocked(request: DownloadRequest): String {
        if (request.enqueueAction == EnqueueAction.INCREMENT_FILE_NAME) {
            val free = request.copy(destination = nextFreeDestination(request.destination, ::destinationTaken))
            return insertNew(identity(free), free)
        }
        val id = identity(request)
        val existing = repository.get(id) ?: return insertNew(id, request)
        return when (request.enqueueAction) {
            EnqueueAction.DO_NOT_ENQUEUE_IF_EXISTING -> id
            EnqueueAction.UPDATE_ACCORDINGLY -> updateExisting(existing, request)
            EnqueueAction.REPLACE_EXISTING -> replaceExisting(existing, request)
            EnqueueAction.INCREMENT_FILE_NAME -> id
        }
    }

    private fun insertNew(id: String, request: DownloadRequest): String {
        val now = System.currentTimeMillis()
        val previous = repository.get(id)
        repository.upsert(
            DownloadRecord(
                id = id,
                request = request,
                status = Status.ADDED,
                createdAt = now,
                updatedAt = now.coerceAtLeast(previous?.updatedAt ?: now),
            ),
        )
        if (request.downloadOnEnqueue) {
            repository.transition(id, Status.QUEUED)
        }
        return id
    }

    private fun updateExisting(existing: DownloadRecord, request: DownloadRequest): String {
        val now = System.currentTimeMillis().coerceAtLeast(existing.updatedAt)
        repository.upsert(
            existing.copy(
                request = existing.request.copy(
                    headers = request.headers,
                    priority = request.priority,
                    tags = request.tags,
                    extras = request.extras,
                ),
                updatedAt = now,
            ),
        )
        return existing.id
    }

    private fun replaceExisting(existing: DownloadRecord, request: DownloadRequest): String {
        stopRunning(existing.id)
        deleteFiles(existing, keepTarget = false)
        repository.clearBlocks(existing.id)
        return insertNew(existing.id, request)
    }

    private fun identity(request: DownloadRequest): String {
        return PublicIds.generate(request.url, request.destination, namespace)
    }

    private fun destinationTaken(relative: String): Boolean {
        val used = repository.query().any { record ->
            record.request.destination.relativePath == relative &&
                record.status != Status.REMOVED &&
                record.status != Status.DELETED
        }
        if (used) return true
        val root = storageRoot ?: return false
        return Destination(relative).resolveInside(root).isFile
    }

    private fun apply(statuses: Set<Status>, to: Status, groupId: String?) {
        repository.query(DownloadFilter(statuses)).forEach { record ->
            if (groupId != null && record.request.groupId != groupId) return@forEach
            move(record.id, to)
        }
    }

    private fun move(publicId: String, to: Status) {
        val record = repository.get(publicId) ?: return
        if (record.status == to && to != Status.QUEUED && to != Status.WAITING_NETWORK) return
        if (to == Status.QUEUED) holdForNetwork.remove(publicId)
        if (!StateMachine.isLegal(record.status, to)) {
            StateMachine.requireTransition(record.status, to)
        }
        if (to == Status.PAUSED || to == Status.CANCELLED || to == Status.REMOVED || to == Status.DELETED) {
            stopRunning(publicId)
        }
        repository.transition(publicId, to)
        when (to) {
            Status.REMOVED -> {
                repository.clearBlocks(publicId)
                deleteFiles(record, keepTarget = true)
            }
            Status.DELETED -> {
                repository.clearBlocks(publicId)
                deleteFiles(record, keepTarget = false)
            }
            else -> Unit
        }
    }

    private fun stopRunning(publicId: String) {
        if (!active.contains(publicId) && !running.containsKey(publicId)) return
        stopping.add(publicId)
        tokens.remove(publicId)?.interrupt()
        active.remove(publicId)
    }

    private fun finish(publicId: String, generation: Long, result: DownloadResult) {
        if (running[publicId] != generation) {
            stopping.remove(publicId)
            return
        }
        running.remove(publicId)
        tokens.remove(publicId)
        active.remove(publicId)
        stopping.remove(publicId)
        val record = repository.get(publicId) ?: return
        if (record.status != Status.DOWNLOADING) return
        when (result) {
            is DownloadResult.Success -> {
                monitor.onTerminal(publicId, success = true, code = null)
                repository.transition(publicId, Status.COMPLETED)
            }
            is DownloadResult.Failure -> retryOrFail(record, result.error)
            is DownloadResult.Busy -> {
                val error = DownloadError(ErrorCode.UNKNOWN, "目标文件正在写入", publicId)
                monitor.onTerminal(publicId, success = false, code = error.code)
                repository.transition(publicId, Status.FAILED, error)
            }
        }
    }

    /**
     * 可重试的失败先写回数次数，再按退避重新排队。
     *
     * 没有网络且策略允许时不消耗次数，转到等待网络。命令线程不等待，到点再投递唤醒。
     *
     * @param record 仍处于下载中的任务。
     * @param error 下载线程返回的失败。
     */
    private fun retryOrFail(record: DownloadRecord, error: DownloadError) {
        val plan = planRetry(record.request.retryPolicy, error, record.retryAttempts)
        when (plan.kind) {
            TaskRetry.WAIT_FOR_NETWORK -> {
                holdForNetwork.add(record.id)
                monitor.onRetry(record.id, plan.nextAttempts, plan.delayMs, error.code)
                logger.log(Level.WARNING, "stage=RETRY publicId=${record.id} code=${error.code.name}")
                repository.transition(record.id, Status.WAITING_NETWORK)
            }
            TaskRetry.BACKOFF -> {
                val updatedAt = clock().coerceAtLeast(record.updatedAt)
                repository.upsert(record.copy(retryAttempts = plan.nextAttempts, updatedAt = updatedAt))
                retryNotBefore[record.id] = clock() + plan.delayMs
                monitor.onRetry(record.id, plan.nextAttempts, plan.delayMs, error.code)
                logger.log(
                    Level.WARNING,
                    "stage=RETRY publicId=${record.id} attempt=${plan.nextAttempts} delayMs=${plan.delayMs} code=${error.code.name}",
                )
                repository.transition(record.id, Status.QUEUED)
                scheduleRetry(plan.delayMs)
            }
            TaskRetry.FAIL -> {
                monitor.onTerminal(record.id, success = false, code = error.code)
                logger.log(Level.SEVERE, "stage=TERMINAL publicId=${record.id} code=${error.code.name}")
                repository.transition(record.id, Status.FAILED, error)
            }
        }
    }

    private fun scheduleRetry(delayMs: Long) {
        val custom = retryWake
        if (custom != null) {
            custom(delayMs)
            return
        }
        retryScheduler.schedule({
            if (!stopped.get()) commands.put(Command.Wake)
        }, delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
    }

    private fun zeroAttempts(publicId: String) {
        retryNotBefore.remove(publicId)
        holdForNetwork.remove(publicId)
        val record = repository.get(publicId) ?: return
        if (record.retryAttempts == 0) return
        repository.upsert(
            record.copy(
                retryAttempts = 0,
                updatedAt = clock().coerceAtLeast(record.updatedAt),
            ),
        )
    }

    /**
     * 导出当前队列、重试分布和错误分布。
     *
     * 地址只保留协议和主机，不包含路径和查询。进程被杀掉后不会因为后台模式自动续跑。
     *
     * @return 多行文本，可直接写进诊断日志。
     */
    public fun dumpState(): String {
        val records = repository.query()
        val metrics = monitor.snapshot()
        return buildString {
            appendLine("后台模式只在进程还活着时保活。进程被杀掉后不会自动续跑，下次启动靠断点恢复。")
            appendLine("active=${active.size} queued=${records.count { it.status == Status.QUEUED }}")
            records.forEach { record ->
                append("id=").append(record.id)
                append(" status=").append(record.status.name)
                append(" attempts=").append(record.retryAttempts)
                append(" host=").append(redact(record.request.url))
                appendLine()
            }
            appendLine("repositoryWrites=${metrics.repositoryWrites}")
            appendLine("retries=${metrics.retryCounts.entries.joinToString { "${it.key.name}:${it.value}" }}")
            appendLine("errors=${metrics.errorCounts.entries.joinToString { "${it.key.name}:${it.value}" }}")
            appendLine("redownloads=${metrics.redownloadCount}")
        }
    }

    private fun deleteFiles(record: DownloadRecord, keepTarget: Boolean) {
        val root = storageRoot ?: return
        val part = downloadPartFile(root, record.request.destination, record.id)
        if (part.isFile) part.delete()
        if (!keepTarget) {
            val target = record.request.destination.resolveInside(root)
            if (target.isFile) target.delete()
        }
    }

    private fun promoteWaits(networkSignal: Boolean) {
        repository.query(DownloadFilter(setOf(Status.WAITING_NETWORK))).forEach { record ->
            if (record.id in holdForNetwork && !networkSignal) return@forEach
            if (networkGate.isAllowed(record.request.networkType)) {
                holdForNetwork.remove(record.id)
                repository.transition(record.id, Status.QUEUED)
            }
        }
    }

    private fun startReady() {
        if (frozen.get()) return
        repository.query(DownloadFilter(setOf(Status.QUEUED))).forEach { record ->
            if (active.size >= limit) return
            if (record.id in active || record.id in stopping) return@forEach
            val readyAt = retryNotBefore[record.id]
            if (readyAt != null && clock() < readyAt) return@forEach
            retryNotBefore.remove(record.id)
            if (!networkGate.isAllowed(record.request.networkType)) {
                repository.transition(record.id, Status.WAITING_NETWORK)
                return@forEach
            }
            val token = InterruptToken()
            val generation = this.generation.incrementAndGet()
            tokens[record.id] = token
            running[record.id] = generation
            active.add(record.id)
            repository.transition(record.id, Status.DOWNLOADING)
            val snapshot = repository.get(record.id) ?: record
            workers.execute {
                val result = try {
                    runner.run(snapshot, token)
                } catch (error: Throwable) {
                    DownloadResult.Failure(
                        DownloadError(
                            code = ErrorCode.UNKNOWN,
                            message = error.message ?: "下载失败",
                            traceId = record.id,
                            cause = error,
                        ),
                    )
                }
                commands.put(Command.Finished(record.id, generation, result))
            }
        }
    }

    private fun publish() {
        val snapshot = repository.query()
        if (snapshot == published) return
        published = snapshot
        listeners.forEach { listener -> listener(snapshot) }
    }

    private sealed interface Command {
        class Invoke(val block: () -> Unit) : Command
        class Move(val publicId: String, val to: Status) : Command
        class Apply(val statuses: Set<Status>, val to: Status, val groupId: String?) : Command
        class Finished(val publicId: String, val generation: Long, val result: DownloadResult) : Command
        class Retry(val publicId: String) : Command
        class ResetAttempts(val publicId: String) : Command
        class Limit(val value: Int) : Command
        class Network(val type: NetworkType?) : Command
        data object Freeze : Command
        data object Unfreeze : Command
        data object Wake : Command
        class Drain(val latch: CountDownLatch) : Command
    }

    public companion object {
        /** 唯一允许修改任务状态的线程名。 */
        public const val COMMAND_THREAD: String = "fastdowload-command"

        /** 工作线程池的大小，也是并发上限的最大值。 */
        public const val POOL_SIZE: Int = 16

        private val ACTIVE_STATUSES: Set<Status> = setOf(
            Status.ADDED,
            Status.QUEUED,
            Status.WAITING_NETWORK,
            Status.DOWNLOADING,
            Status.PAUSED,
        )

        private val logger: Logger = Logger.getLogger("com.arturoyi.fastdowload.engine.dispatch.CommandDispatcher")

        private fun redact(url: String): String {
            val host = runCatching { java.net.URI(url).host }.getOrNull()
            val scheme = url.substringBefore("://", missingDelimiterValue = "")
            return if (host.isNullOrBlank()) "redacted" else "$scheme://$host/"
        }
    }
}
