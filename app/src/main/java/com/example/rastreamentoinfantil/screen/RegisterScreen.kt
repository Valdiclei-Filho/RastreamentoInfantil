package com.example.rastreamentoinfantil.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.rastreamentoinfantil.model.User
import com.example.rastreamentoinfantil.viewmodel.LoginViewModel
import com.example.rastreamentoinfantil.ui.theme.rememberResponsiveDimensions
import java.util.UUID

@Composable
fun RegisterScreen(
    loginViewModel: LoginViewModel,
    navController: NavHostController
) {
    val dimensions = rememberResponsiveDimensions()
    
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
    LaunchedEffect(error, registerSuccess) {
        if (!error.isNullOrEmpty() || registerSuccess) {
            showDialog = true
        }
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
                    .widthIn(max = if (dimensions.isTablet) 500.dp else dimensions.screenWidth.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(dimensions.paddingMediumDp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimensions.paddingMediumDp)
            ) {
                // Título
                Text(
                    text = "Criar Conta",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = dimensions.textExtraLargeSp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(dimensions.paddingLargeDp))

        if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(dimensions.paddingMediumDp),
                        color = MaterialTheme.colorScheme.primary
                    )
        } else {
                    // Campo Nome
            TextField(
                value = name,
                onValueChange = { name = it },
                        label = { 
                            Text(
                                "Nome Completo",
                                fontSize = dimensions.textMediumSp
                            ) 
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dimensions.textFieldHeightDp),
                        singleLine = true
                    )
                    
                    // Campo Email
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
                    
                    // Campo Confirmar Email
            TextField(
                value = confirmEmail,
                onValueChange = { confirmEmail = it },
                        label = { 
                            Text(
                                "Confirmar E-mail",
                                fontSize = dimensions.textMediumSp
                            ) 
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dimensions.textFieldHeightDp),
                        singleLine = true
                    )
                    
                    // Campo Senha
            TextField(
                value = password,
                onValueChange = { password = it },
                        label = { 
                            Text(
                                "Senha",
                                fontSize = dimensions.textMediumSp
                            ) 
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dimensions.textFieldHeightDp),
                        singleLine = true
                    )
                    
                    // Campo Confirmar Senha
            TextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                        label = { 
                            Text(
                                "Confirmar Senha",
                                fontSize = dimensions.textMediumSp
                            ) 
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dimensions.textFieldHeightDp),
                        singleLine = true
                    )
                    
                    // Checkbox de termos
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = acceptedTerms,
                            onCheckedChange = { acceptedTerms = it }
                        )
                        TextButton(
                            onClick = { showTermsDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "Li e aceito o Termo de Uso e Privacidade",
                                fontSize = dimensions.textSmallSp,
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                    
                    // Botão de cadastro
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dimensions.buttonHeightDp),
                        enabled = acceptedTerms
            ) {
                        Text(
                            "Cadastrar-se",
                            fontSize = dimensions.textMediumSp,
                            fontWeight = FontWeight.Medium
                        )
            }
        }
            }
        }

        // Diálogo de termos
        if (showTermsDialog) {
            AlertDialog(
                onDismissRequest = { showTermsDialog = false },
                title = { 
                    Text(
                        "Termo de Aceite",
                        fontSize = dimensions.textLargeSp,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                text = {
                    Text(
                        "Ao prosseguir com o cadastro, você declara estar ciente e de acordo com os seguintes pontos:\n\n" +
                                "1 - Permitir o rastreamento da criança para garantir sua segurança.\n\n" +
                                "2 - Coleta de dados: Permite coletar dados de localização da criança e do responsável.\n\n" +
                                "3 - Notificações: O responsável receberá alertas e deverá tomar as devidas providências sempre que necessário.\n\n" +
                                "4 - Compartilhamento: Os dados não serão compartilhados com terceiros, exceto por obrigação legal.\n\n" +
                                "5 - Segurança: Os dados são armazenados em ambiente seguro e protegido.\n\n" +
                                "6 - Direitos do Responsável: A qualquer momento, é possível solicitar a exclusão de dados ou revogar os direitos do aplicativo no celular.\n\n" +
                                "7 - Consentimento: O uso do aplicativo está condicionado à aceitação deste termo. Suas informações serão utilizadas apenas para fins de funcionamento do app, conforme descrito na política.",
                        fontSize = dimensions.textSmallSp,
                        textAlign = TextAlign.Start
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { showTermsDialog = false }
                    ) {
                        Text(
                            "Fechar",
                            fontSize = dimensions.textMediumSp
                        )
                    }
                }
            )
        }

        // Diálogo de resultado
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { 
                    Text(
                        "Cadastro",
                        fontSize = dimensions.textLargeSp,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                text = {
                    Text(
                        dialogMessage,
                        fontSize = dimensions.textMediumSp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                        showDialog = false
                        if (registerSuccess) {
                            navController.navigate("login") {
                                popUpTo("register") { inclusive = true }
                            }
                        }
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
