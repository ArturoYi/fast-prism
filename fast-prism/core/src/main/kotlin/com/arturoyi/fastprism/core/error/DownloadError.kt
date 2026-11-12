/**
 * 下载失败时对外暴露的错误码、续传失败原因和终态原因。
 *
 * @file
 */
package com.arturoyi.fastprism.core.error

/**
 * 稳定的数字错误码，以及该错误是否允许按重试策略再试。
 *
 * [code] 在枚举项之间唯一。[retryable] 是默认值，单次失败可以用 [DownloadError.retryable] 覆盖。
 *
 * @property code 对外稳定的数字码。不要因枚举重排而改变已有数值。
 * @property retryable 默认是否可以重试。
 */
public enum class ErrorCode(
    public val code: Int,
    public val retryable: Boolean,
) {
    /** 尚未归类的失败，允许重试。 */
    UNKNOWN(0, true),

    /** 域名无法解析，允许重试。 */
    UNKNOWN_HOST(3, true),

    /** 连接或读取超时，允许重试。 */
    CONNECTION_TIMEOUT(4, true),

    /** HTTP 404，不重试。 */
    HTTP_NOT_FOUND(5, false),

    /** HTTP 403，不重试。 */
    HTTP_FORBIDDEN(6, false),

    /** 当前没有可用网络，允许在网络恢复后重试。 */
    NO_NETWORK(7, true),

    /** 目标文件没有创建成功，不重试。 */
    FILE_NOT_CREATED(10, false),

    /** HTTP 412，前置条件失败，不重试。 */
    HTTP_PRECONDITION_FAILED(12, false),

    /** 服务器中途断开，允许重试。 */
    SERVER_CANCELED(13, true),

    /** 落盘内容与声明的摘要不一致，不重试。 */
    INVALID_CONTENT_HASH(20, false),

    /** 目标路径逃出应用内部目录，不重试。 */
    SECURITY_PATH_TRAVERSAL(21, false),

    /** 写入本地文件失败，允许重试。 */
    WRITE_IO_ERROR(22, true),

    /** 下载线程被中断，不重试。 */
    INTERRUPTED(23, false),

    /** 调用方取消了任务，不重试。 */
    CANCELED(24, false),

    /** 下载前预分配文件空间失败，不重试。 */
    FILE_ALLOCATION_FAILED(30, false),

    /** 存储空间不足，不重试。 */
    NO_STORAGE_SPACE(31, false),
}

/**
 * 续传没有从上次偏移继续的原因。
 *
 * 这些值描述探测或区间响应与本地记录不一致，不表示整次下载的最终结果。
 */
public enum class ResumeFailedCause {
    /** 本地记录损坏或字段互相矛盾，不能安全续传。 */
    INFO_DIRTY,

    /** 记录还在，但已写文件不存在。 */
    FILE_NOT_EXIST,

    /** 目标文件不能按随机偏移写入。 */
    OUTPUT_STREAM_NOT_SUPPORT,

    /** 响应里的实体标签与开始下载时保存的不一致。 */
    RESPONSE_ENTITY_TAG_CHANGED,

    /** 条件请求返回前置条件失败。 */
    RESPONSE_PRECONDITION_FAILED,

    /** 新创建的区间响应不是从字节 0 开始。 */
    RESPONSE_CREATED_RANGE_NOT_FROM_0,

    /** 要求从 0 重新下载时，响应区间却不是从 0 开始。 */
    RESPONSE_RESET_RANGE_NOT_FROM_0,

    /** 资源总长度与记录中的长度不同。 */
    CONTENT_LENGTH_CHANGED,

    /** `Last-Modified` 与记录中的值不同。 */
    LAST_MODIFIED_CHANGED,

    /** 服务器认为请求的字节区间无法满足。 */
    RANGE_NOT_SATISFIABLE,
}

/**
 * 一次下载调用结束时的终态。
 *
 * 成功、失败和因忙而没有开始，都用这里的值区分，避免只看异常文本。
 */
public enum class TerminalCause {
    /** 文件已经完整写完。 */
    COMPLETED,

    /** 以错误结束。具体原因见 [DownloadError]。 */
    ERROR,

    /** 目标文件正被别的写入占用。 */
    FILE_BUSY,

    /** 同一条任务已经在运行。 */
    SAME_TASK_BUSY,

    /** 开始写入前预分配空间失败。 */
    PRE_ALLOCATE_FAILED,
}

/**
 * 一次可记录、可展示的下载失败。
 *
 * [retryable] 默认跟随 [code]，构造时可以按这次响应覆盖。
 *
 * @property code 稳定错误码。
 * @property message 给人阅读的说明，不能为空白。
 * @property traceId 这一次失败的关联标识，不能为空白。
 * @property httpCode 触发失败的 HTTP 状态码。不是 HTTP 失败时为 null，给出时必须在 100 到 599。
 * @property resumeFailedCause 续传失败原因。与续传无关时为 null。
 * @property retryable 这一次失败是否还允许重试。
 * @property cause 底层异常。没有底层异常时为 null。
 * @throws IllegalArgumentException [message] 或 [traceId] 为空白，或 [httpCode] 不在 100 到 599。
 */
public class DownloadError(
    public val code: ErrorCode,
    public val message: String,
    public val traceId: String,
    public val httpCode: Int? = null,
    public val resumeFailedCause: ResumeFailedCause? = null,
    public val retryable: Boolean = code.retryable,
    public val cause: Throwable? = null,
) {
    init {
        require(message.isNotBlank()) { "message 不能为空" }
        require(traceId.isNotBlank()) { "traceId 不能为空" }
        require(httpCode == null || httpCode in 100..599) { "httpCode 不合法" }
    }
}
