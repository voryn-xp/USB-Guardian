package com.vorynxp.usbguardian.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import rikka.shizuku.Shizuku

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToOnboarding: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Trigger permission check on screen load/resume
    LaunchedEffect(Unit) {
        viewModel.checkShizukuPermission()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("USB Guardian", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToOnboarding) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Help Guide",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                )
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Shizuku status card / chip
            ShizukuStatusSection(
                status = uiState.shizukuState,
                onRequestPermission = {
                    try {
                        if (Shizuku.pingBinder()) {
                            Shizuku.requestPermission(101)
                        } else {
                            onNavigateToOnboarding()
                        }
                    } catch (e: Exception) {
                        // ignore or show setup guide
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Shield status card
            ShieldStatusCard(
                isActive = uiState.isProtectionActive,
                onToggle = { viewModel.toggleProtection(it) }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Stats row
            StatsSection(
                blockedToday = uiState.blockedTodayCount,
                allowed = uiState.allowedCount,
                total = uiState.totalDevicesSeen
            )

            Spacer(modifier = Modifier.weight(1f))

            // Guide recommendation
            TextButton(
                onClick = onNavigateToOnboarding,
                modifier = Modifier.padding(bottom = 80.dp)
            ) {
                Text(
                    "Need help starting Shizuku? View setup guide.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ShizukuStatusSection(
    status: ShizukuState,
    onRequestPermission: () -> Unit
) {
    val (text, color, icon) = when (status) {
        ShizukuState.CONNECTED -> Triple("Shizuku Connected", MaterialTheme.colorScheme.primary, Icons.Default.CheckCircle)
        ShizukuState.DISCONNECTED -> Triple("Shizuku Disconnected", MaterialTheme.colorScheme.error, Icons.Default.Warning)
        ShizukuState.PERMISSION_DENIED -> Triple("Permission Denied", MaterialTheme.colorScheme.error, Icons.Default.Warning)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = color
                )
            }

            if (status != ShizukuState.CONNECTED) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (status == ShizukuState.DISCONNECTED) "Setup & Connect Shizuku" else "Grant Shizuku Permission",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun ShieldStatusCard(
    isActive: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        color = if (isActive) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        },
                        shape = RoundedCornerShape(60.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .size(64.dp)
                        .animateContentSize()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (isActive) "Protection Active" else "Protection Off",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isActive) {
                    "Blocking unauthorized USB device access."
                } else {
                    "System vulnerable. All USB devices allowed."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Switch(
                checked = isActive,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

@Composable
fun StatsSection(
    blockedToday: Int,
    allowed: Int,
    total: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            title = "Blocked Today",
            value = blockedToday.toString(),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Allowed",
            value = allowed.toString(),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Total Seen",
            value = total.toString(),
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}
