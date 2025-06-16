package com.example.rastreamentoinfantil.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import androidx.compose.runtime.livedata.observeAsState
import androidx.navigation.NavController
import com.example.rastreamentoinfantil.viewmodel.FamilyViewModel
import com.example.rastreamentoinfantil.viewmodel.FamilyViewModelFactory
import com.example.rastreamentoinfantil.repository.FirebaseRepository
import com.google.firebase.auth.FirebaseAuth

@Composable
fun FamilyScreen(navController: NavController) {
    val repository = remember { FirebaseRepository() }
    val currentUser = FirebaseAuth.getInstance().currentUser
    val userId = currentUser?.uid ?: ""
    val userEmail = currentUser?.email ?: ""

    val viewModel: FamilyViewModel = viewModel(
        key = userId,
        factory = FamilyViewModelFactory(repository, userId, userEmail)
    )

    FamilyScreenContent(viewModel = viewModel, navController = navController)
}

@Composable
fun FamilyScreenContent(viewModel: FamilyViewModel, navController: NavController) {
    // Observers e estados
    val family by viewModel.family.observeAsState()
    val members by viewModel.members.observeAsState(emptyList())
    val invites by viewModel.invites.observeAsState(emptyList())
    val loading by viewModel.loading.observeAsState(false)
    val message by viewModel.message.observeAsState()

    val currentFamily = family

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 60.dp)
    ) {
        IconButton(onClick = { navController.popBackStack() }) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Voltar"
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (loading) {
            CircularProgressIndicator()
        } else {
            if (currentFamily == null) {
                Text("Você não pertence a nenhuma família.")
                Spacer(modifier = Modifier.height(8.dp))

                if (invites.isEmpty()) {
                    Text("Nenhum convite pendente.")
                } else {
                    Text("Convites pendentes:")
                    Spacer(modifier = Modifier.height(8.dp))
                    invites.forEach { invite ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(invite.familyName, style = MaterialTheme.typography.bodyLarge)
                            Button(onClick = { viewModel.acceptInvite(invite) }) {
                                Text("Aceitar")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                var familyName by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = familyName,
                    onValueChange = { familyName = it },
                    label = { Text("Nome da nova família") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.createFamily(familyName) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Criar Família")
                }
            } else {
                Text("Família: ${currentFamily.name}", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Membros:")
                members.forEach { member ->
                    Text("- ${member.name} (${member.email})")
                }

                if (currentFamily.responsibleId == viewModel.currentUserId) {
                    Spacer(modifier = Modifier.height(16.dp))
                    var emailToInvite by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = emailToInvite,
                        onValueChange = { emailToInvite = it },
                        label = { Text("Email para convidar") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.sendInvite(emailToInvite) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enviar Convite")
                    }
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Você não é o responsável pela família.")
                }
            }
        }

        message?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(it, color = Color.Red)
            LaunchedEffect(it) {
                delay(3000)
                viewModel.clearMessage()
            }
        }
    }
}
