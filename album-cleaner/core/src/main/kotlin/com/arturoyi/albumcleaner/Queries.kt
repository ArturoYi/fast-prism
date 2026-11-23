/**
 * 候选、分组和回收站的分页条件。
 *
 * @file
 */
package com.arturoyi.albumcleaner

/**
 * 只按页翻的查询。
 *
 * @property cursor 上一页的 [PagedResult.nextCursor]。第一页为 null。
 * @property limit 这一页最多多少条。
 */
public data class PageQuery(
    public val cursor: String? = null,
    public val limit: Int = 50,
)

/**
 * 候选列表的过滤和分页。
 *
 * @property confidence 只看这个把握等级。null 表示不限。
 * @property reason 只看包含这个原因的候选。null 表示不限。
 * @property cursor 上一页的游标。第一页为 null。
 * @property limit 这一页最多多少条。
 */
public data class CandidateQuery(
    public val confidence: ConfidenceLevel? = null,
    public val reason: ReasonCode? = null,
    public val cursor: String? = null,
    public val limit: Int = 50,
)

/**
 * 分组列表的过滤和分页。
 *
 * @property type 只看这一种组。null 表示不限。
 * @property cursor 上一页的游标。第一页为 null。
 * @property limit 这一页最多多少条。
 */
public data class GroupQuery(
    public val type: GroupType? = null,
    public val cursor: String? = null,
    public val limit: Int = 50,
)
