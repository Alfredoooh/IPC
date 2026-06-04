// NvidiaApiService.kt
package com.ipc.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val role: String,   // "user" | "assistant" | "system"
    val content: String
)

sealed class StreamChunk {
    data class Token(val text: String) : StreamChunk()
    data class Done(val fullText: String) : StreamChunk()
    data class Error(val message: String) : StreamChunk()
}

object NvidiaApiService {

    private const val API_KEY  = "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"
    private const val BASE_URL = "https://integrate.api.nvidia.com/v1/chat/completions"
    private const val MODEL    = "deepseek-ai/deepseek-r1"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Envia mensagens e emite tokens em streaming via Flow.
     * Usa SSE (Server-Sent Events) com stream=true.
     */
    fun streamChat(
        messages: List<ChatMessage>,
        systemPrompt: String = buildSystemPrompt()
    ): Flow<StreamChunk> = callbackFlow {

        val allMessages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            messages.forEach { msg ->
                put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
        }

        val body = JSONObject().apply {
            put("model", MODEL)
            put("messages", allMessages)
            put("temperature", 0.7)
            put("top_p", 0.95)
            put("max_tokens", 4096)
            put("stream", true)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(BASE_URL)
            .addHeader("Authorization", "Bearer $API_KEY")
            .addHeader("Accept", "text/event-stream")
            .post(body)
            .build()

        val call = client.newCall(request)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                trySend(StreamChunk.Error("Erro de rede: ${e.message}"))
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    trySend(StreamChunk.Error("Erro API: ${response.code}"))
                    close()
                    return
                }

                val sb = StringBuilder()
                try {
                    val reader = BufferedReader(response.body!!.charStream())
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val l = line!!.trim()
                        if (l.startsWith("data: ")) {
                            val data = l.removePrefix("data: ").trim()
                            if (data == "[DONE]") {
                                trySend(StreamChunk.Done(sb.toString()))
                                break
                            }
                            try {
                                val json  = JSONObject(data)
                                val delta = json.getJSONArray("choices")
                                    .getJSONObject(0)
                                    .getJSONObject("delta")
                                if (delta.has("content")) {
                                    val token = delta.getString("content")
                                    if (token.isNotEmpty()) {
                                        sb.append(token)
                                        trySend(StreamChunk.Token(token))
                                    }
                                }
                            } catch (_: Exception) { /* ignora linhas malformadas */ }
                        }
                    }
                } catch (e: Exception) {
                    trySend(StreamChunk.Error("Erro ao ler stream: ${e.message}"))
                } finally {
                    response.body?.close()
                    close()
                }
            }
        })

        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    /**
     * Chamada normal (não streaming) — útil para preview ou contexto rápido.
     */
    suspend fun chat(
        messages: List<ChatMessage>,
        systemPrompt: String = buildSystemPrompt()
    ): Result<String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        runCatching {
            val allMessages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                messages.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            }

            val body = JSONObject().apply {
                put("model", MODEL)
                put("messages", allMessages)
                put("temperature", 0.7)
                put("top_p", 0.95)
                put("max_tokens", 4096)
                put("stream", false)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer $API_KEY")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw IOException("Erro API: ${response.code}")

            val json = JSONObject(response.body!!.string())
            json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }

    /**
     * Prompt do sistema — adapta o idioma ao utilizador.
     * O idioma será passado do prefs quando a ChatActivity chamar.
     */
    fun buildSystemPrompt(language: String = "pt"): String {
        val langInstruction = when (language) {
            "en" -> "Always respond in English."
            else -> "Responde sempre em português europeu."
        }
        return """
            És um assistente de IA integrado na app IPC. $langInstruction
            Sê conciso, útil e direto. Quando não souberes algo, diz-o claramente.
            Não uses formatação excessiva. Responde de forma natural e conversacional.
        """.trimIndent()
    }
}