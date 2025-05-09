package com.example.rastreamentoinfantil.screen

import androidx.compose.ui.semantics.error
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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

@Composable
fun RegisterScreen(
    loginViewModel: LoginViewModel,
    navController: NavHostController
) {
    var name by remember { mutableStateOf("") }
    var cpf by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var confirmEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    val isLoading by loginViewModel.isLoading.observeAsState(false)
    val error by loginViewModel.error.observeAsState()

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
                label = { Text("Nome") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = cpf,
                onValueChange = { cpf = it },
                label = { Text("CPF") },
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
            Button(
                onClick = {
                    if(password != confirmPassword || email != confirmEmail){
                        showDialog = true
                        return@Button
                    }
                    val user = User(
                        name = name,
                        cpf = cpf,
                        email = email,
                        type = "responsavel"
                    )
                    loginViewModel.registerUser(user, password)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cadastrar-se")
            }
            if (!error.isNullOrEmpty()) {
                showDialog = true
            }
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Cadastro") },
                text = {
                    if (!error.isNullOrEmpty()){
                        Text(error!!)
                    }else if (password != confirmPassword){
                        Text("Senhas não conferem!")
                    } else if(email != confirmEmail){
                        Text("Emails não conferem!")
                    }else{
                        Text("Cadastrado com sucesso!")
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        showDialog = false
                        navController.popBackStack()
                    }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}