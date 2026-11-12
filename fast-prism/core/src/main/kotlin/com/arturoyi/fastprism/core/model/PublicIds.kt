/**
 * 由地址、目标和命名空间稳定生成任务标识。
 *
 * @file
 */
package com.arturoyi.fastprism.core.model

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * 生成对外可见的任务标识。
 *
 * 同一组输入永远得到同一个 32 位十六进制字符串。计算使用 SHA-256，只保留前 16 字节。
 */
public object PublicIds {
    /**
     * 为一次下载生成稳定标识。
     *
     * 参与哈希的文本是 `url|relativePath|namespace`，编码为 UTF-8。路径使用 [Destination.relativePath]，
     * 不展开成绝对路径，因此同一相对路径在不同存储根下标识相同。
     *
     * @param url 资源地址，不能为空白。这里不校验协议。
     * @param destination 内部相对路径。
     * @param namespace 调用方命名空间，不能为空白。不同命名空间可以隔离相同地址。
     * @return 32 个小写十六进制字符。
     * @throws IllegalArgumentException [url] 或 [namespace] 为空白。
     */
    public fun generate(url: String, destination: Destination, namespace: String): String {
        require(url.isNotBlank()) { "url 不能为空" }
        require(namespace.isNotBlank()) { "namespace 不能为空" }
        val payload = "$url|${destination.relativePath}|$namespace"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
        return digest.copyOf(16).toHex()
    }
}

/**
 * 把每个字节格式化成两位小写十六进制。
 *
 * @receiver 要编码的字节。空数组得到空字符串。
 * @return 长度为 `size * 2` 的十六进制文本。
 */
internal fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
    "%02x".format(byte.toInt() and 0xff)
}
