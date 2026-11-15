package com.sevenx.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Night = Color(0xFF090A10)
private val Panel = Color(0xFF141622)
private val Violet = Color(0xFFAA96FF)
private val Muted = Color(0xFFA1A3B3)
private val Foreground = Color(0xFFF5F3FF)

@Composable
fun AppRoot() {
    val palette = darkColorScheme(
        primary = Violet,
        onPrimary = Color(0xFF17112B),
        background = Night,
        onBackground = Foreground,
        surface = Panel,
        onSurface = Foreground,
        secondary = Color(0xFF7BC9B7),
    )

    MaterialTheme(colorScheme = palette) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 30.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "7xAI",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp,
                    )
                    Surface(
                        color = Color(0xFF211D32),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = "ON-DEVICE GATEWAY",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            color = Violet,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        text = "One gateway.\nYour choice of AI.",
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.8).sp,
                    )
                    Text(
                        text = "A private AI workspace powered by OmniRoute.",
                        color = Muted,
                        fontSize = 14.sp,
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = Panel),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF797B8B)),
                            )
                            Text(
                                text = "GATEWAY STATUS",
                                color = Muted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                            )
                        }

                        Text(
                            text = "Gateway stopped",
                            modifier = Modifier.testTag("gateway_status"),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "The local OmniRoute engine has not been installed or started yet.",
                            color = Muted,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                        )

                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = Color(0xFF2A2639),
                                disabledContentColor = Color(0xFFBEB2E9),
                            ),
                        ) {
                            Text(
                                text = "Gateway setup is in progress",
                                modifier = Modifier.padding(vertical = 5.dp),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "BUILT FOR YOUR PHONE",
                        color = Violet,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    )
                    Text(
                        text = "Your phone hosts the gateway. Model responses come from the AI providers you configure.",
                        color = Muted,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "7xAI  •  PRIVATE BY DEFAULT",
                    color = Color(0xFF666879),
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}
