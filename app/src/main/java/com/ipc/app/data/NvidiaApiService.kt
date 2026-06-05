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
    val role: String,
    val content: String
)

sealed class StreamChunk {
    data class Token(val text: String) : StreamChunk()
    data class Done(val fullText: String) : StreamChunk()
    data class Error(val message: String) : StreamChunk()
}

object NvidiaApiService {

    private const val BASE_URL = "https://ipc.alfredopjonas.workers.dev"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun streamChat(
        messages: List<ChatMessage>,
        systemPrompt: String = buildSystemPrompt(),
        token: String = ""
    ): Flow<StreamChunk> = callbackFlow {

        val messagesArray = JSONArray().apply {
            messages.forEach { msg ->
                put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
        }

        val body = JSONObject().apply {
            put("messages", messagesArray)
            put("stream", true)
            put("language", if (systemPrompt.contains("English")) "en" else "pt")
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$BASE_URL/ai/chat")
            .addHeader("Authorization", "Bearer $token")
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
                    trySend(StreamChunk.Error("Erro: ${response.code}"))
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
                                val json = JSONObject(data)
                                val delta = json.getJSONArray("choices")
                                    .getJSONObject(0)
                                    .getJSONObject("delta")
                                if (delta.has("content")) {
                                    val tok = delta.getString("content")
                                    if (tok.isNotEmpty()) {
                                        sb.append(tok)
                                        trySend(StreamChunk.Token(tok))
                                    }
                                }
                            } catch (_: Exception) {}
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