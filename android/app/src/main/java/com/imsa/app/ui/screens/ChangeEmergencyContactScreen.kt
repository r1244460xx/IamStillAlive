package com.imsa.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imsa.app.ui.MainUiState
import com.imsa.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeEmergencyContactScreen(
    state: MainUiState,
    onUpdateEmergencyContact: (newPhone: String) -> Unit,
    onNavigateBack: () -> Unit,
    onClearError: () -> Unit = {}
) {
    var emergencyPhone by remember { mutableStateOf(state.emergencyContact ?: "") }
    val focusManager = LocalFocusManager.current

    val phoneRegex = Regex("^09\\d{8}$")
    val isValidFormat = phoneRegex.matches(emergencyPhone.trim())
    val isSameAsSelf = state.phone.isNotBlank() && emergencyPhone.trim() == state.phone.trim()
    val isFormValid = isValidFormat && !isSameAsSelf && emergencyPhone.trim() != (state.emergencyContact ?: "")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("修改緊急聯絡人", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Slate900)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Slate700
                        )
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
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(LightGreenBackground)
            ) {
                Icon(
                    imageVector = Icons.Default.ContactPhone,
                    contentDescription = null,
                    tint = PrimaryGreenDark,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "設定緊急通報對象",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Slate900
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "當超過 24 小時未偵測到您的手機活動時，守護中心將第一時間通知此緊急聯絡人以保障安全。",
                fontSize = 13.sp,
                color = Slate500,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (state.isError && !state.errorMessage.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AlertRedBg),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AlertRed, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.errorMessage,
                            color = AlertRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Slate100)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = PrimaryGreenDark, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("目前綁定電話", fontSize = 12.sp, color = Slate500)
                        Text(
                            text = state.emergencyContact?.takeIf { it.isNotBlank() } ?: "尚未設定",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "新緊急聯絡人手機號碼",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate900
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = emergencyPhone,
                        onValueChange = { input ->
                            if (input.length <= 10 && input.all { it.isDigit() }) {
                                emergencyPhone = input
                                onClearError()
                            }
                        },
                        placeholder = { Text("例：0987654321") },
                        leadingIcon = {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = Slate400)
                        },
                        trailingIcon = {
                            if (isValidFormat) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryGreenDark)
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (isFormValid && !state.isLoading) {
                                    onUpdateEmergencyContact(emergencyPhone.trim())
                                }
                            }
                        ),
                        singleLine = true,
                        isError = (emergencyPhone.isNotEmpty() && !isValidFormat) || isSameAsSelf,
                        supportingText = {
                            if (isSameAsSelf) {
                                Text("⚠️ 緊急聯絡人不可為本人之手機號碼", color = AlertRed, fontSize = 12.sp)
                            } else if (emergencyPhone.isNotEmpty() && !isValidFormat) {
                                Text("⚠️ 請輸入 09 開頭之 10 碼台灣手機號碼", color = AlertRed, fontSize = 12.sp)
                            } else if (isValidFormat) {
                                Text("✅ 門號格式正確", color = PrimaryGreenDark, fontSize = 12.sp)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryGreenDark,
                            unfocusedBorderColor = BorderLight,
                            errorBorderColor = AlertRed
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    focusManager.clearFocus()
                    onUpdateEmergencyContact(emergencyPhone.trim())
                },
                enabled = isFormValid && !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryGreen,
                    disabledContainerColor = Slate200
                )
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "確認儲存修改",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isFormValid) Color.White else Slate400
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate700)
            ) {
                Text("取消返回", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
