package com.example.bajeti.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bajeti.auth.AuthViewModel
import com.example.bajeti.ui.theme.DividerColor
import com.example.bajeti.ui.theme.SurfaceWhite
import com.example.bajeti.ui.theme.TealLight
import com.example.bajeti.ui.theme.TealPrimary
import com.example.bajeti.ui.theme.TextPrimary
import com.example.bajeti.ui.theme.TextSecondary
import com.example.bajeti.ui.theme.TextTertiary

@Composable
fun SignUpScreen(viewModel: AuthViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordMismatch by remember { mutableStateOf(false) }

    val signUpState by viewModel.signUpUiState.collectAsStateWithLifecycle()

    LaunchedEffect(email, password, confirmPassword) {
        if (signUpState.error != null) viewModel.clearSignUpError()
        passwordMismatch = false
    }

    val isFormValid = email.isNotBlank() && password.isNotBlank() &&
        confirmPassword.isNotBlank() && !signUpState.isLoading

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceWhite)
                .verticalScroll(rememberScrollState())
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
            Text("Simplify your financial journey", fontSize = 13.sp, color = TealLight)

            Spacer(Modifier.height(40.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Create an account", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text("Enter your details to get started.", fontSize = 13.sp, color = TextSecondary)
            }

            Spacer(Modifier.height(28.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Email address", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = { Text("name@example.com", color = TextTertiary, fontSize = 14.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealPrimary,
                        unfocusedBorderColor = DividerColor,
                    ),
                    singleLine = true,
                    enabled = !signUpState.isLoading,
                )
            }

            Spacer(Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Password", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealPrimary,
                        unfocusedBorderColor = DividerColor,
                    ),
                    singleLine = true,
                    enabled = !signUpState.isLoading,
                )
            }

            Spacer(Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Confirm password", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (passwordMismatch) Color(0xFFEF4444) else TealPrimary,
                        unfocusedBorderColor = if (passwordMismatch) Color(0xFFEF4444) else DividerColor,
                    ),
                    singleLine = true,
                    enabled = !signUpState.isLoading,
                )
            }

            if (passwordMismatch) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Passwords do not match.",
                    color = Color(0xFFEF4444),
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (signUpState.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = signUpState.error!!,
                    color = Color(0xFFEF4444),
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (password != confirmPassword) {
                        passwordMismatch = true
                        return@Button
                    }
                    viewModel.signUpWithEmail(email, password)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                enabled = isFormValid,
            ) {
                Text("Create account  →", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = DividerColor)
                Text("  OR CONTINUE WITH  ", fontSize = 11.sp, color = TextTertiary)
                HorizontalDivider(modifier = Modifier.weight(1f), color = DividerColor)
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = { viewModel.signUpWithGoogle() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DividerColor),
                enabled = !signUpState.isLoading,
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(Color(0xFF4285F4), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("G", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Text("Sign up with Google", color = TextPrimary, fontSize = 15.sp)
            }

            Spacer(Modifier.height(28.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Already have an account? ", fontSize = 13.sp, color = TextSecondary)
                TextButton(onClick = { viewModel.navigateToLogin() }, contentPadding = PaddingValues(0.dp)) {
                    Text("Sign in.", fontSize = 13.sp, color = TealPrimary)
                }
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
