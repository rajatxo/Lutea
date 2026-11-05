package com.rajatxo.lutea.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.lutea.data.prefs.PlaylistStyle
import com.rajatxo.lutea.data.prefs.PlaylistStyleManager
import com.rajatxo.lutea.ui.components.CalSansFamily

/**
 * Playlist style picker dialog — lets the user switch between
 * "Wheel Simpler" (original) and "Wheel Advanced" (new experimental).
 */
@Composable
fun PlaylistStylePickerDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val currentStyle by PlaylistStyleManager.state

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Playlist Style",
                color = Color.White,
                fontFamily = CalSansFamily,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column {
                PlaylistStyle.entries.forEach { style ->
                    val isSelected = style == currentStyle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) Color.White.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    PlaylistStyleManager.setStyle(style, context)
                                    onDismiss()
                                },
                            )
                            .padding(vertical = 14.dp, horizontal = 12.dp),
                    ) {
                        Column {
                            Text(
                                text = style.label,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontFamily = CalSansFamily,
                            )
                            val desc = when (style) {
                                PlaylistStyle.WHEEL_SIMPLER -> "Original wheel structure"
                                PlaylistStyle.WHEEL_ADVANCED -> "Bigger text, experimental layout"
                            }
                            Text(
                                text = desc,
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}
