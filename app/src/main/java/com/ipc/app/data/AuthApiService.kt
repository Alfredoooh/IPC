package com.ipc.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class AuthUser(
    val id: String,
    val name: String,
    val email: String,
    val token: String,
    val preferences: Map<String, String> = emptyMap()
)

data class Conversation(
    val id: String,
    val title: String,
    val messages: List<ChatMessage>,
    val updatedAt: Long,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val tags: List<String> = emptyList(),
    val model: String = "gemini-2.5-flash"
)

sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val message: String) : AuthResult<Nothing>()
}

object AuthApiService {

    private const val BASE_URL = "https://ipc.alfredopjonas.workers.dev"

    // ─── Auth ─────────────────────────────────────────────────────────────────

    suspend fun login(email: String, password: String): AuthResult<AuthUser> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply {
                    put("email", email)
                    put("password", password)
                }.toString()
                val conn = post("$BASE_URL/auth/login", body)
                val responseBody = readBody(conn)
                val json = JSONObject(responseBody)
                if (conn.responseCode == 200) {
                    val prefsObj = json.optJSONObject("preferences")
                    val prefsMap = mutableMapOf<String, String>()
                    prefsObj?.keys()?.forEach { k -> prefsMap[k] = prefsObj.optString(k) }
                    AuthResult.Success(AuthUser(
                        id          = json.getString("id"),
                        name        = json.getString("name"),
                        email       = json.getString("email"),
                        token       = json.getString("token"),
                        preferences = prefsMap
                    ))
                } else {
                    AuthResult.Error(json.optString("error", "Erro ao iniciar sessão."))
                }
            }.getOrElse { AuthResult.Error("Erro de ligação: ${it.message}") }
        }

    suspend fun register(name: String, email: String, password: String): AuthResult<AuthUser> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply {
                    put("name", name)
                    put("email", email)
                    put("password", password)
                }.toString()
                val conn = post("$BASE_URL/auth/register", body)
                val responseBody = readBody(conn)
                val json = JSONObject(responseBody)
                if (conn.responseCode == 200) {
                    AuthResult.Success(AuthUser(
                        id    = json.getString("id"),
                        name  = json.getString("name"),
                        email = json.getString("email"),
                        token = json.getString("token")
                    ))
                } else {
                    AuthResult.Error(json.optString("error", "Erro ao registar."))
                }
            }.getOrElse { AuthResult.Error("Erro de ligação: ${it.message}") }
        }

    suspend fun logout(token: String): AuthResult<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = post("$BASE_URL/auth/logout", "{}", token)
                if (conn.responseCode == 200) AuthResult.Success(Unit)
                else AuthResult.Error("Erro ao terminar sessão.")
            }.getOrElse { AuthResult.Error("Erro de ligação: ${it.message}") }
        }

    // ─── Conversas ────────────────────────────────────────────────────────────

    suspend fun listConversations(token: String, archived: Boolean = false): List<Conversation> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url  = "$BASE_URL/conversations${if (archived) "?archived=true" else ""}"
                val conn = get(url, token)
                val json = JSONObject(readBody(conn))
                parseConversationList(json.getJSONArray("conversations"))
            }.getOrDefault(emptyList())
        }

    suspend fun createConversation(token: String, title: String, messages: List<ChatMessage>): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val msgsArr = buildMessagesArray(messages)
                val body = JSONObject().apply {
                    put("title", title)
                    put("messages", msgsArr)
                }.toString()
                val conn = post("$BASE_URL/conversations", body, token)
                val json = JSONObject(readBody(conn))
                json.getString("id")
            }.getOrNull()
        }

    suspend fun updateConversation(token: String, id: String, title: String, messages: List<ChatMessage>): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply {
                    put("title", title)
                    put("messages", buildMessagesArray(messages))
                }.toString()
                val conn = put("$BASE_URL/conversations/$id", body, token)
                conn.responseCode == 200
            }.getOrDefault(false)
        }

    suspend fun deleteConversation(token: String, id: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = delete("$BASE_URL/conversations/$id", token)
                conn.responseCode == 200
            }.getOrDefault(false)
        }

    suspend fun deleteAllConversations(token: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = delete("$BASE_URL/conversations/all", token)
                conn.responseCode == 200
            }.getOrDefault(false)
        }

    suspend fun pinConversation(token: String, id: String, pinned: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply { put("pinned", pinned) }.toString()
                val conn = put("$BASE_URL/conversations/$id/pin", body, token)
                conn.responseCode == 200
            }.getOrDefault(false)
        }

    suspend fun archiveConversation(token: String, id: String, archived: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply { put("archived", archived) }.toString()
                val conn = put("$BASE_URL/conversations/$id/archive", body, token)
                conn.responseCode == 200
            }.getOrDefault(false)
        }

    suspend fun searchConversations(token: String, query: String): List<Conversation> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val conn    = get("$BASE_URL/conversations/search?q=$encoded", token)
                val json    = JSONObject(readBody(conn))
                parseConversationList(json.getJSONArray("conversations"))
            }.getOrDefault(emptyList())
        }

    suspend fun summarizeConversation(token: String, messages: List<ChatMessage>, language: String = "pt"): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply {
                    put("messages", buildMessagesArray(messages))
                    put("language", language)
                }.toString()
                val conn = post("$BASE_URL/ai/summarize", body, token)
                val json = JSONObject(readBody(conn))
                json.optString("summary", null)
            }.getOrNull()
        }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun parseConversationList(arr: JSONArray): List<Conversation> =
        (0 until arr.length()).map { i ->
            val obj  = arr.getJSONObject(i)
            val msgs = obj.optJSONArray("messages") ?: JSONArray()
            val tags = obj.optJSONArray("tags") ?: JSONArray()
            Conversation(
                id       = obj.getString("id"),
                title    = obj.getString("title"),
                messages = (0 until msgs.length()).map { j ->
                    val m = msgs.getJSONObject(j)
                    ChatMessage(m.getString("role"), m.getString("content"))
                },
                updatedAt = obj.getLong("updatedAt"),
                pinned    = obj.optBoolean("pinned", false),
                archived  = obj.optBoolean("archived", false),
                tags      = (0 until tags.length()).map { t -> tags.getString(t) },
                model     = obj.optString("model", "gemini-2.5-flash")
            )
        }

    private fun buildMessagesArray(messages: List<ChatMessage>): JSONArray =
        JSONArray().apply {
            messages.forEach { put(JSONObject().apply { put("role", it.role); put("content", it.content) }) }
        }

    private fun readBody(conn: HttpURLConnection): String =
        try { conn.inputStream.bufferedReader().readText() }
        catch (_: Exception) { conn.errorStream?.bufferedReader()?.readText() ?: "{}" }

    private fun get(url: String, token: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 15_000
            readTimeout    = 15_000
        }

    private fun post(url: String, body: String, token: String? = null): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            doOutput = true
            connectTimeout = 15_000
            readTimeout    = 15_000
            OutputStreamWriter(outputStream).use { it.write(body) }
        }

    private fun put(url: String, body: String, token: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            doOutput = true
            connectTimeout = 15_000
            readTimeout    = 15_000
            OutputStreamWriter(outputStream).use { it.write(body) }
        }

    private fun delete(url: String, token: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 15_000
            readTimeout    = 15_000
        }
}