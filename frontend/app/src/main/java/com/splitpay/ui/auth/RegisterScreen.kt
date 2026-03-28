package com.splitpay.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.splitpay.viewmodel.AuthUiState
import com.splitpay.viewmodel.AuthViewModel

// ── Brand colors ──────────────────────────────────────────────────────────────
private val RPrimary = Color(0xFF2B348D)
private val RPrimaryContainer = Color(0xFF444DA6)
private val RSurface = Color(0xFFF9F9FC)
private val RSurfaceContainerLowest = Color(0xFFFFFFFF)
private val RSurfaceContainerLow = Color(0xFFF3F3F6)
private val ROnSurface = Color(0xFF1A1C1E)
private val ROnSurfaceVariant = Color(0xFF3F4949)
private val ROutlineVariant = Color(0xFFBEC8C9)

@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()

    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) {
            onRegisterSuccess()
            authViewModel.resetState()
        }
    }

    // ── Radial gradient background ────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFE0E0FF),
                        RSurface,
                        RSurfaceContainerLow
                    ),
                    radius = 900f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(vertical = 48.dp),
            verticalArrangement = Arrangement.Center
        ) {

            // ── Hero text ─────────────────────────────────────────────────
            Text(
                text = "Join SplitPay.",
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = RPrimary,
                letterSpacing = (-1).sp,
                lineHeight = 36.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Split smart. Stay friends.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = ROnSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Card ──────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(RSurfaceContainerLowest)
                    .padding(28.dp)
            ) {
                Column {

                    // ── Full Name ─────────────────────────────────────────
                    UnderlineField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = "FULL NAME",
                        placeholder = "Alex Dupont"
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // ── Email ─────────────────────────────────────────────
                    UnderlineField(
                        value = email,
                        onValueChange = { email = it },
                        label = "EMAIL",
                        placeholder = "alex@splitpay.com",
                        keyboardType = KeyboardType.Email
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // ── Password ──────────────────────────────────────────
                    UnderlineField(
                        value = password,
                        onValueChange = { password = it },
                        label = "PASSWORD",
                        placeholder = "••••••••",
                        keyboardType = KeyboardType.Password,
                        isPassword = true,
                        passwordVisible = passwordVisible,
                        onTogglePassword = { passwordVisible = !passwordVisible }
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // ── Confirm Password ──────────────────────────────────
                    UnderlineField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = "CONFIRM PASSWORD",
                        placeholder = "••••••••",
                        keyboardType = KeyboardType.Password,
                        isPassword = true,
                        passwordVisible = confirmPasswordVisible,
                        onTogglePassword = { confirmPasswordVisible = !confirmPasswordVisible }
                    )

                    // ── Errors ────────────────────────────────────────────
                    val errorMessage = when {
                        passwordError.isNotEmpty() -> passwordError
                        uiState is AuthUiState.Error -> (uiState as AuthUiState.Error).message
                        else -> null
                    }
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage,
                            color = Color(0xFFBA1A1A),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // ── Gradient CTA button ───────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(RPrimary, RPrimaryContainer),
                                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                    end = androidx.compose.ui.geometry.Offset(400f, 400f)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                passwordError = ""
                                when {
                                    password.length < 8 ->
                                        passwordError = "Minimum 8 characters"
                                    !password.any { it.isUpperCase() } ->
                                        passwordError = "At least 1 uppercase letter required"
                                    !password.any { it.isDigit() } ->
                                        passwordError = "At least 1 number required"
                                    password != confirmPassword ->
                                        passwordError = "Passwords do not match"
                                    else ->
                                        authViewModel.register(email, password, displayName)
                                }
                            },
                            enabled = uiState !is AuthUiState.Loading,
                            modifier = Modifier.fillMaxSize(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent
                            ),
                            elevation = ButtonDefaults.buttonElevation(0.dp)
                        ) {
                            if (uiState is AuthUiState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "Create Account",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // ── Divider "Or continue with" ─────────────────────────
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = ROutlineVariant.copy(alpha = 0.3f)
                        )
                        Text(
                            text = "  OR CONTINUE WITH  ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ROutlineVariant,
                            letterSpacing = 1.sp
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = ROutlineVariant.copy(alpha = 0.3f)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ── Social buttons ────────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SocialButton(
                            modifier = Modifier.weight(1f),
                            label = "Google",
                            onClick = { /* TODO: OAuth Google */ }
                        )
                        SocialButton(
                            modifier = Modifier.weight(1f),
                            label = "Apple",
                            onClick = { /* TODO: OAuth Apple */ }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Login link ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Already have an account? ",
                    fontSize = 14.sp,
                    color = ROnSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                TextButton(
                    onClick = onNavigateToLogin,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Sign In",
                        fontSize = 14.sp,
                        color = RPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
