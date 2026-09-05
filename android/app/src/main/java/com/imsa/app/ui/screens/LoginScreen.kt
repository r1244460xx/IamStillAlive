package com.imsa.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imsa.app.ui.MainUiState
import com.imsa.app.ui.theme.*

@Composable
fun LoginScreen(
    state: MainUiState,
    onLogin: (phone: String, pass: String) -> Unit,
    onNavigateToRegister: () -> Unit,
    onClearError: () -> Unit = {},
    onUpdateServerUrl: (String) -> Unit = {}
) {
    var phone by remember { mutableStateOf("0912345678") }
    var password by remember { mutableStateOf("pass123456") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var inputServerUrl by remember { mutableStateOf(state.serverUrl) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(state.serverUrl) {
        inputServerUrl = state.serverUrl
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // 伺服器網址狀態膠囊 (點擊可快速切換/自訂)
        Surface(
            color = Slate100,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable { showServerDialog = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = Slate500,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = state.serverUrl,
                    fontSize = 12.sp,
                    color = Slate700,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = Slate500,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 品牌圖示與主標題
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(LightGreenBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.HealthAndSafety,
                contentDescription = null,
                tint = PrimaryGreen,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "IMSA 健在守護",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Slate900
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "單身人士安心守護與每日平安打卡",
            fontSize = 14.sp,
            color = Slate500
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 登入卡片容器
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "會員登入",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate900
                )

                Text(
                    text = "請輸入綁定手機與密碼以進入守護中心",
                    fontSize = 13.sp,
                    color = Slate500
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 手機輸入框
                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it
                        if (state.isError) onClearError()
                    },
                    label = { Text("手機號碼") },
                    placeholder = { Text("0912345678") },
                    leadingIcon = {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = PrimaryGreen)
                    },
                    singleLine = true,
                    isError = state.isError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryGreen,
                        unfocusedBorderColor = BorderLight
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 密碼輸入框
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        if (state.isError) onClearError()
                    },
                    label = { Text("登入密碼") },
                    placeholder = { Text("••••••••") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = PrimaryGreen)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "隱藏密碼" else "顯示密碼",
                                tint = Slate400
                            )
                        }
                    },
                    singleLine = true,
                    isError = state.isError,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (phone.isNotBlank() && password.isNotBlank()) {
                                onLogin(phone.trim(), password)
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryGreen,
                        unfocusedBorderColor = BorderLight
                    )
                )

                // 登入錯誤 Chip 警告提示
                if (state.errorMessage != null && !state.isPhoneConflict) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = AlertRedBg,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = AlertRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.errorMessage,
                                color = AlertRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 登入按鈕
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onLogin(phone.trim(), password)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                    enabled = !state.isLoading && phone.isNotBlank() && password.isNotBlank()
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("登入並啟動守護", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 分隔與切換至註冊
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = Slate200)
            Text(
                text = "新朋友初次使用？",
                fontSize = 13.sp,
                color = Slate500,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = Slate200)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 跳轉註冊按鈕
        OutlinedButton(
            onClick = onNavigateToRegister,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGreen),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = androidx.compose.ui.graphics.SolidColor(PrimaryGreen))
        ) {
            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("立即註冊新帳號", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // 伺服器網址配置彈窗
    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("伺服器連線配置", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "請輸入後端 API 伺服器網址（實體手機請填寫電腦區網 IP）：",
                        fontSize = 13.sp,
                        color = Slate500
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputServerUrl,
                        onValueChange = { inputServerUrl = it },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "快速選擇：", fontSize = 12.sp, color = Slate700, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SuggestionChip(
                            onClick = { inputServerUrl = "http://192.168.0.137:8080/" },
                            label = { Text("本機區網 (192.168.0.137)") }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SuggestionChip(
                            onClick = { inputServerUrl = "http://10.0.2.2:8080/" },
                            label = { Text("模擬器 (10.0.2.2)") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateServerUrl(inputServerUrl.trim())
                        showServerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text("儲存套用")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("取消", color = Slate500)
                }
            }
        )
    }
}
