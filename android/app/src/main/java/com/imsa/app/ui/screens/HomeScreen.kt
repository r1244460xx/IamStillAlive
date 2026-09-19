package com.imsa.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imsa.app.data.EmergencyContactResponse
import com.imsa.app.ui.MainUiState
import com.imsa.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: MainUiState,
    onRefresh: () -> Unit,
    onNavigateToChangePassword: () -> Unit = {},
    onNavigateToChangeEmergencyContact: () -> Unit = {},
    onAddEmergencyContact: (name: String, phone: String) -> Unit = { _, _ -> },
    onUpdateEmergencyContact: (contactId: String, name: String, phone: String) -> Unit = { _, _, _ -> },
    onDeleteEmergencyContact: (contactId: String) -> Unit = {},
    onLogout: () -> Unit
) {
    val isOffline = state.isDisconnected
    val phoneRegex = remember { Regex("^09\\d{8}$") }

    // 對話框狀態
    var showAddDialog by remember { mutableStateOf(false) }
    var newContactName by remember { mutableStateOf("") }
    var newContactPhone by remember { mutableStateOf("") }

    var showEditDialog by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<EmergencyContactResponse?>(null) }
    var editContactName by remember { mutableStateOf("") }
    var editContactPhone by remember { mutableStateOf("") }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletingContact by remember { mutableStateOf<EmergencyContactResponse?>(null) }

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

            val cardBg = when {
                isOffline -> WarningYellowBg
                isAlerted -> AlertRedBg
                else -> SafeGreenBg
            }
            val circleBg = when {
                isOffline -> WarningYellow
                isAlerted -> AlertRed
                else -> PrimaryGreen
            }
            val titleColor = when {
                isOffline -> WarningYellowDark
                isAlerted -> AlertRed
                else -> PrimaryGreenDark
            }
            val circleIcon = when {
                isOffline -> Icons.Default.CloudOff
                isAlerted -> Icons.Default.Warning
                else -> Icons.Default.Shield
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(circleBg)
                    ) {
                        Icon(
                            imageVector = circleIcon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = when {
                            isOffline -> "斷線無法上傳"
                            isAlerted -> "⚠️ 安全警報已觸發"
                            else -> "平安守護中"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = titleColor
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = when {
                            isOffline -> "目前心跳 API 無法連線，打卡記錄無法上傳。\n已在手機保留最近一次解鎖，連網時將立即補傳。"
                            isAlerted -> "已超過 24 小時未偵測到手機解鎖！所有緊急聯絡人已收到群發通報。"
                            else -> "免手動操作・每次解鎖螢幕自動向守護中心報平安"
                        },
                        fontSize = 12.sp,
                        color = Slate700,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

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
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, null, tint = Slate500, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("下次打卡截止時間", fontSize = 12.sp, color = Slate700)
                        }
                        Text(
                            text = state.nextDeadline.replace("T", " ").substringBeforeLast(":"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Slate900
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 3. 緊急聯絡人管理卡片 (CRUD)
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
                            Icon(Icons.Default.Shield, null, tint = PrimaryGreenDark, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "緊急聯絡人名單 (${state.emergencyContacts.size} 位)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                        }

                        if (!isOffline) {
                            TextButton(
                                onClick = {
                                    newContactName = ""
                                    newContactPhone = ""
                                    showAddDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(15.dp), tint = PrimaryGreenDark)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("＋ 新增", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryGreenDark)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (state.emergencyContacts.isEmpty()) {
                        Text("尚未設定緊急聯絡人", fontSize = 12.sp, color = Slate400, modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        state.emergencyContacts.forEachIndexed { index, contact ->
                            if (index > 0) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Slate200)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        color = PrimaryGreen.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "#${index + 1}",
                                            fontSize = 10.sp,
                                            color = PrimaryGreenDark,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = contact.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Slate900
                                        )
                                        Text(
                                            text = contact.phone,
                                            fontSize = 12.sp,
                                            color = Slate500
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            editingContact = contact
                                            editContactName = contact.name
                                            editContactPhone = contact.phone
                                            showEditDialog = true
                                        },
                                        enabled = !isOffline,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "編輯聯絡人",
                                            tint = if (isOffline) Slate400 else Slate700,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    val canDelete = state.emergencyContacts.size > 1 && !isOffline
                                    IconButton(
                                        onClick = {
                                            deletingContact = contact
                                            showDeleteDialog = true
                                        },
                                        enabled = canDelete,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = if (canDelete) "刪除聯絡人" else "至少保留一位",
                                            tint = if (canDelete) AlertRed else Slate400,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (state.emergencyContacts.size == 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "💡 防護防呆：至少需保留 1 位緊急聯絡人，無法刪除唯一個人選。",
                            fontSize = 10.sp,
                            color = Slate500
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. 無感守護狀態卡片
            val context = androidx.compose.ui.platform.LocalContext.current
            var isAccEnabled by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(com.imsa.app.util.GuardianPermissionHelper.isAccessibilityServiceEnabled(context))
            }
            var isBatteryExempt by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(com.imsa.app.util.GuardianPermissionHelper.isIgnoringBatteryOptimizations(context))
            }
            var isAutoStartEnabled by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(com.imsa.app.util.GuardianPermissionHelper.isAutoStartEnabled(context))
            }

            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        isAccEnabled = com.imsa.app.util.GuardianPermissionHelper.isAccessibilityServiceEnabled(context)
                        isBatteryExempt = com.imsa.app.util.GuardianPermissionHelper.isIgnoringBatteryOptimizations(context)
                        isAutoStartEnabled = com.imsa.app.util.GuardianPermissionHelper.isAutoStartEnabled(context)
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
                    // 項目 1: 無障礙心跳守護
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isAccEnabled) "已常駐" else "未開啟",
                                fontSize = 11.sp,
                                color = if (isAccEnabled) PrimaryGreenDark else AlertRed,
                                fontWeight = if (isAccEnabled) FontWeight.Bold else FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            TextButton(
                                onClick = {
                                    com.imsa.app.util.GuardianPermissionHelper.openAccessibilitySettings(context)
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = if (isAccEnabled) "前往設定 ➔" else "前往開啟 ➔",
                                    fontSize = 11.sp,
                                    color = AccentBlue
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 項目 2: 自啟動 / 後台啟動
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (isAutoStartEnabled) {
                                    true -> Icons.Default.CheckCircle
                                    false -> Icons.Default.Info
                                    null -> Icons.Default.Info
                                },
                                contentDescription = null,
                                tint = when (isAutoStartEnabled) {
                                    true -> PrimaryGreenDark
                                    false -> AlertRed
                                    null -> Slate500
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("自啟動 / 後台啟動 (防殺常駐)", fontSize = 12.sp, color = Slate700)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = when (isAutoStartEnabled) {
                                    true -> "已開啟"
                                    false -> "未開啟"
                                    null -> "前往確認"
                                },
                                fontSize = 11.sp,
                                color = when (isAutoStartEnabled) {
                                    true -> PrimaryGreenDark
                                    false -> AlertRed
                                    null -> Slate500
                                },
                                fontWeight = if (isAutoStartEnabled == true) FontWeight.Bold else FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            TextButton(
                                onClick = {
                                    com.imsa.app.util.GuardianPermissionHelper.openAutoStartSettings(context)
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = if (isAutoStartEnabled == true) "前往設定 ➔" else "前往開啟 ➔",
                                    fontSize = 11.sp,
                                    color = AccentBlue
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 項目 3: 電池最佳化豁免
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
                            Text("電池最佳化豁免 (省電策略無限制)", fontSize = 12.sp, color = Slate700)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isBatteryExempt) "已豁免" else "未豁免",
                                fontSize = 11.sp,
                                color = if (isBatteryExempt) PrimaryGreenDark else Slate500,
                                fontWeight = if (isBatteryExempt) FontWeight.Bold else FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            TextButton(
                                onClick = {
                                    if (isBatteryExempt) {
                                        com.imsa.app.util.GuardianPermissionHelper.openBatteryOptimizationSettings(context)
                                    } else {
                                        com.imsa.app.util.GuardianPermissionHelper.requestIgnoreBatteryOptimizations(context)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = if (isBatteryExempt) "前往設定 ➔" else "設定豁免 ➔",
                                    fontSize = 11.sp,
                                    color = AccentBlue
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5. 守護設定快捷列
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Slate100)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .then(
                                if (!isOffline) Modifier.clickable { onNavigateToChangePassword() }
                                else Modifier
                            )
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LockReset, 
                            null, 
                            tint = if (isOffline) Slate400 else Slate700, 
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isOffline) "修改登入密碼 (離線禁用)" else "修改登入密碼 ➔",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isOffline) Slate400 else Slate700
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 6. 最近打卡歷史紀錄
            Text(
                text = "近期打卡紀錄",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Slate900,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )

            val hasRecords = state.recentRecords.isNotEmpty() || state.pendingCheckIn != null
            if (!hasRecords) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("日常解鎖手機將自動為您完成打卡！", fontSize = 13.sp, color = Slate500)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.pendingCheckIn?.let { pending ->
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = WarningYellowBg)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "📱 螢幕解鎖 (待連網補傳)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = WarningYellowDark
                                        )
                                        Text(
                                            text = "手機端暫存最新一筆・連網時立即補傳",
                                            fontSize = 11.sp,
                                            color = Slate700
                                        )
                                    }
                                    Text(
                                        text = pending.timestamp.replace("T", " ").substringBeforeLast("."),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = WarningYellowDark
                                    )
                                }
                            }
                        }
                    }

                    items(state.recentRecords) { record ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = record.remark ?: "平安打卡",
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
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
                                    fontSize = 11.sp,
                                    color = Slate500
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // 對話框 1: 新增緊急聯絡人 Dialog
    // ==========================================
    if (showAddDialog) {
        val isPhoneValid = phoneRegex.matches(newContactPhone.trim())
        val isNotSelf = state.phone.isBlank() || newContactPhone.trim() != state.phone.trim()
        val isNotDuplicate = state.emergencyContacts.none { it.phone.trim() == newContactPhone.trim() }
        val isAddValid = newContactName.isNotBlank() && isPhoneValid && isNotSelf && isNotDuplicate

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("新增緊急聯絡人", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newContactName,
                        onValueChange = { newContactName = it },
                        label = { Text("稱呼 / 姓名 (必填)") },
                        placeholder = { Text("例如: 父親、李專員") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newContactPhone,
                        onValueChange = { newContactPhone = it },
                        label = { Text("手機號碼 (必填)") },
                        placeholder = { Text("09xxxxxxxx") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = newContactPhone.isNotBlank() && (!isPhoneValid || !isNotSelf || !isNotDuplicate),
                        supportingText = {
                            if (newContactPhone.isNotBlank()) {
                                if (!isNotSelf) {
                                    Text("⚠️ 不可設定本人手機號碼", color = AlertRed, fontSize = 11.sp)
                                } else if (!isNotDuplicate) {
                                    Text("⚠️ 此手機號碼已存在名單中", color = AlertRed, fontSize = 11.sp)
                                } else if (!isPhoneValid) {
                                    Text("⚠️ 請輸入 09 開頭之 10 碼格式", color = AlertRed, fontSize = 11.sp)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddEmergencyContact(newContactName, newContactPhone)
                        showAddDialog = false
                    },
                    enabled = isAddValid,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text("新增")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("取消", color = Slate500)
                }
            }
        )
    }

    // ==========================================
    // 對話框 2: 編輯緊急聯絡人 Dialog
    // ==========================================
    if (showEditDialog && editingContact != null) {
        val isPhoneValid = phoneRegex.matches(editContactPhone.trim())
        val isNotSelf = state.phone.isBlank() || editContactPhone.trim() != state.phone.trim()
        val isNotDuplicate = state.emergencyContacts.none { it.id != editingContact?.id && it.phone.trim() == editContactPhone.trim() }
        val isEditValid = editContactName.isNotBlank() && isPhoneValid && isNotSelf && isNotDuplicate

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("編輯緊急聯絡人", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editContactName,
                        onValueChange = { editContactName = it },
                        label = { Text("稱呼 / 姓名 (必填)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editContactPhone,
                        onValueChange = { editContactPhone = it },
                        label = { Text("手機號碼 (必填)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = editContactPhone.isNotBlank() && (!isPhoneValid || !isNotSelf || !isNotDuplicate),
                        supportingText = {
                            if (editContactPhone.isNotBlank()) {
                                if (!isNotSelf) {
                                    Text("⚠️ 不可設定本人手機號碼", color = AlertRed, fontSize = 11.sp)
                                } else if (!isNotDuplicate) {
                                    Text("⚠️ 此手機號碼已存在名單中", color = AlertRed, fontSize = 11.sp)
                                } else if (!isPhoneValid) {
                                    Text("⚠️ 請輸入 09 開頭之 10 碼格式", color = AlertRed, fontSize = 11.sp)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        editingContact?.let {
                            onUpdateEmergencyContact(it.id, editContactName, editContactPhone)
                        }
                        showEditDialog = false
                    },
                    enabled = isEditValid,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text("儲存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("取消", color = Slate500)
                }
            }
        )
    }

    // ==========================================
    // 對話框 3: 刪除確認 Dialog
    // ==========================================
    if (showDeleteDialog && deletingContact != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("確認刪除緊急聯絡人", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "確定要刪除「${deletingContact?.name} (${deletingContact?.phone})」嗎？刪除後若觸發警報將不再向其發送簡訊。",
                    fontSize = 14.sp,
                    color = Slate700
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        deletingContact?.let {
                            onDeleteEmergencyContact(it.id)
                        }
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("確認刪除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消", color = Slate500)
                }
            }
        )
    }
}
