/**
 * 一行照片：缩略图、两行字，以及可选的操作。
 *
 * @file
 */
package com.arturoyi.albumcleaner.sample.futures.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arturoyi.albumcleaner.sample.theme.Indicator
import com.arturoyi.albumcleaner.sample.theme.Ink
import com.arturoyi.albumcleaner.sample.theme.LineButton
import com.arturoyi.albumcleaner.sample.theme.Muted
import com.arturoyi.albumcleaner.sample.theme.cardSurface

/**
 * 画出一行。
 *
 * @param title 主要的一句。
 * @param detail 下面的说明。
 * @param image 缩略图。还没解出来时为 null，只留一块底。
 * @param action 右侧按钮文字。没有操作时为 null。
 * @param onAction 按下右侧按钮。
 * @param enabled 按钮能不能点。
 */
@Composable
internal fun MediaRow(
    title: String,
    detail: String,
    image: ImageBitmap?,
    action: String?,
    onAction: (() -> Unit)?,
    enabled: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .cardSurface()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Indicator),
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Column(Modifier.padding(start = 8.dp).weight(1f)) {
            Text(
                text = title,
                color = Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = detail,
                color = Muted,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (action != null && onAction != null) {
            LineButton(
                text = action,
                onClick = onAction,
                enabled = enabled,
                horizontalPadding = 12.dp,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
