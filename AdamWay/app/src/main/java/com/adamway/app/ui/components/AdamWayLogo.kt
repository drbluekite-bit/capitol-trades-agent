package com.adamway.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamway.app.ui.theme.AdamWayBlack
import com.adamway.app.ui.theme.AdamWayWhite

/** The black-tile "AW" monogram used on the home screen, matching the launcher icon. */
@Composable
fun AdamWayLogo(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 96.dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(AdamWayBlack, RoundedCornerShape(size / 5))
            .background(AdamWayBlack),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "AW",
            color = AdamWayWhite,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.4).sp,
        )
    }
}
