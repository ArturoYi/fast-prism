/**
 * 初始化和保护规则用的配置。
 *
 * @file
 */
package com.arturoyi.albumcleaner

import java.nio.ByteBuffer

/**
 * 创建 [AlbumCleaner] 时的可调参数。
 *
 * 语义聚类默认关闭。打开时要同时给出 [modelProvider]，否则以后扫描会跳过这一级。
 *
 * @property thresholds 模糊、相似和连拍的判定线。
 * @property protection 不能进入候选的照片。
 * @property pageSize 扫描和查询的每批条数。必须大于 0。
 * @property maxConcurrency 特征计算的并发上限。必须大于 0。
 * @property enableSemanticClustering 是否启用语义聚类。默认关闭。
 * @property modelProvider 语义模型的加载入口。关闭语义聚类时为 null。
 * @property emitOnMain 进度是否切到主线程再交给宿主。扫描计算始终在后台线程。
 */
public data class CleanerConfig(
    public val thresholds: ThresholdConfig = ThresholdConfig.default(),
    public val protection: ProtectionPolicyConfig = ProtectionPolicyConfig.default(),
    public val pageSize: Int = 50,
    public val maxConcurrency: Int = 4,
    public val enableSemanticClustering: Boolean = false,
    public val modelProvider: ModelProvider? = null,
    public val emitOnMain: Boolean = true,
)

/**
 * 重复、模糊和连拍的阈值。
 *
 * @property blurVarianceMin 清晰度低于这个值视为模糊。
 * @property phashHammingMax 感知哈希的汉明距离上限，小于等于这个值视为近似。
 * @property burstWindowMs 连拍时间窗，单位毫秒。
 * @property sizeTolerancePercent 同一尺寸档允许的边长差，百分比。
 * @property favoriteProtectDays 收藏后继续保护的天数。
 * @property maxGroupSize 一组最多收进多少张，超出后按时间切开。
 */
public data class ThresholdConfig(
    public val blurVarianceMin: Double = 100.0,
    public val phashHammingMax: Int = 8,
    public val burstWindowMs: Long = 60_000L,
    public val sizeTolerancePercent: Int = 20,
    public val favoriteProtectDays: Int = 7,
    public val maxGroupSize: Int = 20,
) {
    public companion object {
        /** 文档里的默认阈值。 */
        public fun default(): ThresholdConfig = ThresholdConfig()
    }
}

/**
 * 哪些照片不能被推荐删除。
 *
 * @property protectFavorites 收藏不进入候选。
 * @property protectRecentDays 最近这么多天内拍摄的不进入候选。0 表示不按时间保护。
 * @property excludedBucketIds 宿主指定排除的相册桶。
 * @property protectedExtensions 这些扩展名不进入候选，比较时忽略大小写。
 * @property protectLargestInGroup 一组里体积最大的那张不进入候选。
 */
public data class ProtectionPolicyConfig(
    public val protectFavorites: Boolean = true,
    public val protectRecentDays: Int = 7,
    public val excludedBucketIds: Set<String> = emptySet(),
    public val protectedExtensions: Set<String> = setOf("raw", "dng", "heic"),
    public val protectLargestInGroup: Boolean = true,
) {
    public companion object {
        /** 文档里的默认保护规则。 */
        public fun default(): ProtectionPolicyConfig = ProtectionPolicyConfig()
    }
}

/**
 * 语义模型的加载入口。SDK 不打包模型，也不依赖推理运行时。
 *
 * 返回 null 表示这次没有模型。
 */
public fun interface ModelProvider {
    /**
     * 读出模型字节。
     *
     * @return 模型内容。没有模型时返回 null。
     */
    public fun load(): ByteBuffer?
}
