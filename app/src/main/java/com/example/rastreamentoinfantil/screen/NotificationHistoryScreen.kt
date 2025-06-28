package com.example.rastreamentoinfantil.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.TextUnit
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
    val selectedDateFilter by viewModel.selectedDateFilter.collectAsState()
    val selectedEventType by viewModel.selectedEventType.collectAsState()
    val selectedReadStatus by viewModel.selectedReadStatus.collectAsState()
    val dependents by viewModel.dependents.collectAsState()
    val eventTypes by viewModel.eventTypes.collectAsState()
    val dateFilterOptions by viewModel.dateFilterOptions.collectAsState()
    val readStatusOptions by viewModel.readStatusOptions.collectAsState()
    val stats by remember(notifications) { derivedStateOf { viewModel.getNotificationStats() } }
    val filteredStats by remember(notifications, selectedDependent, selectedDateFilter, selectedEventType, selectedReadStatus) { 
        derivedStateOf { viewModel.getFilteredStats() } 
    }
    
    // Estados para filtros
    var showFilterDialog by remember { mutableStateOf(false) }

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
                            StatsCard(
                                stats = stats, 
                                filteredStats = filteredStats,
                                hasActiveFilters = selectedDependent != null || 
                                                 selectedDateFilter != null || 
                                                 selectedEventType != null || 
                                                 selectedReadStatus != null
                            )
                        }
                        
                        // Filtros ativos
                        if (selectedDependent != null || selectedDateFilter != null || selectedEventType != null || selectedReadStatus != null) {
                            item {
                                ActiveFiltersCard(
                                    selectedDependent = selectedDependent,
                                    selectedDateFilter = selectedDateFilter,
                                    selectedEventType = selectedEventType,
                                    selectedReadStatus = selectedReadStatus,
                                    onClearFilters = { viewModel.clearFilters() }
                                )
                            }
                        }
                        
                        items(notifications) { notification ->
                            NotificationCard(
                                notification = notification,
                                onMarkAsRead = { 
                                    notification.id?.let { notificationId ->
                                        viewModel.markAsRead(notificationId, userId)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        
        // Dialog de filtros melhorado
        if (showFilterDialog) {
            EnhancedFilterDialog(
                dependents = dependents,
                eventTypes = eventTypes,
                dateFilterOptions = dateFilterOptions,
                readStatusOptions = readStatusOptions,
                selectedDependent = selectedDependent,
                selectedDateFilter = selectedDateFilter,
                selectedEventType = selectedEventType,
                selectedReadStatus = selectedReadStatus,
                onDependentSelected = { viewModel.setDependentFilter(it) },
                onDateFilterSelected = { viewModel.setDateFilter(it) },
                onEventTypeSelected = { viewModel.setEventTypeFilter(it) },
                onReadStatusSelected = { viewModel.setReadStatusFilter(it) },
                onDismiss = { showFilterDialog = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCard(notification: NotificationHistoryEntry, onMarkAsRead: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { 
                if (!notification.lida) {
                    onMarkAsRead()
                }
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!notification.lida) 
                MaterialTheme.colorScheme.surface 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
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
                    "saida_rota" -> Icons.Default.Warning
                    "volta_rota" -> Icons.Default.CheckCircle
                    "desvio_rota" -> Icons.Default.Warning
                    else -> Icons.Default.Notifications
                }
                
                val iconTint = when (notification.tipoEvento) {
                    "saida_geofence" -> MaterialTheme.colorScheme.error
                    "volta_geofence" -> MaterialTheme.colorScheme.primary
                    "saida_rota" -> MaterialTheme.colorScheme.error
                    "volta_rota" -> MaterialTheme.colorScheme.primary
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
            
            // Status de leitura (sempre mostrar)
                Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (!notification.lida) Icons.Default.FiberManualRecord else Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(8.dp),
                        tint = if (!notification.lida) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (!notification.lida) "Não lida" else "Lida",
                        fontSize = 12.sp,
                        color = if (!notification.lida) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                }
                
                // Botão para marcar como lida (apenas para não lidas)
                if (!notification.lida) {
                    TextButton(
                        onClick = onMarkAsRead,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Marcar como lida",
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EnhancedFilterDialog(
    dependents: List<String>,
    eventTypes: List<String>,
    dateFilterOptions: List<String>,
    readStatusOptions: List<String>,
    selectedDependent: String?,
    selectedDateFilter: String?,
    selectedEventType: String?,
    selectedReadStatus: String?,
    onDependentSelected: (String?) -> Unit,
    onDateFilterSelected: (String?) -> Unit,
    onEventTypeSelected: (String?) -> Unit,
    onReadStatusSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filtros Avançados") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Filtro por dependente
                if (dependents.isNotEmpty()) {
                    FilterSection(
                        title = "Dependente",
                        options = dependents,
                        selectedOption = selectedDependent,
                        onOptionSelected = onDependentSelected
                    )
                }
                
                // Filtro por tipo de evento
                if (eventTypes.isNotEmpty()) {
                    FilterSection(
                        title = "Tipo de Evento",
                        options = eventTypes,
                        selectedOption = selectedEventType,
                        onOptionSelected = onEventTypeSelected
                    )
                }
                
                // Filtro por data
                FilterSection(
                    title = "Período",
                    options = dateFilterOptions.map { getDateFilterDisplayName(it) },
                    selectedOption = selectedDateFilter?.let { getDateFilterDisplayName(it) },
                    onOptionSelected = { option ->
                        val filterValue = dateFilterOptions.find { getDateFilterDisplayName(it) == option }
                        onDateFilterSelected(filterValue)
                    }
                )
                
                // Filtro por status de leitura
                FilterSection(
                    title = "Status de Leitura",
                    options = readStatusOptions.map { getReadStatusDisplayName(it) },
                    selectedOption = selectedReadStatus?.let { getReadStatusDisplayName(it) },
                    onOptionSelected = { option ->
                        val filterValue = readStatusOptions.find { getReadStatusDisplayName(it) == option }
                        onReadStatusSelected(filterValue)
                    }
                )
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
fun FilterSection(
    title: String,
    options: List<String>,
    selectedOption: String?,
    onOptionSelected: (String?) -> Unit
) {
    Column {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        options.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedOption == option,
                    onClick = {
                        onOptionSelected(
                            if (selectedOption == option) null
                            else option
                        )
                    }
                )
                Text(
                    text = option,
                    fontSize = 14.sp
                )
            }
        }
    }
}

private fun getDateFilterDisplayName(filter: String): String {
    return when (filter) {
        "hoje" -> "Hoje"
        "ontem" -> "Ontem"
        "ultima_semana" -> "Última Semana"
        "ultimo_mes" -> "Último Mês"
        "ultima_hora" -> "Última Hora"
        "ultimas_24h" -> "Últimas 24 Horas"
        else -> filter
    }
}

private fun getReadStatusDisplayName(status: String): String {
    return when (status) {
        "todas" -> "Todas"
        "nao_lidas" -> "Não Lidas"
        "lidas" -> "Lidas"
        else -> status
    }
}

@Composable
fun StatsCard(
    stats: Map<String, Int>,
    filteredStats: Map<String, Int>,
    hasActiveFilters: Boolean
) {
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
            
            // Estatísticas gerais
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Total", stats["total"] ?: 0)
                StatItem("Não lidas", stats["nao_lidas"] ?: 0)
                StatItem("Saídas", (stats["saidas_geofence"] ?: 0) + (stats["saidas_rota"] ?: 0))
                StatItem("Retornos", (stats["voltas_geofence"] ?: 0) + (stats["voltas_rota"] ?: 0))
            }
            
            // Estatísticas filtradas (se houver filtros ativos)
            if (hasActiveFilters) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Resultados Filtrados",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("Filtrado", filteredStats["total_filtrado"] ?: 0, fontSize = 14.sp)
                    StatItem("Não lidas", filteredStats["nao_lidas_filtrado"] ?: 0, fontSize = 14.sp)
                    StatItem("Lidas", filteredStats["lidas_filtrado"] ?: 0, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: Int, fontSize: TextUnit = 18.sp) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
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
    selectedDateFilter: String?,
    selectedEventType: String?,
    selectedReadStatus: String?,
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
                selectedDateFilter?.let {
                    Text("Período: ${getDateFilterDisplayName(it)}", fontSize = 12.sp)
                }
                selectedEventType?.let {
                    Text("Evento: $it", fontSize = 12.sp)
                }
                selectedReadStatus?.let {
                    Text("Status: ${getReadStatusDisplayName(it)}", fontSize = 12.sp)
                }
            }
            TextButton(onClick = onClearFilters) {
                Text("Limpar")
            }
        }
    }
} 