package com.vorynxp.usbguardian.ui.devices

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vorynxp.usbguardian.data.db.UsbDeviceEntity
import com.vorynxp.usbguardian.data.prefs.DeviceRule
import com.vorynxp.usbguardian.ui.theme.CrimsonRed
import com.vorynxp.usbguardian.ui.theme.EmeraldGreen
import com.vorynxp.usbguardian.ui.theme.SlateGray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    viewModel: DevicesViewModel = hiltViewModel()
) {
    val devices by viewModel.devicesState.collectAsState()
    var selectedDevice by remember { mutableStateOf<UsbDeviceEntity?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("USB Device Registry", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        if (devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No USB Devices Registered",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Connect a USB device to register it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(devices, key = { "${it.vendorId}_${it.productId}" }) { device ->
                    DeviceSwipeableRow(
                        device = device,
                        onTap = {
                            selectedDevice = device
                            showBottomSheet = true
                        },
                        viewModel = viewModel
                    )
                }
            }
        }

        if (showBottomSheet && selectedDevice != null) {
            DeviceDetailBottomSheet(
                device = selectedDevice!!,
                onDismiss = {
                    showBottomSheet = false
                    selectedDevice = null
                },
                onAllow = {
                    viewModel.updateDeviceRule(selectedDevice!!, DeviceRule.ALLOWED)
                    showBottomSheet = false
                },
                onBlock = {
                    viewModel.updateDeviceRule(selectedDevice!!, DeviceRule.BLOCKED)
                    showBottomSheet = false
                },
                onReset = {
                    viewModel.updateDeviceRule(selectedDevice!!, DeviceRule.UNKNOWN)
                    showBottomSheet = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSwipeableRow(
    device: UsbDeviceEntity,
    onTap: () -> Unit,
    viewModel: DevicesViewModel
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    viewModel.updateDeviceRule(device, DeviceRule.ALLOWED)
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    viewModel.updateDeviceRule(device, DeviceRule.BLOCKED)
                }
                else -> {}
            }
            false // Return false so it bounces back rather than removing from list
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> EmeraldGreen
                SwipeToDismissBoxValue.EndToStart -> CrimsonRed
                else -> Color.Transparent
            }
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Check
                SwipeToDismissBoxValue.EndToStart -> Icons.Default.Close
                else -> Icons.Default.Check
            }
            val text = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> "Allow"
                SwipeToDismissBoxValue.EndToStart -> "Block"
                else -> ""
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (direction == SwipeToDismissBoxValue.StartToEnd) {
                        Icon(icon, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text, color = Color.White, fontWeight = FontWeight.Bold)
                    } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                        Text(text, color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(icon, contentDescription = null, tint = Color.White)
                    }
                }
            }
        },
        content = {
            DeviceRowContent(device = device, onTap = onTap)
        }
    )
}

@Composable
fun DeviceRowContent(
    device: UsbDeviceEntity,
    onTap: () -> Unit
) {
    val badgeColor = when (device.status) {
        DeviceRule.ALLOWED.name -> EmeraldGreen
        DeviceRule.BLOCKED.name -> CrimsonRed
        else -> SlateGray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "VID:PID — ${device.vidPidString}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Last seen: ${formatTimestamp(device.lastSeen)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeColor.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = device.status,
                    color = badgeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailBottomSheet(
    device: UsbDeviceEntity,
    onDismiss: () -> Unit,
    onAllow: () -> Unit,
    onBlock: () -> Unit,
    onReset: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Device Details",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            DetailItem(label = "Device Name", value = device.name)
            DetailItem(label = "Vendor ID (VID)", value = String.format("%04X", device.vendorId))
            DetailItem(label = "Product ID (PID)", value = String.format("%04X", device.productId))
            DetailItem(label = "VID:PID Combination", value = device.vidPidString)
            DetailItem(label = "Last Connection Event", value = formatTimestamp(device.lastSeen))
            DetailItem(label = "Current Security Policy", value = device.status, isStatus = true)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Update Authorization Policy",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAllow,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Allow", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onBlock,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Block", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Reset to Default (Ask)", fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun DetailItem(
    label: String,
    value: String,
    isStatus: Boolean = false
) {
    val statusColor = if (isStatus) {
        when (value) {
            DeviceRule.ALLOWED.name -> EmeraldGreen
            DeviceRule.BLOCKED.name -> CrimsonRed
            else -> SlateGray
        }
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = statusColor
        )
    }
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
