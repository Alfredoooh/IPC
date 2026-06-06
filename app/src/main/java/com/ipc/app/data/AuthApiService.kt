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
    val token: String
)

data class Conversation(
    val id: String,
    val title: String,
    val messages: List<ChatMessage>,
    val updatedAt: Long
)

sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val message: String) : AuthResult<Nothing>()
}

object AuthApiService {

    private const val BASE_URL = "https://ipc.alfredopjonas.workers.dev"

    suspend fun login(email: String, password: String): AuthResult<AuthUser> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply {
                    put("email", email)
                    put("password", password)
                }.toString()
                val conn = post("$BASE_URL/auth/login", body)
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                if (conn.responseCode == 200) {
                    AuthResult.Success(AuthUser(
                        id    = json.getString("id"),
                        name  = json.getString("name"),
                        email = json.getString("email"),
                        token = json.getString("token")
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
                val responseBody = if (conn.responseCode == 200)
                    conn.inputStream.bufferedReader().readText()
                else
                    conn.errorStream.bufferedReader().readText()
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

    suspend fun listConversations(token: String): List<Conversation> =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = get("$BASE_URL/conversations", token)
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val arr  = json.getJSONArray("conversations")
                (0 until arr.length()).map { i ->
                    val obj  = arr.getJSONObject(i)
                    val msgs = obj.getJSONArray("messages")
                    Conversation(
                        id        = obj.getString("id"),
                        title     = obj.getString("title"),
                        messages  = (0 until msgs.length()).map { j ->
                            val m = msgs.getJSONObject(j)
                            ChatMessage(m.getString("role"), m.getString("content"))
                        },
                        updatedAt = obj.getLong("updatedAt")
                    )
                }
            }.getOrDefault(emptyList())
        }

    suspend fun createConversation(token: String, title: String, messages: List<ChatMessage>): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val msgsArr = JSONArray().apply {
                    messages.forEach { put(JSONObject().apply { put("role", it.role); put("content", it.content) }) }
                }
                val body = JSONObject().apply {
                    put("title", title)
                    put("messages", msgsArr)
                }.toString()
                val conn = post("$BASE_URL/conversations", body, token)
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                json.getString("id")
            }.getOrNull()
        }

    suspend fun updateConversation(token: String, id: String, title: String, messages: List<ChatMessage>): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val msgsArr = JSONArray().apply {
                    messages.forEach { put(JSONObject().apply { put("role", it.role); put("content", it.content) }) }
                }
                val body = JSONObject().apply {
                    put("title", title)
                    put("messages", msgsArr)
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

    private fun get(url: String, token: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 15_000
        conn.readTimeout    = 15_000
        return conn
    }

    private fun post(url: String, body: String, token: String? = null): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout    = 15_000
        OutputStreamWriter(conn.outputStream).use { it.write(body) }
        return conn
    }

    private fun put(url: String, body: String, token: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "PUT"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout    = 15_000
        OutputStreamWriter(conn.outputStream).use { it.write(body) }
        return conn
    }

    private fun delete(url: String, token: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "DELETE"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 15_000
        conn.readTimeout    = 15_000
        return conn
    }
}