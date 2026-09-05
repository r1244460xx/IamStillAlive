package com.imsa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.imsa.app.ui.MainViewModel
import com.imsa.app.ui.screens.HomeScreen
import com.imsa.app.ui.screens.LoginScreen
import com.imsa.app.ui.screens.RegisterScreen
import com.imsa.app.ui.theme.IMSATheme

enum class AuthScreen {
    LOGIN,
    REGISTER
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IMSATheme {
                val state by viewModel.uiState.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }
                var currentAuthScreen by remember { mutableStateOf(AuthScreen.LOGIN) }

                LaunchedEffect(state.message) {
                    state.message?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearMessage()
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { _ ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (state.isLoggedIn) {
                            HomeScreen(
                                state = state,
                                onCheckIn = { viewModel.checkIn() },
                                onRefresh = { viewModel.refreshData() },
                                onTriggerWorkManager = { viewModel.triggerBackgroundHeartbeat() },
                                onLogout = {
                                    viewModel.logout()
                                    currentAuthScreen = AuthScreen.LOGIN
                                }
                            )
                        } else {
                            when (currentAuthScreen) {
                                AuthScreen.LOGIN -> {
                                    LoginScreen(
                                        state = state,
                                        onLogin = { phone, pass ->
                                            viewModel.login(phone, pass)
                                        },
                                        onNavigateToRegister = {
                                            viewModel.clearError()
                                            currentAuthScreen = AuthScreen.REGISTER
                                        },
                                        onClearError = { viewModel.clearError() }
                                    )
                                }
                                AuthScreen.REGISTER -> {
                                    RegisterScreen(
                                        state = state,
                                        onRegister = { phone, pass, nickname, emergency ->
                                            viewModel.register(phone, pass, nickname, emergency)
                                        },
                                        onNavigateToLogin = {
                                            viewModel.clearError()
                                            currentAuthScreen = AuthScreen.LOGIN
                                        },
                                        onClearError = { viewModel.clearError() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
