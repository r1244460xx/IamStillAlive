package com.imsa.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imsa.app.ui.MainUiState
import com.imsa.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: MainUiState,
    onCheckIn: () -> Unit = {},
    onRefresh: () -> Unit,
    onTriggerWorkManager: () -> Unit = {},
    onLogout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("IMSA 健在守護", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "你好，${state.nickname} (${state.phone})",
                            fontSize = 12.sp,
                            color = Slate500
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, contentDescription = "切換帳號")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate50)
            )
        },
        containerColor = Slate50
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. 安全狀態與解鎖守護核心面板
            val isAlerted = state.safetyStatus == "ALERTED"
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAlerted) AlertRedBg else SafeGreenBg
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(if (isAlerted) AlertRed else PrimaryGreen)
                    ) {
                        Icon(
                            imageVector = if (isAlerted) Icons.Default.Warning else Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isAlerted) "⚠️ 安全警報已觸發" else "平安守護中",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = if (isAlerted) AlertRed else PrimaryGreenDark
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isAlerted)
                            "已超過 24 小時未偵測到手機解鎖！緊急聯絡人已收到通報。"
                        else
                            "免手動操作・每次解鎖螢幕自動向守護中心報平安",
                        fontSize = 13.sp,
                        color = Slate700,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. 下次檢測截止時間卡片
            if (!state.nextDeadline.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate100)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, null, tint = Slate500, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("下次打卡截止時間", fontSize = 13.sp, color = Slate700)
                        }
                        Text(
                            text = state.nextDeadline.replace("T", " ").substringBeforeLast(":"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Slate900
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 3. 緊急聯絡人資訊
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Icon(Icons.Default.Shield, null, tint = Slate500, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "緊急通報電話：${state.emergencyContact ?: "尚未設定"}",
                    fontSize = 12.sp,
                    color = Slate500
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. 無感守護狀態卡片 (Accessibility & Battery Optimization)
            val context = androidx.compose.ui.platform.LocalContext.current
            var isAccEnabled by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(com.imsa.app.util.GuardianPermissionHelper.isAccessibilityServiceEnabled(context))
            }
            var isBatteryExempt by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(com.imsa.app.util.GuardianPermissionHelper.isIgnoringBatteryOptimizations(context))
            }

            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        isAccEnabled = com.imsa.app.util.GuardianPermissionHelper.isAccessibilityServiceEnabled(context)
                        isBatteryExempt = com.imsa.app.util.GuardianPermissionHelper.isIgnoringBatteryOptimizations(context)
                        onRefresh()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Slate100)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isAccEnabled) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (isAccEnabled) PrimaryGreenDark else AlertRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("解鎖心跳守護 (無通知常駐)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate900)
                        }
                        if (isAccEnabled) {
                            Text("已常駐運行", fontSize = 11.sp, color = PrimaryGreenDark, fontWeight = FontWeight.Bold)
                        } else {
                            TextButton(
                                onClick = {
                                    com.imsa.app.util.GuardianPermissionHelper.openAccessibilitySettings(context)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("前往開啟 ➔", fontSize = 11.sp, color = AccentBlue)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isBatteryExempt) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (isBatteryExempt) PrimaryGreenDark else Slate500,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("電池最佳化豁免 (抗殺進程)", fontSize = 12.sp, color = Slate700)
                        }
                        if (isBatteryExempt) {
                            Text("已豁免", fontSize = 11.sp, color = PrimaryGreenDark)
                        } else {
                            TextButton(
                                onClick = {
                                    com.imsa.app.util.GuardianPermissionHelper.requestIgnoreBatteryOptimizations(context)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("設定豁免 ➔", fontSize = 11.sp, color = AccentBlue)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 5. 最近打卡歷史紀錄
            Text(
                text = "近期打卡紀錄",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Slate900,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            if (state.recentRecords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暫無打卡紀錄，日常解鎖手機將自動為您完成打卡！", fontSize = 13.sp, color = Slate500)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.recentRecords) { record ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = record.remark ?: "平安打卡",
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = Slate900
                                    )
                                    Text(
                                        text = record.deviceInfo ?: "Android 裝置",
                                        fontSize = 11.sp,
                                        color = Slate500
                                    )
                                }
                                Text(
                                    text = record.loginTime.replace("T", " ").substringBeforeLast("."),
                                    fontSize = 12.sp,
                                    color = Slate500
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
