package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.ui.graphics.nativeCanvas
import com.example.data.database.CommandHistory
import com.example.data.database.SavedScript
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.terminal.AnsiParser
import com.example.terminal.TerminalSession
import com.example.terminal.TerminalTheme
import com.example.terminal.TerminalViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CodeShellApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeShellApp(viewModel: TerminalViewModel = viewModel()) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    
    // ViewModel states
    val sessions = viewModel.sessions
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val theme by viewModel.currentTheme.collectAsStateWithLifecycle()
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val isWordWrap by viewModel.isWordWrap.collectAsStateWithLifecycle()
    val isMatrixActive by viewModel.isMatrixActive.collectAsStateWithLifecycle()
    val copilotResponse by viewModel.copilotResponse.collectAsStateWithLifecycle()
    val isCopilotLoading by viewModel.isCopilotLoading.collectAsStateWithLifecycle()
    val savedScripts by viewModel.savedScripts.collectAsStateWithLifecycle()

    var showScriptsDialog by remember { mutableStateOf(false) }
    var showNewScriptDialog by remember { mutableStateOf(false) }
    var isCopilotOpen by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    if (isMatrixActive) {
        MatrixWaterfallScreen(onExit = { viewModel.isMatrixActive.value = false })
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(320.dp),
                    drawerContainerColor = Color(0xFF0F172A),
                    drawerContentColor = Color(0xFFF1F5F9)
                ) {
                    CodeShellDrawerContent(
                        sessions = sessions,
                        activeSession = activeSession,
                        onSessionSelect = {
                            viewModel.selectSession(it)
                            coroutineScope.launch { drawerState.close() }
                        },
                        onAddSession = {
                            viewModel.createNewSession()
                            coroutineScope.launch { drawerState.close() }
                        },
                        onCloseSession = { viewModel.closeSession(it) },
                        currentTheme = theme,
                        onThemeSelect = { viewModel.currentTheme.value = it },
                        fontSize = fontSize,
                        onFontSizeChange = { viewModel.fontSize.value = it },
                        isWordWrap = isWordWrap,
                        onWordWrapChange = { viewModel.isWordWrap.value = it },
                        onOpenScripts = {
                            showScriptsDialog = true
                            coroutineScope.launch { drawerState.close() }
                        },
                        onOpenAbout = {
                            showAboutDialog = true
                            coroutineScope.launch { drawerState.close() }
                        }
                    )
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(theme.accent)
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Terminal Logo",
                                        tint = if (theme == TerminalTheme.HIGH_DENSITY) Color(0xFF381E72) else theme.background,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "CodeShell",
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 18.sp,
                                        color = theme.foreground
                                    )
                                    Text(
                                        text = activeSession?.name?.uppercase() ?: "SESSION 1: SH",
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = theme.accent,
                                        letterSpacing = 1.2.sp
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = Color.White
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { showScriptsDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Scripts",
                                    tint = theme.accent
                                )
                            }
                            IconButton(
                                onClick = { isCopilotOpen = !isCopilotOpen },
                                modifier = Modifier.testTag("toggle_copilot")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "AI Copilot",
                                    tint = if (isCopilotOpen) theme.accent else Color.White
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = theme.background,
                            titleContentColor = theme.foreground
                        )
                    )
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(theme.background)
                ) {
                    // Left Column: Active Terminal Screen
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                    ) {
                        TerminalScreen(
                            session = activeSession,
                            theme = theme,
                            fontSize = fontSize,
                            isWordWrap = isWordWrap,
                            onSubmitCommand = { viewModel.submitCommand(it) },
                            recentHistory = viewModel.recentCommandHistory.collectAsStateWithLifecycle().value
                        )
                    }

                    // Right Column: AI Terminal Copilot (collapsible sidebar)
                    AnimatedVisibility(
                        visible = isCopilotOpen,
                        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(0.dp))
                    ) {
                        AiCopilotPanel(
                            onClose = { isCopilotOpen = false },
                            copilotResponse = copilotResponse,
                            isLoading = isCopilotLoading,
                            onQuery = { viewModel.askCopilot(it) },
                            onRunCommand = { viewModel.runCopilotSuggestedCommand(it) },
                            theme = theme
                        )
                    }
                }
            }
        }
    }

    // Scripts management dialog
    if (showScriptsDialog) {
        SavedScriptsDialog(
            scripts = savedScripts,
            onClose = { showScriptsDialog = false },
            onRun = { script ->
                viewModel.runCopilotSuggestedCommand("sh -c \"${script.content.replace("\"", "\\\"")}\"")
                showScriptsDialog = false
            },
            onDelete = { viewModel.deleteCustomScript(it.id) },
            onAddClick = { showNewScriptDialog = true }
        )
    }

    if (showNewScriptDialog) {
        NewScriptDialog(
            onClose = { showNewScriptDialog = false },
            onSave = { title, content ->
                viewModel.saveCustomScript(title, content)
                showNewScriptDialog = false
            }
        )
    }

    if (showAboutDialog) {
        AboutDeveloperDialog(
            theme = theme,
            onClose = { showAboutDialog = false }
        )
    }
}

@Composable
fun CodeShellDrawerContent(
    sessions: List<TerminalSession>,
    activeSession: TerminalSession?,
    onSessionSelect: (TerminalSession) -> Unit,
    onAddSession: () -> Unit,
    onCloseSession: (TerminalSession) -> Unit,
    currentTheme: TerminalTheme,
    onThemeSelect: (TerminalTheme) -> Unit,
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    isWordWrap: Boolean,
    onWordWrapChange: (Boolean) -> Unit,
    onOpenScripts: () -> Unit,
    onOpenAbout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "CodeShell Terminal",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = currentTheme.accent,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "Multi-Session Control Panel",
            style = MaterialTheme.typography.bodySmall,
            color = currentTheme.secondary
        )

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = Color(0xFF334155))
        Spacer(modifier = Modifier.height(16.dp))

        // Sessions list
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Terminal Sessions",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            IconButton(onClick = onAddSession, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Session",
                    tint = currentTheme.accent
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            sessions.forEachIndexed { idx, session ->
                val isActive = session == activeSession
                val itemBgColor = if (isActive) currentTheme.accent else Color.Transparent
                val itemContentColor = if (isActive) {
                    if (currentTheme == TerminalTheme.HIGH_DENSITY) Color(0xFF381E72) else currentTheme.background
                } else {
                    currentTheme.secondary
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(itemBgColor)
                        .clickable { onSessionSelect(session) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Shell Icon",
                            tint = itemContentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "session_${idx + 1} (${session.name})",
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = itemContentColor,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    if (sessions.size > 1) {
                        IconButton(
                            onClick = { onCloseSession(session) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.Red.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = Color(0xFF334155))
        Spacer(modifier = Modifier.height(16.dp))

        // Custom scripts manager shortcut
        Button(
            onClick = onOpenScripts,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Run", tint = currentTheme.accent)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Scripts Manager", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color.White)
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = Color(0xFF334155))
        Spacer(modifier = Modifier.height(16.dp))

        // Color Themes
        Text(
            text = "Visual Console Theme",
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            TerminalTheme.values().forEach { theme ->
                val isSelected = theme == currentTheme
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) currentTheme.accent.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { onThemeSelect(theme) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(theme.background)
                            .border(1.dp, theme.foreground, RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = theme.displayName,
                        fontSize = 13.sp,
                        color = if (isSelected) Color.White else currentTheme.secondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = Color(0xFF334155))
        Spacer(modifier = Modifier.height(16.dp))

        // Text preferences
        Text(
            text = "Terminal Preferences",
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Font Size selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Text Size", fontSize = 13.sp, color = currentTheme.secondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { if (fontSize > 8) onFontSizeChange(fontSize - 1) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Decrease", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${fontSize}sp",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { if (fontSize < 24) onFontSizeChange(fontSize + 1) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Increase", tint = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Word wrap toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Word Wrap", fontSize = 13.sp, color = currentTheme.secondary)
            Switch(
                checked = isWordWrap,
                onCheckedChange = onWordWrapChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = currentTheme.accent,
                    checkedTrackColor = currentTheme.accent.copy(alpha = 0.3f)
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = Color(0xFF334155))
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onOpenAbout,
            colors = ButtonDefaults.buttonColors(containerColor = currentTheme.accent),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "About",
                tint = if (currentTheme == TerminalTheme.HIGH_DENSITY) Color(0xFF381E72) else currentTheme.background
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "About Developer & Company",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (currentTheme == TerminalTheme.HIGH_DENSITY) Color(0xFF381E72) else currentTheme.background
            )
        }
    }
}

@Composable
fun TerminalScreen(
    session: TerminalSession?,
    theme: TerminalTheme,
    fontSize: Int,
    isWordWrap: Boolean,
    onSubmitCommand: (String) -> Unit,
    recentHistory: List<CommandHistory>
) {
    if (session == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = theme.accent)
        }
        return
    }

    val outputText by session.output.collectAsStateWithLifecycle()
    val promptText by session.prompt.collectAsStateWithLifecycle()
    
    var commandInput by remember { mutableStateOf(TextFieldValue("")) }
    var historyIndex by remember { mutableStateOf(-1) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Fast ANSI escape-code parser compilation
    val parsedAnnotatedText = remember(outputText, theme) {
        AnsiParser.parse(outputText, theme.foreground)
    }

    // Scroll to bottom when output updates
    LaunchedEffect(outputText) {
        delay(100)
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    // Blink cursor logic
    var cursorVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            cursorVisible = !cursorVisible
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        // Monospace terminal scrollback container (high density terminal viewport)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(Color.Black, RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF4A4458), RoundedCornerShape(16.dp))
                .clickable {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // Main outputs
                Text(
                    text = parsedAnnotatedText,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize + 4).sp
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Active prompt line
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = promptText,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSize.sp,
                            color = theme.accent,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    
                    Box(modifier = Modifier.weight(1f)) {
                        // Custom terminal entry text rendering with cursor
                        Row {
                            Text(
                                text = commandInput.text,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = fontSize.sp,
                                    color = theme.foreground
                                )
                            )
                            if (cursorVisible) {
                                Text(
                                    text = "█",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = fontSize.sp,
                                        color = theme.cursor
                                    )
                                )
                            }
                        }

                        // Fully transparent native input field behind to catch soft keyboard
                        BasicTextField(
                            value = commandInput,
                            onValueChange = { commandInput = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .testTag("terminal_input"),
                            textStyle = TextStyle(
                                color = Color.Transparent, // hide raw native characters
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSize.sp
                            ),
                            cursorBrush = SolidColor(Color.Transparent),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Done,
                                autoCorrectEnabled = false
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    val cmd = commandInput.text
                                    onSubmitCommand(cmd)
                                    commandInput = TextFieldValue("")
                                    historyIndex = -1
                                }
                            )
                        )
                    }
                }
            }
        }

        // Scrollable extra keys helper bar for developer efficiency
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val keys = listOf("ESC", "TAB", "CTRL", "ALT", "↑", "↓", "←", "→", "-", "|", "/")
            keys.forEach { key ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF4A4458))
                        .clickable {
                            when (key) {
                                "ESC" -> {
                                    // Simulated escape key
                                    session.appendOutput("^ESC ")
                                }
                                "TAB" -> {
                                    // Inject Tab autocomplete trigger
                                    val current = commandInput.text
                                    commandInput = TextFieldValue("$current\t")
                                }
                                "CTRL" -> {
                                    session.appendOutput("^C ")
                                }
                                "ALT" -> {
                                    session.appendOutput("^ALT ")
                                }
                                "↑" -> {
                                    // Recall older command history
                                    if (recentHistory.isNotEmpty()) {
                                        val nextIdx = historyIndex + 1
                                        if (nextIdx < recentHistory.size) {
                                            historyIndex = nextIdx
                                            commandInput = TextFieldValue(recentHistory[historyIndex].command)
                                        }
                                    }
                                }
                                "↓" -> {
                                    // Recall newer command history
                                    if (historyIndex > 0) {
                                        historyIndex -= 1
                                        commandInput = TextFieldValue(recentHistory[historyIndex].command)
                                    } else {
                                        historyIndex = -1
                                        commandInput = TextFieldValue("")
                                    }
                                }
                                else -> {
                                    // General character injection
                                    val current = commandInput.text
                                    commandInput = TextFieldValue(current + key)
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = key,
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE6E1E5)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun AiCopilotPanel(
    onClose: () -> Unit,
    copilotResponse: String?,
    isLoading: Boolean,
    onQuery: (String) -> Unit,
    onRunCommand: (String) -> Unit,
    theme: TerminalTheme
) {
    var copilotPrompt by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "AI",
                    tint = theme.accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "AI Terminal Copilot",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color.White
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Scrolling answers log
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2B2930))
                .border(1.dp, Color(0xFF4A4458), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = theme.accent, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Consulting Gemini 3.5...",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = theme.secondary
                    )
                }
            } else if (copilotResponse != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    // Beautiful markdown rendering simulation
                    val lines = copilotResponse.split("\n")
                    lines.forEach { line ->
                        if (line.trim().startsWith("`") && line.trim().endsWith("`") || line.trim().startsWith("`") && line.trim().contains("`")) {
                            // Extract command inside backticks
                            val cleanCmd = line.replace("`", "").trim()
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1B1F)),
                                border = BorderStroke(1.dp, Color(0xFF4A4458))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = cleanCmd,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = theme.accent,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = { onRunCommand(cleanCmd) },
                                        colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "RUN",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.background
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = line,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.padding(vertical = 2.dp),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Hello, Hacker! 🤖",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "I'm your terminal copilot. Ask me how to run script installations, write bash loops, curl REST endpoints, or manage files.",
                        fontSize = 11.sp,
                        color = theme.secondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    // Sample clicks
                    val samples = listOf("How to run Python REPL?", "Install CMatrix waterfall", "Save script example")
                    samples.forEach { sample ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1C1B1F))
                                .border(1.dp, Color(0xFF4A4458), RoundedCornerShape(8.dp))
                                .clickable { onQuery(sample) }
                                .padding(8.dp)
                        ) {
                            Text(
                                text = sample,
                                fontSize = 11.sp,
                                color = theme.accent,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Prompt input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = copilotPrompt,
                onValueChange = { copilotPrompt = it },
                placeholder = { Text("Ask copilot...", fontSize = 12.sp, color = theme.secondary) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("copilot_input"),
                textStyle = TextStyle(fontSize = 13.sp, color = Color.White),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.accent,
                    unfocusedBorderColor = Color(0xFF4A4458),
                    focusedContainerColor = Color(0xFF2B2930),
                    unfocusedContainerColor = Color(0xFF2B2930)
                ),
                maxLines = 2
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    onQuery(copilotPrompt)
                    copilotPrompt = ""
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.accent)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = theme.background
                )
            }
        }
    }
}

@Composable
fun MatrixWaterfallScreen(onExit: () -> Unit) {
    var tick by remember { mutableStateOf(0) }
    val letters = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_@#\$%&*-+=?/\\"
    
    // Setup falling columns
    val columnsCount = 35
    val columnsY = remember { IntArray(columnsCount) { Random.nextInt(-30, 0) } }
    val columnsSpeed = remember { IntArray(columnsCount) { Random.nextInt(1, 4) } }
    val columnChars = remember { 
        List(columnsCount) { 
            List(25) { letters[Random.nextInt(letters.length)] } 
        } 
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(40)
            tick++
            for (i in 0 until columnsCount) {
                columnsY[i] += columnsSpeed[i]
                if (columnsY[i] > 40) {
                    columnsY[i] = Random.nextInt(-20, 0)
                    columnsSpeed[i] = Random.nextInt(1, 4)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onExit() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellWidth = size.width / columnsCount
            val cellHeight = 35f

            for (col in 0 until columnsCount) {
                val startYIdx = columnsY[col]
                for (row in 0..25) {
                    val charY = startYIdx - row
                    if (charY in 0..35) {
                        val alpha = (1.0f - (row / 25.0f)).coerceIn(0f, 1f)
                        val color = if (row == 0) Color.White else Color(0xFF39FF14).copy(alpha = alpha)
                        val char = columnChars[col][(charY + tick) % columnChars[col].size]
                        
                        drawContext.canvas.nativeCanvas.drawText(
                            char.toString(),
                            col * cellWidth + 10f,
                            charY * cellHeight,
                            android.graphics.Paint().apply {
                                this.color = android.graphics.Color.argb(
                                    (color.alpha * 255).toInt(),
                                    (color.red * 255).toInt(),
                                    (color.green * 255).toInt(),
                                    (color.blue * 255).toInt()
                                )
                                this.textSize = 28f
                                this.isAntiAlias = true
                                this.typeface = android.graphics.Typeface.MONOSPACE
                            }
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.7f))
                .border(1.dp, Color(0xFF39FF14), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "CLICK ANYWHERE TO EXIT MATRIX",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF39FF14)
            )
        }
    }
}

@Composable
fun SavedScriptsDialog(
    scripts: List<SavedScript>,
    onClose: () -> Unit,
    onRun: (SavedScript) -> Unit,
    onDelete: (SavedScript) -> Unit,
    onAddClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "CodeShell Scripts", fontFamily = FontFamily.Monospace, fontSize = 16.sp)
                IconButton(onClick = onAddClick, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Script")
                }
            }
        },
        text = {
            Box(modifier = Modifier.sizeIn(maxHeight = 350.dp, maxWidth = 300.dp)) {
                if (scripts.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No custom scripts yet.",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Click '+' to save standard tasks.",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(scripts) { script ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                                border = BorderStroke(1.dp, Color(0xFF4A4458))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = script.title,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = script.content,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Color(0xFF06B6D4),
                                        maxLines = 2
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        IconButton(
                                            onClick = { onDelete(script) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = Color.Red.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Button(
                                            onClick = { onRun(script) },
                                            modifier = Modifier.height(28.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                        ) {
                                            Text(text = "RUN", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text(text = "DISMISS")
            }
        },
        containerColor = Color(0xFF1C1B1F),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

@Composable
fun NewScriptDialog(
    onClose: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(text = "New CodeShell Script", fontSize = 16.sp, fontFamily = FontFamily.Monospace) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Script Title (e.g. wordcount.py)", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 13.sp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Bash or Python shell code", fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotEmpty() && code.isNotEmpty()) onSave(title, code) },
                enabled = title.isNotEmpty() && code.isNotEmpty()
            ) {
                Text(text = "SAVE SCRIPT")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text(text = "CANCEL")
            }
        },
        containerColor = Color(0xFF1C1B1F),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

@Composable
fun AboutDeveloperDialog(
    theme: TerminalTheme,
    onClose: () -> Unit
) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .border(1.dp, theme.accent.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = theme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Circular Avatar or Tech Badge
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(theme.accent.copy(alpha = 0.15f))
                        .border(2.dp, theme.accent, androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "AR",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.accent
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Developer Name
                Text(
                    text = "Prince AR Abdur Rahman",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.foreground
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Subtitle
                Text(
                    text = "Independent App Developer",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.accent,
                        letterSpacing = 1.sp
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(theme.accent.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Bio
                Text(
                    text = "Independent App Developer passionate about building modern Android applications, productivity tools, AI-powered experiences, media players, educational apps, and next-generation digital products.",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 13.sp,
                        color = theme.foreground.copy(alpha = 0.85f),
                        lineHeight = 18.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(16.dp))

                // Contact Section Header
                Text(
                    text = "GET IN TOUCH",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.accent,
                        letterSpacing = 1.2.sp
                    ),
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // WhatsApp 1 Button
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { uriHandler.openUri("https://api.whatsapp.com/send?phone=8801707424006") },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF25D366).copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, Color(0xFF25D366))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "WhatsApp: 01707424006",
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF25D366)
                            )
                        )
                    }
                }

                // WhatsApp 2 Button
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { uriHandler.openUri("https://api.whatsapp.com/send?phone=8801796951709") },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF25D366).copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, Color(0xFF25D366))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "WhatsApp: 01796951709",
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF25D366)
                            )
                        )
                    }
                }

                // Facebook Button
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { uriHandler.openUri("https://www.facebook.com/share/1BNn32qoJo/") },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1877F2).copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, Color(0xFF1877F2))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Facebook Profile",
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1877F2)
                            )
                        )
                    }
                }

                // Instagram Button
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { uriHandler.openUri("https://www.instagram.com/ur___abdur____rahman__2008") },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE4405F).copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, Color(0xFFE4405F))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Instagram Profile",
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE4405F)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(16.dp))

                // About Company Section
                Text(
                    text = "ABOUT COMPANY",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.accent,
                        letterSpacing = 1.2.sp
                    ),
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "NexVora Lab's Ofc",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.foreground
                    ),
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "NexVora Lab's Ofc focuses on creating innovative Android applications designed to improve productivity, entertainment, learning, and digital experiences.",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 13.sp,
                        color = theme.foreground.copy(alpha = 0.85f),
                        lineHeight = 18.sp
                    ),
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = theme.foreground.copy(alpha = 0.05f)),
                    border = BorderStroke(1.dp, theme.accent.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "MISSION",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.accent
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Build fast, beautiful, privacy-friendly, and user-focused applications accessible to everyone.",
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 12.sp,
                                color = theme.foreground.copy(alpha = 0.9f),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(16.dp))

                // Technical Info Section
                Text(
                    text = "TECHNICAL INFORMATION",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.accent,
                        letterSpacing = 1.2.sp
                    ),
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "App Version", fontSize = 13.sp, color = theme.foreground.copy(alpha = 0.7f))
                    Text(text = "1.0.0", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.foreground)
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(16.dp))

                // Credits Section
                Text(
                    text = "CREDITS",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.accent,
                        letterSpacing = 1.2.sp
                    ),
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Developed by Prince AR Abdur Rahman\nPublished by NexVora Lab's Ofc\n© 2026 NexVora Lab's Ofc. All Rights Reserved.",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.sp,
                        color = theme.foreground.copy(alpha = 0.6f),
                        lineHeight = 16.sp
                    ),
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Dismiss Button
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "DISMISS",
                        fontWeight = FontWeight.Bold,
                        color = if (theme == TerminalTheme.HIGH_DENSITY) Color(0xFF381E72) else theme.background
                    )
                }
            }
        }
    }
}
