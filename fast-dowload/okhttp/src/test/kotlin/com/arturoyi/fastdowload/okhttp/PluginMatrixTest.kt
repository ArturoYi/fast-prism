/**
 * 不同客户端和分块策略写出同一份字节。
 *
 * @file
 */
package com.arturoyi.fastdowload.okhttp

import com.arturoyi.fastdowload.core.model.Destination
import com.arturoyi.fastdowload.core.model.DownloadRequest
import com.arturoyi.fastdowload.core.model.IntegritySpec
import com.arturoyi.fastdowload.core.strategy.AdaptiveBreakpointStrategy
import com.arturoyi.fastdowload.core.strategy.SingleBlockStrategy
import com.arturoyi.fastdowload.engine.BlockDownloadCall
import com.arturoyi.fastdowload.engine.DownloadResult
import com.arturoyi.fastdowload.engine.http.UrlConnectionHttpClient
import com.arturoyi.fastdowload.engine.store.InMemoryBreakpointStore
import com.arturoyi.fastdowload.engine.verify.ChannelIntegrityVerifier
import com.arturoyi.fastdowload.engine.verify.DefaultIntegrityVerifier
import com.arturoyi.fastdowload.testing.MockDownloadServerRule
import com.arturoyi.fastdowload.testing.RangedResourceDispatcher
import com.arturoyi.fastdowload.testing.patternedBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

/**
 * OkHttp 与 `HttpURLConnection`、自适应与单块、两种摘要实现的组合。
 */
class PluginMatrixTest {
    /** 每个测试方法单独启动并关闭的本地服务器。 */
    @JvmField
    @RegisterExtension
    val server = MockDownloadServerRule()

    /** 四种客户端和分块组合的文件哈希相同。 */
    @Test
    fun clientsAndStrategiesWriteTheSameBytes() {
        val body = patternedBody(64 * 1024)
        server.server.dispatcher = RangedResourceDispatcher(body)
        val url = server.url("/file")
        val hashes = mutableListOf<String>()
        listOf(
            "url" to UrlConnectionHttpClient(),
            "okhttp" to OkHttpHttpClient(),
        ).forEach { (clientName, client) ->
            listOf(
                "adaptive" to AdaptiveBreakpointStrategy,
                "single" to SingleBlockStrategy,
            ).forEach { (strategyName, strategy) ->
                val root = tempRoot("$clientName-$strategyName")
                val result = BlockDownloadCall(
                    request = DownloadRequest(
                        url = url,
                        destination = Destination("file.bin"),
                        connectionCount = 4,
                    ),
                    storageRoot = root,
                    store = InMemoryBreakpointStore(),
                    client = client,
                    strategy = strategy,
                ).execute()
                val success = assertInstanceOf(DownloadResult.Success::class.java, result)
                assertEquals(body.size.toLong(), success.bytesWritten)
                hashes += sha256(File(root, "file.bin").readBytes())
            }
        }
        assertEquals(1, hashes.distinct().size)
        assertEquals(sha256(body), hashes.first())
    }

    /** 换一种摘要实现后，对得上的文件仍然可以提交。 */
    @Test
    fun channelVerifierAcceptsTheSameFile() {
        val body = patternedBody(8 * 1024)
        server.server.dispatcher = RangedResourceDispatcher(body)
        val root = tempRoot("verify")
        val digest = sha256(body)
        val result = BlockDownloadCall(
            request = DownloadRequest(
                url = server.url("/file"),
                destination = Destination("file.bin"),
                connectionCount = 1,
                integrity = IntegritySpec.Sha256(digest),
            ),
            storageRoot = root,
            store = InMemoryBreakpointStore(),
            client = UrlConnectionHttpClient(),
            verifier = ChannelIntegrityVerifier,
        ).execute()
        assertInstanceOf(DownloadResult.Success::class.java, result)
        assertEquals(digest, sha256(File(root, "file.bin").readBytes()))
        val again = DefaultIntegrityVerifier.verify(File(root, "file.bin"), IntegritySpec.Sha256(digest), null)
        assertEquals(true, again)
    }

    private fun tempRoot(name: String): File {
        return Files.createTempDirectory("okhttp-matrix-$name").toFile()
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }
}
