package com.example.rastreamentoinfantil.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rastreamentoinfantil.model.NotificationHistoryEntry
import com.example.rastreamentoinfantil.viewmodel.NotificationHistoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryScreen(
    viewModel: NotificationHistoryViewModel,
    userId: String,
    isResponsible: Boolean = false,
    onNavigateBack: () -> Unit
) {
    val notifications by viewModel.notifications.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedDependent by viewModel.selectedDependent.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val selectedEventType by viewModel.selectedEventType.collectAsState()
    val dependents by viewModel.dependents.collectAsState()
    val eventTypes by viewModel.eventTypes.collectAsState()
    val stats by remember { derivedStateOf { viewModel.getNotificationStats() } }
    
    // Estados para filtros
    var showFilterDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        viewModel.loadNotificationHistory(userId, isResponsible)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Histórico de Notificações") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filtros")
                    }
                    IconButton(onClick = { viewModel.refresh(userId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error ?: "Erro desconhecido",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.refresh(userId) }) {
                            Text("Tentar Novamente")
                        }
                    }
                }
                notifications.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.NotificationsNone,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Nenhuma notificação encontrada",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Estatísticas
                        item {
                            StatsCard(stats = stats)
                        }
                        
                        // Filtros ativos
                        if (selectedDependent != null || selectedDate != null || selectedEventType != null) {
                            item {
                                ActiveFiltersCard(
                                    selectedDependent = selectedDependent,
                                    selectedDate = selectedDate,
                                    selectedEventType = selectedEventType,
                                    onClearFilters = { viewModel.clearFilters() }
                                )
                            }
                        }
                        
                        items(notifications) { notification ->
                            NotificationCard(notification = notification)
                        }
                    }
                }
            }
        }
        
        // Dialog de filtros
        if (showFilterDialog) {
            FilterDialog(
                dependents = dependents,
                eventTypes = eventTypes,
                selectedDependent = selectedDependent,
                selectedEventType = selectedEventType,
                onDependentSelected = { viewModel.setDependentFilter(it) },
                onEventTypeSelected = { viewModel.setEventTypeFilter(it) },
                onDateSelected = { viewModel.setDateFilter(it) },
                onDismiss = { showFilterDialog = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCard(notification: NotificationHistoryEntry) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Cabeçalho com tipo de evento e horário
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ícone baseado no tipo de evento
                val icon = when (notification.tipoEvento) {
                    "saida_geofence" -> Icons.AutoMirrored.Filled.ExitToApp
                    "volta_geofence" -> Icons.Default.Home
                    "desvio_rota" -> Icons.Default.Warning
                    else -> Icons.Default.Notifications
                }
                
                val iconTint = when (notification.tipoEvento) {
                    "saida_geofence" -> MaterialTheme.colorScheme.error
                    "volta_geofence" -> MaterialTheme.colorScheme.primary
                    "desvio_rota" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
                
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Título
                Text(
                    text = notification.titulo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                
                // Horário
                notification.horarioEvento?.let { horario ->
                    Text(
                        text = horario,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Corpo da notificação
            Text(
                text = notification.body,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // Informações adicionais
            if (notification.childName != null || notification.latitude != null) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Nome do dependente
                    notification.childName?.let { name ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = name,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // Localização
                    if (notification.latitude != null && notification.longitude != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "%.4f, %.4f".format(notification.latitude, notification.longitude),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // Status de leitura
            if (!notification.lida) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        modifier = Modifier.size(8.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Não lida",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun FilterDialog(
    dependents: List<String>,
    eventTypes: List<String>,
    selectedDependent: String?,
    selectedEventType: String?,
    onDependentSelected: (String?) -> Unit,
    onEventTypeSelected: (String?) -> Unit,
    onDateSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDate by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filtros") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Filtro por dependente
                if (dependents.isNotEmpty()) {
                    Text(
                        text = "Dependente:",
                        fontWeight = FontWeight.Bold
                    )
                    dependents.forEach { dependent ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedDependent == dependent,
                                onClick = {
                                    onDependentSelected(
                                        if (selectedDependent == dependent) null
                                        else dependent
                                    )
                                }
                            )
                            Text(dependent)
                        }
                    }
                }
                
                // Filtro por tipo de evento
                if (eventTypes.isNotEmpty()) {
                    Text(
                        text = "Tipo de Evento:",
                        fontWeight = FontWeight.Bold
                    )
                    eventTypes.forEach { eventType ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedEventType == eventType,
                                onClick = {
                                    onEventTypeSelected(
                                        if (selectedEventType == eventType) null
                                        else eventType
                                    )
                                }
                            )
                            Text(eventType)
                        }
                    }
                }
                
                // Filtro por data
                Text(
                    text = "Data:",
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedDate == "hoje",
                        onClick = {
                            selectedDate = if (selectedDate == "hoje") null else "hoje"
                            onDateSelected(selectedDate)
                        }
                    )
                    Text("Hoje")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedDate == "ontem",
                        onClick = {
                            selectedDate = if (selectedDate == "ontem") null else "ontem"
                            onDateSelected(selectedDate)
                        }
                    )
                    Text("Ontem")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}

@Composable
fun StatsCard(stats: Map<String, Int>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Estatísticas",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Total", stats["total"] ?: 0)
                StatItem("Não lidas", stats["nao_lidas"] ?: 0)
                StatItem("Saídas", stats["saidas_geofence"] ?: 0 + (stats["saidas_rota"] ?: 0))
                StatItem("Retornos", stats["voltas_geofence"] ?: 0 + (stats["voltas_rota"] ?: 0))
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ActiveFiltersCard(
    selectedDependent: String?,
    selectedDate: String?,
    selectedEventType: String?,
    onClearFilters: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Filtros Ativos:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                selectedDependent?.let {
                    Text("Dependente: $it", fontSize = 12.sp)
                }
                selectedDate?.let {
                    Text("Data: $it", fontSize = 12.sp)
                }
                selectedEventType?.let {
                    Text("Evento: $it", fontSize = 12.sp)
                }
            }
            TextButton(onClick = onClearFilters) {
                Text("Limpar")
            }
        }
    }
} 