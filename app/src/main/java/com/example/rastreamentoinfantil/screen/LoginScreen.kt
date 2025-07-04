package com.example.rastreamentoinfantil.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.rastreamentoinfantil.viewmodel.LoginViewModel
import com.example.rastreamentoinfantil.ui.theme.rememberResponsiveDimensions

@Composable
fun LoginScreen(
    loginViewModel: LoginViewModel,
    navController: NavHostController
) {
    val dimensions = rememberResponsiveDimensions()
    
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val user by loginViewModel.user.observeAsState()
    val isLoading by loginViewModel.isLoading.observeAsState(false)
    val error by loginViewModel.error.observeAsState()
    var showDialog by remember { mutableStateOf(false) }

    // A navegação agora é controlada pelo Navigation.kt baseada no estado de autenticação
    // Não precisamos mais navegar automaticamente aqui
    // O Navigation.kt observa o estado isLoggedIn e redireciona automaticamente

    // Controla a exibição do diálogo conforme o erro no ViewModel
    LaunchedEffect(error) {
        showDialog = !error.isNullOrEmpty()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
                .padding(dimensions.paddingMediumDp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
            // Container responsivo para o conteúdo
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = if (dimensions.isTablet) 400.dp else dimensions.screenWidth.dp)
                    .padding(dimensions.paddingMediumDp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimensions.paddingMediumDp)
            ) {
                // Título
                Text(
                    text = "Rastreamento Infantil",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = dimensions.textExtraLargeSp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(dimensions.paddingLargeDp))
                
                // Subtítulo
                Text(
                    text = "Faça login para continuar",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = dimensions.textMediumSp,
                        textAlign = TextAlign.Center
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(dimensions.paddingLargeDp))

        if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(dimensions.paddingMediumDp),
                        color = MaterialTheme.colorScheme.primary
                    )
        } else {
                    // Campo de email
            TextField(
                value = email,
                onValueChange = { email = it },
                        label = { 
                            Text(
                                "Email",
                                fontSize = dimensions.textMediumSp
                            ) 
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dimensions.textFieldHeightDp),
                        singleLine = true
                    )

                    // Campo de senha com toggle de visibilidade
            TextField(
                value = password,
                onValueChange = { password = it },
                        label = { 
                            Text(
                                "Senha",
                                fontSize = dimensions.textMediumSp
                            ) 
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Ocultar senha" else "Mostrar senha"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dimensions.textFieldHeightDp),
                        singleLine = true
            )

                    Spacer(modifier = Modifier.height(dimensions.paddingMediumDp))

                    // Botão de login
            Button(
                onClick = {
                    loginViewModel.login(email, password)
                },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dimensions.buttonHeightDp)
            ) {
                        Text(
                            "Entrar",
                            fontSize = dimensions.textMediumSp,
                            fontWeight = FontWeight.Medium
                        )
            }

                    Spacer(modifier = Modifier.height(dimensions.paddingSmallDp))

                    // Botão de cadastro
            Button(
                onClick = {
                    navController.navigate("register")
                },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dimensions.buttonHeightDp)
            ) {
                        Text(
                            "Cadastrar",
                            fontSize = dimensions.textMediumSp,
                            fontWeight = FontWeight.Medium
                        )
            }
        }
            }
        }

        // Diálogo de erro
        if (showDialog) {
            AlertDialog(
                onDismissRequest = {
                    showDialog = false
                    loginViewModel.clearError()
                },
                title = { 
                    Text(
                        "Erro",
                        fontSize = dimensions.textLargeSp,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                text = { 
                    Text(
                        error!!,
                        fontSize = dimensions.textMediumSp
                    ) 
                },
                confirmButton = {
                    Button(
                        onClick = {
                        showDialog = false
                            loginViewModel.clearError()
                        }
                    ) {
                        Text(
                            "OK",
                            fontSize = dimensions.textMediumSp
                        )
                    }
                }
            )
        }
    }
}