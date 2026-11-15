/**
 * 完成前核对文件摘要。
 *
 * @file
 */
package com.arturoyi.fastdowload.core.spi

import com.arturoyi.fastdowload.core.model.IntegritySpec
import java.io.File

/**
 * 判断文件是否和期望摘要一致。
 *
 * 不一致时调用方不得把临时文件改成最终文件。实现要能被多条任务同时调用。
 */
public fun interface IntegrityVerifier {
    /**
     * 核对 [file]。
     *
     * `Content-MD5` 的期望值来自 [contentMd5]，不是 [spec] 里的调用方文本。
     *
     * @param file 已经写完的文件。
     * @param spec 调用方要求的校验。
     * @param contentMd5 响应头 `Content-MD5`。没有时为 null。
     * @return 摘要一致时为 true。
     */
    public fun verify(file: File, spec: IntegritySpec, contentMd5: String?): Boolean
}
