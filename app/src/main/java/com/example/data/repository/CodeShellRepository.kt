package com.example.data.repository

import com.example.BuildConfig
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import com.example.data.database.CommandHistory
import com.example.data.database.CommandHistoryDao
import com.example.data.database.SavedScript
import com.example.data.database.SavedScriptDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class CodeShellRepository(
    private val commandHistoryDao: CommandHistoryDao,
    private val savedScriptDao: SavedScriptDao
) {
    val recentHistory: Flow<List<CommandHistory>> = commandHistoryDao.getRecentHistory()
    val allScripts: Flow<List<SavedScript>> = savedScriptDao.getAllScripts()

    suspend fun insertCommand(commandText: String) = withContext(Dispatchers.IO) {
        commandHistoryDao.insertCommand(CommandHistory(command = commandText))
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        commandHistoryDao.clearHistory()
    }

    suspend fun saveScript(title: String, content: String) = withContext(Dispatchers.IO) {
        savedScriptDao.insertScript(SavedScript(title = title, content = content))
    }

    suspend fun deleteScript(id: Int) = withContext(Dispatchers.IO) {
        savedScriptDao.deleteScriptById(id)
    }

    /**
     * Call Gemini API to get suggestions, explanations, or write scripts.
     */
    suspend fun generateAiContent(prompt: String, systemInstruction: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("Gemini API key is not configured. Please add GEMINI_API_KEY to your Secrets panel."))
        }

        try {
            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                systemInstruction = systemInstruction?.let { Content(parts = listOf(Part(text = it))) }
            )
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val answer = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (answer != null) {
                Result.success(answer)
            } else {
                Result.failure(Exception("Received empty response from Gemini API."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
