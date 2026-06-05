package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.TextStyle
import com.example.api.KernelResult
import com.example.data.ProcessedItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Custom Theme colors
val CyberBackground = Color(0xFF0A0C10)
val CyberCardBg = Color(0xFF121620)
val CyberBorder = Color(0xFF232A3B)
val CyberGreen = Color(0xFF10B981)
val CyberGreenAlpha = Color(0x2010B981)
val CyberBlue = Color(0xFF1D4ED8)
val CyberMint = Color(0xFF34D399)
val CyberOrange = Color(0xFFF59E0B)

/**
 * Copia o texto para o Clipboard e abre diretamente a tela de contatos do WhatsApp.
 */
fun shareToWhatsApp(context: Context, text: String, clipboardManager: androidx.compose.ui.platform.ClipboardManager) {
    if (text.isEmpty()) {
        Toast.makeText(context, "Não há texto para enviar.", Toast.LENGTH_SHORT).show()
        return
    }

    // 1. Copia o texto para a área de transferência do sistema
    clipboardManager.setText(AnnotatedString(text))

    // 2. Tenta abrir o WhatsApp padrão
    val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        setPackage("com.whatsapp")
    }

    try {
        context.startActivity(whatsappIntent)
        Toast.makeText(context, "Copiado! Direcionando para o WhatsApp...", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        // Tenta o WhatsApp Business caso o padrão não esteja instalado
        val businessIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            setPackage("com.whatsapp.w4b")
        }
        try {
            context.startActivity(businessIntent)
            Toast.makeText(context, "Copiado! Direcionando para o WhatsApp Business...", Toast.LENGTH_SHORT).show()
        } catch (ex: Exception) {
            // Se nenhum dos dois for localizado, usa o compartilhador genérico
            val generalIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(generalIntent, "WhatsApp não instalado. Enviar para:"))
            Toast.makeText(context, "Copiado para área de transferência! Escolha o aplicativo de envio.", Toast.LENGTH_LONG).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KernelDashboard(viewModel: KernelViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val rawText by viewModel.rawText.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val currentResult by viewModel.currentResult.collectAsStateWithLifecycle()
    val history by viewModel.historyItems.collectAsStateWithLifecycle()

    var showHistoryConfirmDialog by remember { mutableStateOf(false) }

    var activeShareText by remember { mutableStateOf("") }
    var showContactSelection by remember { mutableStateOf(false) }
    var deviceContacts by remember { mutableStateOf<List<DeviceContact>>(emptyList()) }
    var contactSearchQuery by remember { mutableStateOf("") }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            deviceContacts = fetchDeviceContacts(context)
            showContactSelection = true
        } else {
            Toast.makeText(context, "Permissão para acessar contatos foi recusada nas configurações.", Toast.LENGTH_LONG).show()
        }
    }

    val requestContactsAndShow: (String) -> Unit = { text ->
        activeShareText = text
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            deviceContacts = fetchDeviceContacts(context)
            showContactSelection = true
        } else {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    // Speech manager instantiator
    val speechManager = remember {
        SpeechManager(
            context = context,
            onResult = { resultText ->
                viewModel.onRawTextChanged(resultText)
                Toast.makeText(context, "Voz convertida!", Toast.LENGTH_SHORT).show()
            },
            onError = { err ->
                viewModel.setError(err)
            },
            onListeningStateChange = { listening ->
                viewModel.setListening(listening)
            }
        )
    }

    // Permission launcher for Recording
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            speechManager.startListening()
        } else {
            viewModel.setError("A permissão de gravação é necessária para capturar o áudio.")
        }
    }

    // Header pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseSize by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseSize"
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBackground),
        containerColor = CyberBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header item
                item {
                    TelemetryHeader(pulseSize, isListening, isProcessing)
                }

                // Error alert banner
                if (errorMessage != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0x30DC2626)),
                            border = BorderStroke(1.dp, Color(0xFFDC2626)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("error_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Alerta",
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = errorMessage ?: "",
                                    color = Color(0xFFFECACA),
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.setError(null) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Fechar erro",
                                        tint = Color(0xFFF87171)
                                    )
                                }
                            }
                        }
                    }
                }

                // Preset Transcriptions layout
                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Presets",
                                tint = CyberMint,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Carregar Amostras Brutas de Áudio (Dictation):",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(viewModel.presets) { preset ->
                                SuggestionChip(
                                    onClick = { viewModel.loadPreset(preset.text) },
                                    label = {
                                        Text(
                                            preset.title,
                                            color = if (rawText == preset.text) CyberGreen else Color.White
                                        )
                                    },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = if (rawText == preset.text) CyberGreenAlpha else CyberCardBg
                                    ),
                                    border = BorderStroke(1.dp, if (rawText == preset.text) CyberGreen else CyberBorder)
                                )
                            }
                        }
                    }
                }

                // Workspace Input card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                        border = BorderStroke(1.dp, CyberBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "DUMP TEXTUAL BRUTO (STT TRANSCRIPT)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = Color.LightGray
                                )
                                if (rawText.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.clearInput() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Limpar",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = rawText,
                                onValueChange = { viewModel.onRawTextChanged(it) },
                                placeholder = {
                                    Text(
                                        text = "Insira um texto gaguejado, informal, com pausas ou clique em 'Falar' para ditar...",
                                        color = Color.Gray,
                                        fontSize = 14.sp
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .testTag("transcription_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyberGreen,
                                    unfocusedBorderColor = CyberBorder,
                                    focusedContainerColor = CyberBackground,
                                    unfocusedContainerColor = CyberBackground,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                textStyle = TextStyle(
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Beautiful animated pulsing waveform showing speech & processing state
                            VoiceStateWaveform(isListening = isListening, isProcessing = isProcessing)

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Dictation Mic button
                                Button(
                                    onClick = {
                                        if (isListening) {
                                            speechManager.stopListening()
                                        } else {
                                            val permissionCheck = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            )
                                            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                                speechManager.startListening()
                                            } else {
                                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isListening) Color.Red else CyberBorder
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("voice_capture_button"),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                                            contentDescription = "Voz",
                                            tint = if (isListening) Color.White else CyberMint,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isListening) "Gravando..." else "Falar",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 13.sp
                                        )
                                    }
                                }

                                // Run modules button
                                Button(
                                    onClick = { viewModel.processTranscription() },
                                    enabled = !isProcessing && rawText.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyberGreen,
                                        disabledContainerColor = CyberBorder
                                    ),
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .height(48.dp)
                                        .testTag("process_button")
                                ) {
                                    if (isProcessing) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Outlined.Info,
                                                contentDescription = "Lapidar",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "LAPIDAR TEXTO",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }

                            if (rawText.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { viewModel.clearInput() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0x22EA4335),
                                        contentColor = Color(0xFFFF9494)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFFEA4335).copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("clear_text_button")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Apagar todo o texto já falado",
                                            tint = Color(0xFFEA4335),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "APAGAR TEXTO FALADO / TRANSCRITO",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Polished Output Result Section (Gemini)
                if (currentResult != null) {
                    item {
                        OutputResultCard(currentResult!!, clipboardManager, context, onShareToContact = requestContactsAndShow)
                    }
                }

                // History Section header
                if (history.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.List,
                                    contentDescription = "Histórico",
                                    tint = CyberMint,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "LOGS DO PROCESSADOR DE ESTADOS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }

                            TextButton(
                                onClick = { showHistoryConfirmDialog = true },
                                modifier = Modifier.height(30.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Limpar Logs", color = Color(0xFFF87171), fontSize = 12.sp)
                            }
                        }
                    }

                    items(history, key = { it.id }) { historyItem ->
                        HistoryRowItem(
                            item = historyItem,
                            clipboardManager = clipboardManager,
                            context = context,
                            onDelete = {
                                viewModel.deleteItem(historyItem)
                            },
                            onShareToContact = { text ->
                                requestContactsAndShow(text)
                            }
                        )
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Vazio",
                                    tint = CyberBorder,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Nenhum áudio lapidado no histórico local",
                                    color = Color.Gray,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // Real-time voice overlay
            if (isListening) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f))
                        .clickable { speechManager.stopListening() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(150.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp * pulseSize)
                                    .clip(CircleShape)
                                    .background(Color.Red.copy(alpha = 0.2f))
                            )
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .background(Color.Red.copy(alpha = 0.4f))
                            )
                            IconButton(
                                onClick = { speechManager.stopListening() },
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color.Red)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Gravar",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            "Kernel Capturando Áudio...",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Comece a falar. Toque em qualquer lugar para encerrar.",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            }
        }
    }

    // Clear history warning dialog
    if (showHistoryConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryConfirmDialog = false },
            title = { Text("Confirmar Limpeza", color = Color.White) },
            text = { Text("Deseja mesmo permanentemente expurgar todo o histórico de logs do Kernel?", color = Color.LightGray) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    onClick = {
                        viewModel.clearAllHistory()
                        showHistoryConfirmDialog = false
                    }
                ) {
                    Text("Expurgar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHistoryConfirmDialog = false }) {
                    Text("Cancelar", color = Color.Gray)
                }
            },
            containerColor = CyberCardBg,
            textContentColor = Color.White
        )
    }

    if (showContactSelection) {
        ContactSelectionDialog(
            contacts = deviceContacts,
            searchQuery = contactSearchQuery,
            onSearchChange = { contactSearchQuery = it },
            onDismiss = { showContactSelection = false },
            onContactSelected = { selectedContact ->
                showContactSelection = false
                val formattedPhone = formatPhoneNumberForWhatsApp(selectedContact.phoneNumber)
                sendWhatsAppDirect(context, formattedPhone, activeShareText, clipboardManager)
            }
        )
    }
}

@Composable
fun TelemetryHeader(pulseSize: Float, isListening: Boolean, isProcessing: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        border = BorderStroke(1.dp, CyberBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "KERNEL",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Linguístico",
                        color = CyberMint,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Light
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Camada de Lapidação Ativa",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            // Pulse beacon indicating system state
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(24.dp)
                ) {
                    val pulseColor = when {
                        isListening -> Color(0xFFEA4335)
                        isProcessing -> CyberOrange
                        else -> CyberGreen
                    }
                    Box(
                        modifier = Modifier
                            .size(12.dp * pulseSize)
                            .clip(CircleShape)
                            .background(pulseColor.copy(alpha = 0.4f))
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(pulseColor)
                    )
                }
                val labelColor = when {
                    isListening -> Color(0xFFEA4335)
                    isProcessing -> CyberOrange
                    else -> CyberGreen
                }
                val labelText = when {
                    isListening -> "ESCUTANDO"
                    isProcessing -> "LAPIDANDO"
                    else -> "PRONTO"
                }
                Text(
                    text = labelText,
                    color = labelColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactSelectionDialog(
    contacts: List<DeviceContact>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onContactSelected: (DeviceContact) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "SELECIONAR CONTATO",
                    color = CyberGreen,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Selecione um contato para abrir diretamente a conversa com o texto inserido.",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Buscar nome ou número...", color = Color.Gray) },
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberGreen,
                        unfocusedBorderColor = CyberBorder,
                        focusedContainerColor = CyberBackground,
                        unfocusedContainerColor = CyberBackground
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(8.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = CyberGreen
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Limpar",
                                    tint = Color.LightGray
                                )
                            }
                        }
                    }
                )

                val filteredContacts = remember(contacts, searchQuery) {
                    contacts.filter {
                        it.name.contains(searchQuery, ignoreCase = true) ||
                                it.phoneNumber.contains(searchQuery)
                    }
                }

                if (filteredContacts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (contacts.isEmpty()) "Nenhum contato encontrado no telefone." else "Nenhum contato coincide com a busca.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredContacts) { contact ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                                border = BorderStroke(1.dp, CyberBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onContactSelected(contact) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(CyberGreenAlpha),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = contact.name.firstOrNull()?.toString()?.uppercase() ?: "?",
                                            color = CyberGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = contact.name,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = contact.phoneNumber,
                                            color = Color.LightGray,
                                            fontSize = 11.sp
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "Enviar",
                                        tint = CyberGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = CyberBackground,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun OutputResultCard(
    result: KernelResult,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    context: Context,
    onShareToContact: (String) -> Unit
) {
    var expandedCoT by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        border = BorderStroke(1.dp, CyberGreen),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("output_result_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Lapidado",
                        tint = CyberGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "TEXTO LAPIDADO (CULTA PORTUGUÊS)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = CyberGreen
                    )
                }

                // Copy and Share actions row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(result.polishedText))
                            Toast.makeText(context, "Copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copiar",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            try {
                                val sendIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, result.polishedText)
                                    type = "text/plain"
                                    // Direct intent targets WhatsApp packages if needed
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Enviar via:")
                                context.startActivity(shareIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Erro ao compartilhar.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Compartilhar",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Strictly meets of UI/Visual Requirements (typing clean, no markdown)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberBackground)
                    .border(1.dp, CyberBorder, RoundedCornerShape(4.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = result.polishedText.ifEmpty { "[Ruídos ininteligíveis - Saneado para vazio]" },
                    color = if (result.polishedText.isEmpty()) Color.Gray else Color.White,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botão especial de copiar e direcionar para o WhatsApp
            Button(
                onClick = {
                    shareToWhatsApp(context, result.polishedText, clipboardManager)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF25D366) // WhatsApp official branding color
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("whatsapp_direct_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Enviar WhatsApp",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "COPIAR E ENVIAR NO WHATSAPP",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Novo botão de selecionar contato diretamente do app
            Button(
                onClick = {
                    onShareToContact(result.polishedText)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberBlue
                ),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, CyberBlue.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("whatsapp_contact_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Contatos do WhatsApp",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ENVIAR DIRETO DO SEU CONTATO LOCAL",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Expansion trigger step-by-step telemetry
            Row(
                modifier = Modifier
                    .fillModifierClickable { expandedCoT = !expandedCoT }
                    .fillMaxWidth()
                    .border(1.dp, CyberBorder, RoundedCornerShape(4.dp))
                    .background(CyberBackground)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (expandedCoT) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expandir de-bug",
                        tint = CyberOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Depuração do Kernel (Chain-of-Thought)",
                        color = CyberOrange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFFF9800).copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "5 MÓDULOS",
                        color = CyberOrange,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            AnimatedVisibility(
                visible = expandedCoT,
                modifier = Modifier.animateContentSize()
            ) {
                Column(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryStepItem("1. Saneamento de Pausas", result.fillerWords, Icons.Default.PlayArrow)
                    TelemetryStepItem("2. Correção de Gagueiras", result.repetitions, Icons.Default.Build)
                    TelemetryStepItem("3. Sintaxe & Concordância", result.syntaxCorrections, Icons.Default.Check)
                    TelemetryStepItem("4. Pontuação Lógica", result.logicalPunctuation, Icons.Default.Edit)
                    TelemetryStepItem("5. Conversão Coloquial", result.colloquialToProfessional, Icons.Default.Refresh)
                }
            }
        }
    }
}

@Composable
fun TelemetryStepItem(label: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        border = BorderStroke(1.dp, CyberBorder.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CyberMint,
                modifier = Modifier
                    .size(14.dp)
                    .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    color = CyberMint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun HistoryRowItem(
    item: ProcessedItem,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    context: Context,
    onDelete: () -> Unit,
    onShareToContact: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val formattedTime = remember(item.timestamp) {
        val sdf = SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault())
        sdf.format(Date(item.timestamp))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        border = BorderStroke(1.dp, CyberBorder),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(CyberGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Lapidado às $formattedTime",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Tap to copy original or prompt
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(item.polishedText))
                            Toast.makeText(context, "Copiado!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copiar polido",
                            tint = Color.LightGray,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Share button / Enviar para WhatsApp
                    IconButton(
                        onClick = {
                            onShareToContact(item.polishedText)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Enviar para o WhatsApp",
                            tint = Color.LightGray,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Trash button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remover",
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Toggle expand button
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expandir detalhes",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Polished final view (Main element we care about)
            Text(
                text = item.polishedText.ifEmpty { "[Limpeza efetuada sem saída textual]" },
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis
            )

            // If expanded, show the full comparison side-by-side
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Divider(color = CyberBorder, thickness = 1.dp)

                    // Input Raw
                    Column {
                        Text(
                            text = "ENTRADA ORIGINAL RECEPTORA:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberOrange,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberBackground)
                                .border(1.dp, CyberBorder, RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = item.originalText,
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Detailed steps accordion inside items
                    Column {
                        Text(
                            text = "AÇÕES DE REESTRUTURAÇÃO APLICADAS:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberMint,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TelemetryHistoryTag("1. Pausas", item.fillerWords.isNotEmpty())
                            TelemetryHistoryTag("2. Gagueira", item.repetitions.isNotEmpty())
                            TelemetryHistoryTag("3. Sintaxe", item.syntaxCorrections.isNotEmpty())
                            TelemetryHistoryTag("4. Pontuação", item.logicalPunctuation.isNotEmpty())
                            TelemetryHistoryTag("5. Coloquial", item.colloquialToProfessional.isNotEmpty())
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TelemetryHistoryTag(label: String, present: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (present) CyberGreenAlpha else CyberBorder.copy(alpha = 0.3f))
            .border(1.dp, if (present) CyberGreen.copy(alpha = 0.5f) else CyberBorder, RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = if (present) CyberMint else Color.Gray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// Inline helper extension to clear modifier chain and make clean code
private fun Modifier.fillModifierClickable(onClick: () -> Unit): Modifier {
    return this.clickable { onClick() }
}

@Composable
fun VoiceStateWaveform(
    isListening: Boolean,
    isProcessing: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isListening && !isProcessing) {
        // Simple resting indicator, subtle and elegant
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.5f))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Sistema pronto para escuta • Toque em Falar",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "waveform")

    // Dynamic wave bar heights animated independently
    val animDuration = 600

    val multiplier1 by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(animDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )

    val multiplier2 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(animDuration + 150, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )

    val multiplier3 by transition.animateFloat(
        initialValue = 0.1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(animDuration - 100, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    val multiplier4 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(animDuration + 250, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar4"
    )

    val multiplier5 by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(animDuration - 50, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar5"
    )

    val barColor = if (isListening) Color(0xFFEA4335) else CyberOrange
    val labelText = if (isListening) "CAPTURANDO ÁUDIO..." else "PROCESSANDO LAPIDAÇÃO..."

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .background(Color.Black.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp))
            .border(BorderStroke(1.dp, CyberBorder.copy(alpha = 0.5f)), shape = RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            // Left visualizer bars
            AudioWaveBar(heightMultiplier = multiplier1, baseColor = barColor)
            AudioWaveBar(heightMultiplier = multiplier2, baseColor = barColor)
            AudioWaveBar(heightMultiplier = multiplier3, baseColor = barColor)
            AudioWaveBar(heightMultiplier = multiplier4, baseColor = barColor)
            AudioWaveBar(heightMultiplier = multiplier5, baseColor = barColor)

            Spacer(modifier = Modifier.width(8.dp))

            // Center status pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor.copy(alpha = 0.15f))
                    .border(BorderStroke(1.dp, barColor.copy(alpha = 0.4f)), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = labelText,
                    color = barColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right visualizer bars (symmetric structure)
            AudioWaveBar(heightMultiplier = multiplier5, baseColor = barColor)
            AudioWaveBar(heightMultiplier = multiplier4, baseColor = barColor)
            AudioWaveBar(heightMultiplier = multiplier3, baseColor = barColor)
            AudioWaveBar(heightMultiplier = multiplier2, baseColor = barColor)
            AudioWaveBar(heightMultiplier = multiplier1, baseColor = barColor)
        }

        // Subtitle status prompt
        Text(
            text = if (isListening) {
                "Sua voz está sendo capturada em tempo real. Faça pausas naturais sem pressa (pausa de até 5s permitida)."
            } else {
                "Saneando redundâncias fônicas, repetições e ajustando formalidades da fala..."
            },
            color = Color.LightGray,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
fun AudioWaveBar(heightMultiplier: Float, baseColor: Color) {
    val barHeight = 20.dp * heightMultiplier
    Box(
        modifier = Modifier
            .width(4.dp)
            .height(barHeight)
            .clip(RoundedCornerShape(2.dp))
            .background(baseColor)
    )
}
