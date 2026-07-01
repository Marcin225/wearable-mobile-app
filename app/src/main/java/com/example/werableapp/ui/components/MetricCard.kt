package com.example.werableapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
* Card displaying a single wearable metric
*/
@Composable
fun MetricCard(
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(140.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161A23))
    ) {
        Column(
            modifier = modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon (
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = modifier.size(32.dp)
            )
            Column {
                Row (
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = title,
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Spacer(
                        modifier = modifier.width(4.dp)
                    )
                    Text(
                        text = value,
                        color = Color.White,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}