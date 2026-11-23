/**
 * 按位滚动的数字。没变的那一位停在原地。
 *
 * 个位永远在最右边。10 变成 19 时，十位的 1 不播放，只有个位从 0 换到 9。
 *
 * @file
 */
package com.arturoyi.fastdownload.sample.futures.tasks

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/** 一位数字滚上去或滚下来的时间。 */
private const val ROLL_MILLIS: Int = 200

/**
 * 从右边对齐的数字。单位和空格不要放进来，那些不滚动。
 *
 * 槽位从第一帧就占好。后来才出现的高位会滚进来，已经相同的字符不播放。
 *
 * @param text 当前数字，例如 `19` 或 `1.2`。
 * @param places 预留的位数。短于这个长度时，左边是空的。
 * @param color 字色。
 * @param fontSize 字号。
 * @param modifier 外边距。
 * @param fontWeight 字重。不传就用默认。
 */
@Composable
internal fun RollingDigits(
    text: String,
    places: Int,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
) {
    val digitWidth = rememberDigitWidth(fontSize, fontWeight)
    val count = maxOf(places, text.length)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        for (place in count - 1 downTo 0) {
            val index = text.lastIndex - place
            val char = if (index in text.indices) text[index] else null
            key(place) {
                RollingChar(
                    char = char,
                    color = color,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    digitWidth = digitWidth,
                )
            }
        }
    }
}

@Composable
private fun rememberDigitWidth(fontSize: TextUnit, fontWeight: FontWeight?): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val weight = fontWeight ?: FontWeight.Normal
    return remember(measurer, density, fontSize, weight) {
        val style = TextStyle(fontSize = fontSize, fontWeight = weight)
        val widest = ('0'..'9').maxOf { digit ->
            measurer.measure(
                text = digit.toString(),
                style = style,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            ).size.width
        }
        with(density) { widest.toDp() }
    }
}

@Composable
private fun RollingChar(
    char: Char?,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight?,
    digitWidth: Dp,
) {
    AnimatedContent(
        targetState = char,
        modifier = Modifier.clipToBounds(),
        transitionSpec = { roll(initialState, targetState) },
        contentAlignment = Alignment.Center,
        label = "digit",
    ) { value ->
        when {
            value == null -> Spacer(Modifier.size(0.dp))
            value.isDigit() -> Box(Modifier.width(digitWidth), contentAlignment = Alignment.Center) {
                DigitText(value, color, fontSize, fontWeight)
            }
            else -> DigitText(value, color, fontSize, fontWeight)
        }
    }
}

@Composable
private fun DigitText(
    char: Char,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight?,
) {
    Text(
        text = char.toString(),
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}

/** 变大的数字向上滚，变小的向下滚。新出现或消失的一位也跟着滚。 */
private fun roll(from: Char?, to: Char?): ContentTransform {
    val upward = when {
        from != null && to != null && from.isDigit() && to.isDigit() -> to >= from
        else -> true
    }
    val enter = if (upward) {
        slideInVertically(tween(ROLL_MILLIS)) { height -> height } + fadeIn(tween(ROLL_MILLIS))
    } else {
        slideInVertically(tween(ROLL_MILLIS)) { height -> -height } + fadeIn(tween(ROLL_MILLIS))
    }
    val exit = if (upward) {
        slideOutVertically(tween(ROLL_MILLIS)) { height -> -height } + fadeOut(tween(ROLL_MILLIS))
    } else {
        slideOutVertically(tween(ROLL_MILLIS)) { height -> height } + fadeOut(tween(ROLL_MILLIS))
    }
    return ContentTransform(
        targetContentEnter = enter,
        initialContentExit = exit,
        sizeTransform = SizeTransform(clip = true),
    )
}
