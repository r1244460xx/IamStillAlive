package com.imsa.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imsa.app.ui.MainUiState
import com.imsa.app.ui.theme.*

@Composable
fun RegisterScreen(
    state: MainUiState,
    onRegister: (phone: String, pass: String, nickname: String, emergency: String) -> Unit,
    onNavigateToLogin: () -> Unit,
    onClearError: () -> Unit = {}
) {
    var phone by remember { mutableStateOf("0912345678") }
    var password by remember { mutableStateOf("pass123456") }
    var nickname by remember { mutableStateOf("測試者") }
    var emergencyContact by remember { mutableStateOf("0987654321") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.HealthAndSafety,
            contentDescription = null,
            tint = PrimaryGreen,
            modifier = Modifier.size(72.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "IMSA 健在守護",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Slate900
        )

        Text(
            text = "單身人士安心守護與定時平安打卡",
            fontSize = 14.sp,
            color = Slate500
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 手機號碼輸入框
        OutlinedTextField(
            value = phone,
            onValueChange = {
                phone = it
                if (state.isPhoneConflict || state.isError) onClearError()
            },
            label = { Text("手機號碼 (登入主憑證)") },
            leadingIcon = { Icon(Icons.Default.Phone, null) },
            isError = state.isPhoneConflict,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        // 若手機已被註冊，顯示醒目的警告 Chip / Alert Banner
        if (state.isPhoneConflict) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = AlertRedBg,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = AlertRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.errorMessage ?: "該手機號碼已註冊",
                            color = AlertRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "此手機已擁有帳號，請直接前往登入。",
                            color = Slate700,
                            fontSize = 12.sp
                        )
                    }
                    TextButton(
                        onClick = onNavigateToLogin,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "前往登入 →",
                            color = PrimaryGreenDark,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                if (state.isError) onClearError()
            },
            label = { Text("密碼") },
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = nickname,
            onValueChange = {
                nickname = it
                if (state.isError) onClearError()
            },
            label = { Text("稱呼 / 暱稱") },
            leadingIcon = { Icon(Icons.Default.Person, null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        val isEmergencySameAsPhone = phone.isNotBlank() && emergencyContact.isNotBlank() && emergencyContact.trim() == phone.trim()
        val phoneRegex = Regex("^09\\d{8}$")
        val isEmergencyValidFormat = emergencyContact.isBlank() || phoneRegex.matches(emergencyContact.trim())

        OutlinedTextField(
            value = emergencyContact,
            onValueChange = { emergencyContact = it },
            label = { Text("緊急聯絡人電話 (選填)") },
            placeholder = { Text("例如 0987654321") },
            leadingIcon = { Icon(Icons.Default.Phone, null) },
            isError = isEmergencySameAsPhone || (!isEmergencyValidFormat && emergencyContact.isNotBlank()),
            supportingText = {
                if (isEmergencySameAsPhone) {
                    Text("⚠️ 緊急聯絡人不可為本人手機號碼", color = AlertRed, fontSize = 12.sp)
                } else if (emergencyContact.isNotBlank() && !isEmergencyValidFormat) {
                    Text("⚠️ 請輸入 09 開頭之 10 碼台灣手機號碼", color = AlertRed, fontSize = 12.sp)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = { onRegister(phone, password, nickname, emergencyContact) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
            enabled = !state.isLoading && !isEmergencySameAsPhone && isEmergencyValidFormat
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("啟動守護 (註冊並進入)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = Slate200)
            Text(
                text = "已有帳號？",
                fontSize = 13.sp,
                color = Slate500,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = Slate200)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onNavigateToLogin,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGreen),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = androidx.compose.ui.graphics.SolidColor(PrimaryGreen))
        ) {
            Text("返回登入頁面", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
