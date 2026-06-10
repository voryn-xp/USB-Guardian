package com.vorynxp.usbguardian.ui.logs

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vorynxp.usbguardian.data.db.LogEntity
import com.vorynxp.usbguardian.ui.theme.CrimsonRed
import com.vorynxp.usbguardian.ui.theme.EmeraldGreen
import com.vorynxp.usbguardian.ui.theme.SlateGray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    viewModel: LogsViewModel = hiltViewModel()
) {
    val logs by viewModel.logsState.collectAsState()
    val activeFilter by viewModel.filter.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event Logs", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (logs.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showClearConfirm = true },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear Logs")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeFilter == LogFilter.ALL,
                    onClick = { viewModel.setFilter(LogFilter.ALL) },
                    label = { Text("All Events") }
                )
                FilterChip(
                    selected = activeFilter == LogFilter.BLOCKED,
                    onClick = { viewModel.setFilter(LogFilter.BLOCKED) },
                    label = { Text("Blocked") }
                )
                FilterChip(
                    selected = activeFilter == LogFilter.ALLOWED,
                    onClick = { viewModel.setFilter(LogFilter.ALLOWED) },
                    label = { Text("Allowed") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No Events Found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Filtered logs list is empty.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(logs, key = { it.id }) { log ->
                        LogItemRow(log = log)
                    }
                }
            }
        }

        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("Clear Log History?", fontWeight = FontWeight.Bold) },
                text = { Text("This action will permanently delete all records of USB connection events. This cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearLogs()
                            showClearConfirm = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete All", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun LogItemRow(log: LogEntity) {
    val isBlocked = log.action.contains("Block", ignoreCase = true)
    val isAllowed = log.action.contains("Allow", ignoreCase = true) || log.action.contains("User Allowed", ignoreCase = true)
    val color = when {
        isBlocked -> CrimsonRed
        isAllowed -> EmeraldGreen
        else -> SlateGray
    }

    val icon = if (isBlocked) Icons.Default.Warning else Icons.Default.Info

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color.copy(alpha = 0.15f), shape = RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = log.deviceName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formatLogTime(log.timestamp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VID:PID — ${log.vidPid}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = log.action,
                        color = color,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

fun formatLogTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss · MMM dd", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
