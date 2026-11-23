/**
 * 不依赖 Android 的状态、原因和进度类型。
 *
 * @file
 */
package com.arturoyi.albumcleaner

/**
 * SDK 现在处在哪一步。
 *
 * [RELEASED] 是终态。[SCANNING] 只在收集 [AlbumCleaner.scan] 时出现。[APPLYING] 留给以后的删除。
 */
public enum class CleanerState {
    /** 还没初始化。 */
    UNINITIALIZED,

    /** 可以开始扫描或查询。 */
    READY,

    /** 正在扫描。 */
    SCANNING,

    /** 正在提交删除。 */
    APPLYING,

    /** 已经释放，不能再初始化。 */
    RELEASED,

    /** 发生了需要宿主处理的错误。 */
    ERROR,
}

/** 候选有多把握。 */
public enum class ConfidenceLevel {
    /** 字节完全一致。 */
    HIGH,

    /** 感知哈希或连拍对上了。 */
    MEDIUM,

    /** 语义相似。默认不产生这种结果。 */
    LOW,
}

/** 一组照片为什么被放在一起。 */
public enum class GroupType {
    /** 文件字节相同。 */
    EXACT_DUPLICATE,

    /** 看起来几乎一样。 */
    NEAR_DUPLICATE,

    /** 短时间连拍。 */
    BURST,

    /** 模糊、过曝或过小。 */
    LOW_QUALITY,

    /** 截图。 */
    SCREENSHOT,

    /** 语义相近。默认关闭。 */
    SEMANTIC,
}

/** 扫描进行到哪一段。 */
public enum class ScanPhase {
    /** 正在读媒体库元数据。 */
    QUERY,

    /** 正在写入本地索引。 */
    INDEX,

    /** 正在计算哈希和清晰度。 */
    FINGERPRINT,

    /** 正在分组。 */
    DEDUP,

    /** 正在决定每组留哪一张。 */
    SCORE,

    /** 这一轮结束。 */
    DONE,
}

/** 媒体库里一张照片发生了什么。 */
public enum class ChangeKind {
    /** 新出现。 */
    INSERTED,

    /** 内容或元数据变了。 */
    UPDATED,

    /** 已经不在媒体库里。 */
    DELETED,
}

/** 一张照片被推荐删除的原因。 */
public enum class ReasonCode {
    /** 有一张字节相同的照片。 */
    EXACT_DUPLICATE,

    /** 有一张看起来几乎一样的照片。 */
    NEAR_DUPLICATE,

    /** 画面模糊。 */
    BLURRY,

    /** 过曝。 */
    OVER_EXPOSED,

    /** 欠曝。 */
    UNDER_EXPOSED,

    /** 尺寸过小。 */
    TOO_SMALL,

    /** 截图。 */
    SCREENSHOT,

    /** 缓存或临时图片。 */
    CACHE_IMAGE,

    /** 语义相近。 */
    SEMANTIC_SIMILAR,
}

/** 一张照片为什么没有进入候选。 */
public enum class ProtectionReason {
    /** 用户收藏了。 */
    FAVORITE,

    /** 拍得太近。 */
    RECENT,

    /** 宿主排除了它所在的相册。 */
    USER_EXCLUDED,

    /** 扩展名在保护列表里。 */
    RAW_FORMAT,

    /** 它是这一组里最大的文件。 */
    LARGEST_IN_GROUP,

    /** 画面里有人。首版不会产出这个原因。 */
    PERSON_DETECTED,
}

/**
 * 一组相似或低质量照片。
 *
 * @property groupId 组标识。
 * @property type 分组依据。
 * @property keepAssetId 建议留下的照片。
 * @property memberCount 组里有多少张。
 * @property totalBytes 组里文件大小之和。
 */
public data class CleanGroup(
    public val groupId: String,
    public val type: GroupType,
    public val keepAssetId: Long,
    public val memberCount: Int,
    public val totalBytes: Long,
)

/**
 * 空间估算。
 *
 * @property totalEstimatedBytes 当前候选大约能释放的字节数。
 * @property byReason 按首个原因归类的估算字节数。
 * @property appliedBytes 已经实际删除的字节数。
 */
public data class SpaceReport(
    public val totalEstimatedBytes: Long,
    public val byReason: Map<ReasonCode, Long>,
    public val appliedBytes: Long,
)

/**
 * 一页查询结果。
 *
 * @param T 这一页的元素。
 * @property items 这一页的内容。
 * @property nextCursor 下一页的游标。没有下一页时为 null。
 * @property total 符合条件的总数。数起来太贵时为 null。
 */
public data class PagedResult<T>(
    public val items: List<T>,
    public val nextCursor: String?,
    public val total: Int?,
)

/**
 * 扫描进度。
 *
 * @property scanned 这一阶段已经处理的张数。
 * @property total 这一阶段的总数。还不知道时为 null。
 * @property phase 当前阶段。
 */
public data class ScanProgress(
    public val scanned: Int,
    public val total: Int?,
    public val phase: ScanPhase,
)

/**
 * 删除提交的进度。
 *
 * @property succeeded 已经从索引里去掉的张数。
 * @property failed 没能去掉的张数。
 * @property total 这次打算处理的张数。
 */
public data class ApplyProgress(
    public val succeeded: Int,
    public val failed: Int,
    public val total: Int,
)

/**
 * 媒体库变化。SDK 不因为这个事件自己再扫一遍。
 *
 * @property assetId 变化的照片。
 * @property kind 增、改或删。
 */
public data class ChangeEvent(
    public val assetId: Long,
    public val kind: ChangeKind,
)
