package com.example.terminal

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID

class TerminalSession(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "sh",
    private val filesDirPath: String,
    private val onSessionTerminated: (TerminalSession) -> Unit
) {
    val output = MutableStateFlow("")
    val prompt = MutableStateFlow("codeshell $ ")
    val isRunning = MutableStateFlow(true)
    
    // Process variables
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        startProcess()
    }

    private fun startProcess() {
        try {
            val workingDir = File(filesDirPath)
            if (!workingDir.exists()) {
                workingDir.mkdirs()
            }
            
            // Build process
            val pb = ProcessBuilder("sh")
                .directory(workingDir)
                .redirectErrorStream(true)
            
            val env = pb.environment()
            env["HOME"] = filesDirPath
            env["PATH"] = "${env["PATH"]}:/system/bin:/system/xbin"
            env["TERM"] = "xterm-256color"
            
            val proc = pb.start()
            process = proc
            
            writer = BufferedWriter(OutputStreamWriter(proc.outputStream))
            
            // Read output stream asynchronously
            coroutineScope.launch {
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                val buffer = CharArray(2048)
                var bytesRead = 0
                try {
                    while (isRunning.value && reader.read(buffer).also { bytesRead = it } != -1) {
                        val chunk = String(buffer, 0, bytesRead)
                        appendOutput(chunk)
                    }
                } catch (e: Exception) {
                    appendOutput("\n[Read Error: ${e.message}]")
                } finally {
                    reader.close()
                    terminate()
                }
            }
            
            // Wait for completion
            coroutineScope.launch {
                try {
                    val exitCode = proc.waitFor()
                    appendOutput("\n[Process finished with exit code $exitCode]")
                } catch (e: Exception) {
                    // Ignore
                } finally {
                    terminate()
                }
            }

            // Print welcome banner inside shell
            writeToProcess("echo -e \"\\e[1;36mWelcome to CodeShell terminal!\\e[0m\"\n")
            writeToProcess("echo -e \"\\e[1;32mType \\e[1;33mhelp\\e[1;32m for available CodeShell custom commands and tools.\\e[0m\"\n")
            writeToProcess("cd \"\$HOME\"\n")

        } catch (e: Exception) {
            Log.e("TerminalSession", "Failed to start real shell process", e)
            appendOutput("\u001B[1;33m[Warning]\u001B[0m Failed to launch native shell process.\n")
            appendOutput("Falling back to CodeShell Simulated Environment.\n\n")
            appendOutput("\u001B[1;36mCodeShell Virtual Terminal v1.0.0\u001B[0m\n")
            appendOutput("Type '\u001B[1;32mhelp\u001B[0m' to see custom tools, packages, and tutorials.\n\n")
            process = null
        }
    }

    fun appendOutput(text: String) {
        val current = output.value
        // Keep terminal output length limited to prevent memory bloat
        val next = if (current.length > 25000) {
            current.takeLast(15000) + text
        } else {
            current + text
        }
        output.value = next
    }

    fun writeToProcess(text: String) {
        val proc = process
        val wr = writer
        if (proc != null && wr != null) {
            coroutineScope.launch {
                try {
                    wr.write(text)
                    wr.flush()
                } catch (e: Exception) {
                    appendOutput("\n[Error writing input: ${e.message}]")
                }
            }
        } else {
            // Simulated fallback execution
            if (text.endsWith("\n")) {
                val cleanCmd = text.trim()
                if (cleanCmd.isNotEmpty()) {
                    simulateCommand(cleanCmd)
                }
            }
        }
    }

    private fun simulateCommand(commandLine: String) {
        val args = commandLine.split(" ")
        val cmd = args[0]
        appendOutput("\n${prompt.value}$commandLine\n")
        
        when (cmd) {
            "pwd" -> appendOutput("$filesDirPath\n")
            "ls" -> appendOutput("scripts/  docs/  cache/  user_script.sh\n")
            "whoami" -> appendOutput("codeshell_user\n")
            "uname" -> {
                if (args.contains("-a")) {
                    appendOutput("Linux CodeShell-VirtualDevice 5.15.0 #1 SMP PREEMPT Android\n")
                } else {
                    appendOutput("Linux\n")
                }
            }
            "id" -> appendOutput("uid=10234(codeshell) gid=10234(codeshell) groups=10234(codeshell)\n")
            "date" -> appendOutput("${java.util.Date()}\n")
            "ping" -> {
                val host = args.getOrNull(1) ?: "google.com"
                appendOutput("PING $host (142.250.190.46) 56(84) bytes of data.\n")
                appendOutput("64 bytes from $host: icmp_seq=1 ttl=117 time=21.4 ms\n")
                appendOutput("64 bytes from $host: icmp_seq=2 ttl=117 time=23.1 ms\n")
                appendOutput("--- $host ping statistics ---\n")
                appendOutput("2 packets transmitted, 2 received, 0% packet loss, time 1002ms\n")
            }
            "cat" -> {
                val file = args.getOrNull(1)
                if (file == "user_script.sh") {
                    appendOutput("#!/bin/sh\necho 'Hello World! Welcome to CodeShell!'\n")
                } else {
                    appendOutput("cat: ${file ?: ""}: No such file or directory\n")
                }
            }
            "mkdir" -> {
                val folder = args.getOrNull(1) ?: ""
                appendOutput("simulated-sh: created directory '$folder'\n")
            }
            "rm" -> {
                val target = args.getOrNull(1) ?: ""
                appendOutput("simulated-sh: removed '$target'\n")
            }
            else -> {
                appendOutput("simulated-sh: $cmd: command executed successfully (simulated environment)\n")
            }
        }
    }

    fun terminate() {
        if (!isRunning.value) return
        isRunning.value = false
        process?.destroy()
        process = null
        writer = null
        coroutineScope.cancel()
        onSessionTerminated(this)
    }
}
