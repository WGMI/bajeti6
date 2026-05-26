package com.example.bajeti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bajeti.auth.AuthViewModel
import com.example.bajeti.ui.theme.DividerColor
import com.example.bajeti.ui.theme.SurfaceWhite
import com.example.bajeti.ui.theme.TealPrimary
import com.example.bajeti.ui.theme.TextPrimary
import com.example.bajeti.ui.theme.TextSecondary
import com.example.bajeti.ui.theme.TextTertiary

private const val OTP_LENGTH = 6

@Composable
fun EmailVerificationScreen(viewModel: AuthViewModel) {
    var otpValue by remember { mutableStateOf("") }
    var resendCooldown by remember { mutableStateOf(false) }

    val signUpState by viewModel.signUpUiState.collectAsStateWithLifecycle()

    LaunchedEffect(otpValue) {
        if (signUpState.error != null) viewModel.clearSignUpError()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceWhite)
                .padding(horizontal = 24.dp, vertical = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(TealPrimary, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("B", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text("Bajeti", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

            Spacer(Modifier.height(48.dp))

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color(0xFFE8F4F5), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("✉", fontSize = 32.sp)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Check your email",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "We sent a 6-digit verification code to your email address.",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )

            Spacer(Modifier.height(36.dp))

            OtpInputRow(
                value = otpValue,
                onValueChange = { if (it.length <= OTP_LENGTH) otpValue = it },
                enabled = !signUpState.isLoading,
            )

            if (signUpState.error != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = signUpState.error!!,
                    color = Color(0xFFEF4444),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = { viewModel.verifyEmail(otpValue) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                enabled = otpValue.length == OTP_LENGTH && !signUpState.isLoading,
            ) {
                Text("Verify email  →", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Didn't receive the code? ", fontSize = 13.sp, color = TextSecondary)
                TextButton(
                    onClick = {
                        viewModel.resendVerificationCode()
                        resendCooldown = true
                    },
                    contentPadding = PaddingValues(0.dp),
                    enabled = !resendCooldown && !signUpState.isLoading,
                ) {
                    Text(
                        if (resendCooldown) "Sent!" else "Resend",
                        fontSize = 13.sp,
                        color = if (resendCooldown) TextTertiary else TealPrimary,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick = { viewModel.navigateToSignUp() },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text("← Back to sign up", fontSize = 13.sp, color = TextTertiary)
            }
        }

        if (signUpState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = TealPrimary)
            }
        }
    }
}

@Composable
private fun OtpInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        enabled = enabled,
        decorationBox = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                repeat(OTP_LENGTH) { index ->
                    val char = value.getOrNull(index)
                    val isFocused = index == value.length
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .border(
                                width = if (isFocused) 2.dp else 1.dp,
                                color = when {
                                    isFocused -> TealPrimary
                                    char != null -> TealPrimary.copy(alpha = 0.5f)
                                    else -> DividerColor
                                },
                                shape = RoundedCornerShape(10.dp),
                            )
                            .background(
                                if (char != null) Color(0xFFE8F4F5) else SurfaceWhite,
                                RoundedCornerShape(10.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = char?.toString() ?: "",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        },
    )
}
