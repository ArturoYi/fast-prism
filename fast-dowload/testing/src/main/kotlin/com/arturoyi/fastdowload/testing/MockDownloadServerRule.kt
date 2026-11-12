/**
 * 给下载测试用的本地 HTTP 服务。
 *
 * 每个测试方法启动自己的 MockWebServer，结束时关闭。
 * 响应体按给定字节原样返回，不额外压缩。
 *
 * @file
 */
package com.arturoyi.fastdowload.testing

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/** 10 MiB 的字节数，供大响应测试复用。 */
public const val TEN_MIB: Int = 10 * 1024 * 1024

/**
 * 生成长度为 [size] 的可重复字节序列。
 *
 * 第 `i` 个字节是 `i % 251`。内容只依赖长度，方便比对整文件。
 *
 * @param size 字节数，不能为负。
 * @return 长度为 [size] 的数组。`size` 为 0 时返回空数组。
 * @throws IllegalArgumentException [size] 小于 0。
 */
public fun patternedBody(size: Int): ByteArray {
    require(size >= 0) { "size 不能为负" }
    return ByteArray(size) { index -> (index % 251).toByte() }
}

/**
 * 对任何路径都返回同一份正文和同一组头。
 *
 * @param body 原样写入响应体的字节。
 * @param headers 附加响应头。同名头后写的值覆盖先写的值。
 * @param code HTTP 状态码。
 */
public class FixedBodyDispatcher(
    private val body: ByteArray,
    private val headers: Map<String, String> = emptyMap(),
    private val code: Int = 200,
) : Dispatcher() {
    /**
     * 忽略请求内容，返回构造时给定的响应。
     *
     * @param request MockWebServer 收到的请求。路径、方法和头都不影响响应。
     * @return 状态码、头和正文都已设好的响应。
     */
    public override fun dispatch(request: RecordedRequest): MockResponse {
        val response = MockResponse.Builder()
            .code(code)
            .body(Buffer().write(body))
        headers.forEach { (name, value) ->
            response.setHeader(name, value)
        }
        return response.build()
    }
}

/**
 * 每个测试方法各用一个新的 MockWebServer。
 *
 * [beforeEach] 启动端口，[afterEach] 关闭。关闭后不要再调用 [enqueueFixed] 或 [url]。
 */
public class MockDownloadServerRule : BeforeEachCallback, AfterEachCallback {
    /** 本测试方法独占的服务器。只在回调之间使用。 */
    public val server: MockWebServer = MockWebServer()

    /**
     * 在测试方法开始前监听一个系统分配的端口。
     *
     * @param context 当前测试的扩展上下文。本规则不读取其中的标签或存储。
     */
    public override fun beforeEach(context: ExtensionContext) {
        server.start()
    }

    /**
     * 在测试方法结束后关闭服务器并释放端口。
     *
     * @param context 当前测试的扩展上下文。关闭失败时异常会留给 JUnit。
     */
    public override fun afterEach(context: ExtensionContext) {
        server.close()
    }

    /**
     * 让后续所有请求都返回同一份正文。
     *
     * 重复调用会替换调度器，不会排成多次队列响应。
     *
     * @param body 响应正文字节。
     * @param headers 附加响应头。
     * @param code HTTP 状态码，默认 200。
     */
    public fun enqueueFixed(
        body: ByteArray,
        headers: Map<String, String> = emptyMap(),
        code: Int = 200,
    ) {
        server.dispatcher = FixedBodyDispatcher(body, headers, code)
    }

    /**
     * 返回这台服务器上 [path] 的绝对地址。
     *
     * @param path 以 `/` 开头的路径。
     * @return 含实际端口的 `http` 地址。
     */
    public fun url(path: String): String = server.url(path).toString()
}
