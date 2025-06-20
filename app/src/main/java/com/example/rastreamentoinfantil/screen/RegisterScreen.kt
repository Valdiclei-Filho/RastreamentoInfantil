package com.example.rastreamentoinfantil.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.rastreamentoinfantil.model.User
import com.example.rastreamentoinfantil.viewmodel.LoginViewModel
import java.util.UUID
import kotlin.toString

@Composable
fun RegisterScreen(
    loginViewModel: LoginViewModel,
    navController: NavHostController
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var confirmEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    var acceptedTerms by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }

    val isLoading by loginViewModel.isLoading.observeAsState(false)
    val error by loginViewModel.error.observeAsState()
    val registerSuccess by loginViewModel.registerSuccess.observeAsState(false)

    // Controle para mensagem do diálogo
    val dialogMessage = when {
        !error.isNullOrEmpty() -> error!!
        password != confirmPassword -> "Senhas não conferem!"
        email != confirmEmail -> "Emails não conferem!"
        registerSuccess -> "Cadastrado com sucesso!"
        else -> ""
    }

    // Observe as mudanças de error e registerSuccess para abrir o diálogo
    androidx.compose.runtime.LaunchedEffect(error, registerSuccess) {
        if (!error.isNullOrEmpty() || registerSuccess) {
            showDialog = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome Completo") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = confirmEmail,
                onValueChange = { confirmEmail = it },
                label = { Text("Confirmar E-mail") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Senha") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirmar Senha") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = acceptedTerms,
                    onCheckedChange = { acceptedTerms = it }
                )
                TextButton(onClick = { showTermsDialog = true }) {
                    Text("Li e aceito o Termo de Uso e Privacidade")
                }
            }
            if (showTermsDialog) {
                AlertDialog(
                    onDismissRequest = { showTermsDialog = false },
                    title = { Text("Termo de Aceite") },
                    text = {
                        Text("Ao criar uma conta, você concorda com os Termos de Uso e a Política de Privacidade do aplicativo Rastreamento Infantil. Suas informações serão utilizadas apenas para fins de funcionamento do app, conforme descrito na política. Você pode solicitar a exclusão dos seus dados a qualquer momento.")
                    },
                    confirmButton = {
                        Button(onClick = { showTermsDialog = false }) {
                            Text("Fechar")
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    // Validações locais antes de chamar a ViewModel
                    if (password != confirmPassword || email != confirmEmail) {
                        showDialog = true
                        return@Button
                    }
                    val user = User(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        email = email,
                        type = null,
                        acceptedTerms = acceptedTerms
                    )
                    loginViewModel.registerUser(user, password)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = acceptedTerms
            ) {
                Text("Cadastrar-se")
            }
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Cadastro") },
                text = {
                    Text(dialogMessage)
                },
                confirmButton = {
                    Button(onClick = {
                        showDialog = false
                        if (registerSuccess) {
                            navController.navigate("login") {
                                popUpTo("register") { inclusive = true }
                            }
                        }
                    }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}
