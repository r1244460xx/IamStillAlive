package com.imsa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import com.imsa.app.ui.screens.ChangeEmergencyContactScreen
import com.imsa.app.ui.screens.ChangePasswordScreen
import com.imsa.app.ui.screens.HomeScreen
import com.imsa.app.ui.screens.LoginScreen
import com.imsa.app.ui.screens.RegisterScreen
import com.imsa.app.ui.theme.IMSATheme

enum class AuthScreen {
    LOGIN,
    REGISTER
}

enum class MainScreen {
    HOME,
    CHANGE_PASSWORD,
    CHANGE_EMERGENCY_CONTACT
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
                var currentMainScreen by remember { mutableStateOf(MainScreen.HOME) }

                LaunchedEffect(state.message) {
                    state.message?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearMessage()
                    }
                }

                BackHandler(enabled = state.isLoggedIn && currentMainScreen != MainScreen.HOME) {
                    viewModel.clearError()
                    currentMainScreen = MainScreen.HOME
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { _ ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (state.isLoggedIn) {
                            when (currentMainScreen) {
                                MainScreen.HOME -> {
                                    HomeScreen(
                                        state = state,
                                        onRefresh = { viewModel.refreshData() },
                                        onNavigateToChangePassword = {
                                            viewModel.clearError()
                                            currentMainScreen = MainScreen.CHANGE_PASSWORD
                                        },
                                        onNavigateToChangeEmergencyContact = {
                                            viewModel.clearError()
                                            currentMainScreen = MainScreen.CHANGE_EMERGENCY_CONTACT
                                        },
                                        onLogout = {
                                            viewModel.logout()
                                            currentAuthScreen = AuthScreen.LOGIN
                                            currentMainScreen = MainScreen.HOME
                                        }
                                    )
                                }
                                MainScreen.CHANGE_PASSWORD -> {
                                    ChangePasswordScreen(
                                        state = state,
                                        onChangePassword = { oldPass, newPass ->
                                            viewModel.changePassword(oldPass, newPass) {
                                                currentMainScreen = MainScreen.HOME
                                            }
                                        },
                                        onNavigateBack = {
                                            viewModel.clearError()
                                            currentMainScreen = MainScreen.HOME
                                        },
                                        onClearError = { viewModel.clearError() }
                                    )
                                }
                                MainScreen.CHANGE_EMERGENCY_CONTACT -> {
                                    ChangeEmergencyContactScreen(
                                        state = state,
                                        onUpdateEmergencyContact = { newPhone ->
                                            viewModel.updateEmergencyContact(newPhone) {
                                                currentMainScreen = MainScreen.HOME
                                            }
                                        },
                                        onNavigateBack = {
                                            viewModel.clearError()
                                            currentMainScreen = MainScreen.HOME
                                        },
                                        onClearError = { viewModel.clearError() }
                                    )
                                }
                            }
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
                                        onClearError = { viewModel.clearError() },
                                        onUpdateServerUrl = { viewModel.updateServerUrl(it) }
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
