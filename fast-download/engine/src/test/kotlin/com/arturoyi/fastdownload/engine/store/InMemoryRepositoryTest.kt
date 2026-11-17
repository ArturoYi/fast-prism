/**
 * 内存仓库与契约检查。
 *
 * @file
 */
package com.arturoyi.fastdownload.engine.store

import com.arturoyi.fastdownload.testing.RepositoryChecks
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * [InMemoryBreakpointStore] 满足仓库契约。
 */
class InMemoryRepositoryTest {
    /**
     * 排序、偏移、脏标记清洗和非法迁移与另一份实现一致。
     */
    @Test
    fun matchesRepositoryContract() {
        val root = Files.createTempDirectory("fastdownload-memory").toFile()
        RepositoryChecks.verify(InMemoryBreakpointStore(), root)
    }
}
