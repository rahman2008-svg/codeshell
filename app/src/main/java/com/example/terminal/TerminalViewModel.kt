package com.example.terminal

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.CommandHistory
import com.example.data.database.SavedScript
import com.example.data.repository.CodeShellRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID

class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = CodeShellRepository(db.commandHistoryDao(), db.savedScriptDao())

    // Terminal settings
    val currentTheme = MutableStateFlow(TerminalTheme.HIGH_DENSITY)
    val fontSize = MutableStateFlow(13) // DP/SP
    val isWordWrap = MutableStateFlow(true)

    // State package manager
    val installedPackages = MutableStateFlow<Set<String>>(setOf("neofetch")) // neofetch pre-installed for immediate satisfaction

    // Terminal Sessions
    val sessions = mutableStateListOf<TerminalSession>()
    val activeSession = MutableStateFlow<TerminalSession?>(null)

    // Active Matrix screen effect state
    val isMatrixActive = MutableStateFlow(false)

    // REPL States
    val isReplActive = MutableStateFlow(false)
    val replType = MutableStateFlow<String?>(null) // "python" or "nodejs"
    private val replHistory = mutableListOf<String>()

    // AI Copilot States
    private val _copilotResponse = MutableStateFlow<String?>(null)
    val copilotResponse: StateFlow<String?> = _copilotResponse.asStateFlow()

    private val _isCopilotLoading = MutableStateFlow(false)
    val isCopilotLoading: StateFlow<Boolean> = _isCopilotLoading.asStateFlow()

    // Room flows
    val recentCommandHistory: StateFlow<List<CommandHistory>> = repository.recentHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedScripts: StateFlow<List<SavedScript>> = repository.allScripts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Start a default session on launch
        createNewSession()
    }

    fun createNewSession() {
        val count = sessions.size + 1
        val newSession = TerminalSession(
            name = "Session $count (sh)",
            filesDirPath = getApplication<Application>().filesDir.absolutePath,
            onSessionTerminated = { closedSession ->
                viewModelScope.launch(Dispatchers.Main) {
                    sessions.remove(closedSession)
                    if (activeSession.value == closedSession) {
                        activeSession.value = sessions.lastOrNull()
                    }
                }
            }
        )
        sessions.add(newSession)
        activeSession.value = newSession
    }

    fun closeSession(session: TerminalSession) {
        session.terminate()
    }

    fun selectSession(session: TerminalSession) {
        activeSession.value = session
    }

    /**
     * Submit command from UI input
     */
    fun submitCommand(cmdText: String) {
        val session = activeSession.value ?: return
        val trimmed = cmdText.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            // Save to DB
            repository.insertCommand(trimmed)

            // Intercept command logic
            if (isReplActive.value) {
                handleReplInput(trimmed, session)
            } else {
                handleShellInput(trimmed, session)
            }
        }
    }

    private suspend fun handleShellInput(trimmed: String, session: TerminalSession) {
        val parts = trimmed.split("\\s+".toRegex())
        val baseCmd = parts[0]

        // Local echo to console
        session.appendOutput("\n${session.prompt.value}$trimmed\n")

        when (baseCmd) {
            "help", "cs-help" -> {
                printHelp(session)
            }
            "clear" -> {
                session.output.value = ""
            }
            "pkg", "cs" -> {
                val action = parts.getOrNull(1)
                when (action) {
                    "list" -> printPackages(session)
                    "install" -> {
                        val pkgName = parts.getOrNull(2)
                        if (pkgName != null) {
                            runPackageInstallAnimation(pkgName, session)
                        } else {
                            session.appendOutput("Usage: pkg install <package_name>\n")
                        }
                    }
                    else -> {
                        session.appendOutput("CodeShell Package Manager:\n")
                        session.appendOutput("  pkg list             List installable packages\n")
                        session.appendOutput("  pkg install <pkg>    Install a package locally\n")
                    }
                }
            }
            "neofetch" -> {
                if (installedPackages.value.contains("neofetch")) {
                    printNeofetch(session)
                } else {
                    session.appendOutput("codeshell: command not found: neofetch\n💡 Try: pkg install neofetch\n")
                }
            }
            "cmatrix" -> {
                if (installedPackages.value.contains("cmatrix")) {
                    isMatrixActive.value = true
                } else {
                    session.appendOutput("codeshell: command not found: cmatrix\n💡 Try: pkg install cmatrix\n")
                }
            }
            "curl" -> {
                if (installedPackages.value.contains("curl")) {
                    val url = parts.getOrNull(1)
                    if (url != null) {
                        runLocalCurl(url, session)
                    } else {
                        session.appendOutput("Usage: curl <url>\n")
                    }
                } else {
                    session.appendOutput("codeshell: command not found: curl\n💡 Try: pkg install curl\n")
                }
            }
            "python" -> {
                if (installedPackages.value.contains("python")) {
                    enterRepl("python", ">>> ", session)
                } else {
                    session.appendOutput("codeshell: command not found: python\n💡 Try: pkg install python\n")
                }
            }
            "nodejs" -> {
                if (installedPackages.value.contains("nodejs")) {
                    enterRepl("nodejs", "> ", session)
                } else {
                    session.appendOutput("codeshell: command not found: nodejs\n💡 Try: pkg install nodejs\n")
                }
            }
            "gemini", "ai" -> {
                val query = parts.drop(1).joinToString(" ")
                if (query.isNotEmpty()) {
                    runGeminiQueryInTerminal(query, session)
                } else {
                    session.appendOutput("Usage: gemini <prompt text>\n")
                }
            }
            "themes" -> {
                printThemesList(session)
            }
            "theme" -> {
                val themeArg = parts.getOrNull(1)
                if (themeArg != null) {
                    applyThemeByName(themeArg, session)
                } else {
                    session.appendOutput("Usage: theme <theme_name_or_number>\nType 'themes' to list available themes.\n")
                }
            }
            else -> {
                // If native shell is running, write to it
                session.writeToProcess(trimmed + "\n")
            }
        }
    }

    private suspend fun handleReplInput(trimmed: String, session: TerminalSession) {
        session.appendOutput("\n${session.prompt.value}$trimmed\n")

        if (trimmed == "exit()" || trimmed == "quit()" || trimmed == "exit" || trimmed == ".exit") {
            exitRepl(session)
            return
        }

        replHistory.add(trimmed)
        val currentType = replType.value ?: return
        val capitalizedType = if (currentType == "python") "Python" else "Node.js"

        session.appendOutput("\u001B[1;30mEvaluating in Virtual $capitalizedType Engine...\u001B[0m\n")

        // Try to evaluate locally or forward to Gemini for an authentic REPL feel
        val historyContext = replHistory.joinToString("\n")
        val systemPrompt = "You are a stateful interactive $capitalizedType shell compiler. " +
                "Evaluate the user's latest statement and output only what a real compiler shell would return (stdout or evaluation results). " +
                "Keep variables stateful based on the history provided. If there is a syntax error, print it clearly. " +
                "Keep your response strictly short and concise. Do NOT include any markdown code blocks (e.g. ```python), just the raw console printout."

        viewModelScope.launch {
            val result = repository.generateAiContent(
                prompt = "History/Context:\n$historyContext\n\nLatest Statement to execute: $trimmed",
                systemInstruction = systemPrompt
            )
            result.onSuccess { text ->
                session.appendOutput(text + "\n")
            }.onFailure { e ->
                // Local fallback evaluator if offline/no API key
                tryLocalReplEvaluation(trimmed, session)
            }
        }
    }

    private fun tryLocalReplEvaluation(statement: String, session: TerminalSession) {
        if (statement.contains("print") || statement.contains("console.log")) {
            val content = statement.substringAfter("(").substringBeforeLast(")")
                .replace("'", "").replace("\"", "")
            session.appendOutput(content + "\n")
        } else if (statement.matches("^[0-9+\\-*/()\\s]+$".toRegex())) {
            // Basic math solver fallback
            session.appendOutput("= [Simulated Solver]: evaluation complete\n")
        } else {
            session.appendOutput("variable assignment recorded (simulated execution)\n")
        }
    }

    private fun enterRepl(type: String, promptChar: String, session: TerminalSession) {
        isReplActive.value = true
        replType.value = type
        replHistory.clear()
        session.prompt.value = promptChar
        
        if (type == "python") {
            session.appendOutput("Python 3.10.6 (tags/v3.10.6:9c7b4bd, Aug  1 2022) [Clang 14.0.6 (Android)] on linux\n")
            session.appendOutput("Type \"help\", \"copyright\", \"credits\" or \"license\" for more information.\n")
        } else {
            session.appendOutput("Welcome to Node.js v18.15.0.\n")
            session.appendOutput("Type \".help\" for more information.\n")
        }
    }

    private fun exitRepl(session: TerminalSession) {
        isReplActive.value = false
        replType.value = null
        replHistory.clear()
        session.prompt.value = "codeshell $ "
        session.appendOutput("Exited REPL back to main shell.\n")
    }

    // Custom commands outputs
    private fun printHelp(session: TerminalSession) {
        session.appendOutput("\n\u001B[1;36m==================== CODESHELL SYSTEM UTILITIES ====================\u001B[0m\n")
        session.appendOutput("CodeShell is an advanced developer tool styled after Termux.\n\n")
        session.appendOutput("\u001B[1;33mCore Built-in Commands:\u001B[0m\n")
        session.appendOutput("  \u001B[1;32mhelp\u001B[0m                Show this help console\n")
        session.appendOutput("  \u001B[1;32mclear\u001B[0m               Clear terminal scrollback\n")
        session.appendOutput("  \u001B[1;32mthemes\u001B[0m              List available retro color themes\n")
        session.appendOutput("  \u001B[1;32mtheme <name/no>\u001B[0m     Change active terminal look (e.g. 'theme matrix')\n")
        session.appendOutput("  \u001B[1;32mexit\u001B[0m                Close current session\n\n")
        session.appendOutput("\u001B[1;33mPackage Manager (pkg / cs):\u001B[0m\n")
        session.appendOutput("  \u001B[1;32mpkg list\u001B[0m            Check installable developer packages\n")
        session.appendOutput("  \u001B[1;32mpkg install <pkg>\u001B[0m   Install a simulated/interactive software tool\n\n")
        session.appendOutput("\u001B[1;33mInstallable Shell Packages:\u001B[0m\n")
        session.appendOutput("  \u001B[1;32mneofetch\u001B[0m            Print beautifully formatted device specifications\n")
        session.appendOutput("  \u001B[1;32mcmatrix\u001B[0m             Engage full-screen matrix cascading green code\n")
        session.appendOutput("  \u001B[1;32mcurl <url>\u001B[0m          Fetch live text content/JSON from any URL\n")
        session.appendOutput("  \u001B[1;32mpython\u001B[0m              Interactive Python REPL console (AI-driven evaluation!)\n")
        session.appendOutput("  \u001B[1;32mnodejs\u001B[0m              Interactive Node.js JS console\n\n")
        session.appendOutput("\u001B[1;33mGemini Integrated AI (ai / gemini):\u001B[0m\n")
        session.appendOutput("  \u001B[1;32mgemini <prompt>\u001B[0m     Run a state-of-the-art AI assistant inside the terminal!\n")
        session.appendOutput("  \u001B[1;35mAI Copilot Drawer\u001B[0m   Use the side Copilot panel for advanced script advice!\n")
        session.appendOutput("\u001B[1;36m====================================================================\u001B[0m\n")
    }

    private fun printPackages(session: TerminalSession) {
        val installed = installedPackages.value
        val allPkgs = listOf("neofetch", "cmatrix", "curl", "python", "nodejs")
        session.appendOutput("\n\u001B[1;35mCodeShell Package Repository:\u001B[0m\n")
        allPkgs.forEach { pkg ->
            val status = if (installed.contains(pkg)) "\u001B[1;32m[Installed]\u001B[0m" else "\u001B[1;31m[Available]\u001B[0m"
            session.appendOutput("  $pkg - $status\n")
        }
        session.appendOutput("Run 'pkg install <name>' to fetch and deploy any tool!\n")
    }

    private suspend fun runPackageInstallAnimation(pkgName: String, session: TerminalSession) {
        val normalized = pkgName.trim().lowercase()
        val allPkgs = listOf("neofetch", "cmatrix", "curl", "python", "nodejs")
        if (!allPkgs.contains(normalized)) {
            session.appendOutput("E: Package '$pkgName' not found in package repository.\n")
            return
        }

        if (installedPackages.value.contains(normalized)) {
            session.appendOutput("Package $pkgName is already installed.\n")
            return
        }

        session.appendOutput("Updating local repositories...\n")
        delay(600)
        session.appendOutput("Reading database records... Done.\n")
        delay(400)
        session.appendOutput("Resolving dependencies for \u001B[1;32m$normalized\u001B[0m... OK.\n")
        session.appendOutput("Need to retrieve 18.4 MB of archives.\n")
        session.appendOutput("Beginning download stream:\n")

        val steps = 10
        for (i in 1..steps) {
            delay(250)
            val percentage = i * 10
            val progressBar = StringBuilder("[")
            val filled = i * 2
            for (j in 1..20) {
                if (j <= filled) progressBar.append("=") else progressBar.append(" ")
            }
            progressBar.append("] $percentage%")
            // Print progress line, use carriage return if possible or just log
            session.appendOutput("  $progressBar Downloading package contents...\n")
        }

        delay(500)
        session.appendOutput("Unpacking binaries and libraries...\n")
        delay(600)
        session.appendOutput("Configuring system paths and environment variables...\n")
        delay(400)

        // Add package to set
        installedPackages.value = installedPackages.value + normalized
        session.appendOutput("\n\u001B[1;32mSuccessfully installed package: $normalized\u001B[0m 🎉\n")
        session.appendOutput("Try running '\u001B[1;33m$normalized\u001B[0m' now!\n")
    }

    private fun printNeofetch(session: TerminalSession) {
        val totalMem = Runtime.getRuntime().totalMemory() / (1024 * 1024)
        val freeMem = Runtime.getRuntime().freeMemory() / (1024 * 1024)
        val usedMem = totalMem - freeMem

        val asciiLogo = """
       \u001B[1;36m/\\_/\\      \u001B[1;32mcodeshell@android\u001B[0m
      \u001B[1;36m( o.o )     \u001B[1;30m-----------------\u001B[0m
       \u001B[1;36m> ^ <      \u001B[1;33mOS:\u001B[0m CodeShell Terminal OS v1.0
     \u001B[1;36m/   |   \\    \u001B[1;33mKernel:\u001B[0m Linux Android Core 5.15
    \u001B[1;36m/|_|_|_|_|\\   \u001B[1;33mUptime:\u001B[0m ${System.currentTimeMillis() / 60000 % 180} minutes
    \u001B[1;36m|_|_|_|_|_|   \u001B[1;33mShell:\u001B[0m CodeShell Interactive Bash
                  \u001B[1;33mCPU:\u001B[0m Android ARM Octa-Core
                  \u001B[1;33mMemory:\u001B[0m ${usedMem}MB / ${totalMem}MB (Allocated)
                  \u001B[1;33mThemes:\u001B[0m Classic Matrix, Slate, Retro CRT
                  \u001B[1;33mAI Engine:\u001B[0m Gemini 3.5 Flash Copilot
        """
        session.appendOutput(asciiLogo + "\n")
    }

    private suspend fun runLocalCurl(urlStr: String, session: TerminalSession) {
        session.appendOutput("\u001B[1;30mInitiating HTTP request via real curl stream...\u001B[0m\n")
        var correctedUrl = urlStr
        if (!correctedUrl.startsWith("http://") && !correctedUrl.startsWith("https://")) {
            correctedUrl = "https://$correctedUrl"
        }

        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url(correctedUrl)
                    .header("User-Agent", "CodeShell/1.0.0 (Android Terminal)")
                    .build()

                val response = client.newCall(request).execute()
                val headersString = StringBuilder()
                headersString.append("\u001B[1;34mHTTP/1.1 ${response.code} ${response.message}\u001B[0m\n")
                response.headers.forEach { pair ->
                    headersString.append("\u001B[1;33m${pair.first}:\u001B[0m ${pair.second}\n")
                }
                headersString.append("\n")

                val body = response.body?.string() ?: ""
                val bodySnippet = if (body.length > 2000) {
                    body.take(2000) + "\n\n\u001B[1;31m[Output truncated to 2000 characters]\u001B[0m"
                } else {
                    body
                }

                withContext(Dispatchers.Main) {
                    session.appendOutput(headersString.toString())
                    session.appendOutput(bodySnippet + "\n")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    session.appendOutput("curl: (7) Failed to connect to $urlStr: ${e.message}\n")
                }
            }
        }
    }

    private suspend fun runGeminiQueryInTerminal(prompt: String, session: TerminalSession) {
        session.appendOutput("\u001B[1;35m[AI Assistant]:\u001B[0m Consulting Gemini Brain model...\n")
        val sysInstruction = "You are an advanced embedded AI Assistant built inside a premium mobile terminal called CodeShell. Answer the query concisely. Style any code blocks or CLI snippets clearly."
        val result = repository.generateAiContent(prompt, sysInstruction)
        result.onSuccess { response ->
            session.appendOutput("\n\u001B[1;32m[AI Answer]:\u001B[0m\n$response\n")
        }.onFailure { err ->
            session.appendOutput("\n\u001B[1;31mE: Failed to get AI response.\u001B[0m ${err.localizedMessage}\n")
        }
    }

    private fun printThemesList(session: TerminalSession) {
        session.appendOutput("\n\u001B[1;36mCodeShell Visual Themes Catalog:\u001B[0m\n")
        TerminalTheme.values().forEachIndexed { index, theme ->
            session.appendOutput("  [${index + 1}] \u001B[1;33m${theme.name.lowercase()}\u001B[0m - ${theme.displayName}\n")
        }
        session.appendOutput("\nApply theme by typing: theme <name_or_number> (e.g. 'theme matrix' or 'theme 2')\n")
    }

    private fun applyThemeByName(themeArg: String, session: TerminalSession) {
        val cleanArg = themeArg.trim().lowercase()
        val index = cleanArg.toIntOrNull()
        var matchedTheme: TerminalTheme? = null

        if (index != null && index in 1..TerminalTheme.values().size) {
            matchedTheme = TerminalTheme.values()[index - 1]
        } else {
            matchedTheme = TerminalTheme.values().firstOrNull { it.name.lowercase() == cleanArg }
        }

        if (matchedTheme != null) {
            currentTheme.value = matchedTheme
            session.appendOutput("\u001B[1;32mTheme successfully changed to: ${matchedTheme.displayName}\u001B[0m 🎨\n")
        } else {
            session.appendOutput("theme: '$themeArg' matches no visual theme style.\nType 'themes' for a list.\n")
        }
    }

    // AI Copilot Actions
    fun askCopilot(userPrompt: String) {
        if (userPrompt.trim().isEmpty()) return
        _isCopilotLoading.value = true
        _copilotResponse.value = null

        viewModelScope.launch {
            val systemPrompt = "You are the advanced CodeShell AI Terminal Copilot. " +
                    "Help the user with Linux commands, shell scripting, package lookups, or system administration. " +
                    "If they ask for a command, return the suggested command enclosed in standard markdown backticks " +
                    "(e.g., `pkg install python` or `ls -la`) so they can execute it with one click. " +
                    "Explain the code snippets and teach the concepts in a friendly and professional developer tone."

            val result = repository.generateAiContent(userPrompt, systemPrompt)
            _isCopilotLoading.value = false
            result.onSuccess { text ->
                _copilotResponse.value = text
            }.onFailure { err ->
                _copilotResponse.value = "Failed to connect to AI Copilot: ${err.message}\n\n💡 Ensure your Gemini API Key is entered in the AI Studio Secrets tab."
            }
        }
    }

    fun runCopilotSuggestedCommand(command: String) {
        val session = activeSession.value ?: return
        // Input into current session automatically and run it
        submitCommand(command)
    }

    // Save custom scripts locally
    fun saveCustomScript(title: String, code: String) {
        viewModelScope.launch {
            repository.saveScript(title, code)
        }
    }

    fun deleteCustomScript(id: Int) {
        viewModelScope.launch {
            repository.deleteScript(id)
        }
    }
}
