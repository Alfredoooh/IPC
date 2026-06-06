package com.ipc.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
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

data class ChatMessage(val role: String, val content: String)

sealed class StreamChunk {
    data class ThinkToken(val text: String) : StreamChunk()
    data class Token(val text: String) : StreamChunk()
    data class Done(val fullText: String) : StreamChunk()
    data class Error(val message: String) : StreamChunk()
}

object NvidiaApiService {

    private const val BASE_URL = "https://ipc.alfredopjonas.workers.dev"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun streamChat(
        messages: List<ChatMessage>,
        systemPrompt: String = buildSystemPrompt(),
        token: String = "",
        think: Boolean = false
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
            put("think", think)
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
                var doneSent = false
                try {
                    val reader = BufferedReader(response.body!!.charStream())
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val l = line!!.trim()
                        if (!l.startsWith("data: ")) continue
                        val data = l.removePrefix("data: ").trim()
                        if (data == "[DONE]") {
                            if (!doneSent) {
                                doneSent = true
                                trySend(StreamChunk.Done(sb.toString()))
                            }
                            break
                        }
                        try {
                            val json   = JSONObject(data)
                            val choice = json.getJSONArray("choices").getJSONObject(0)
                            val delta  = choice.getJSONObject("delta")

                            if (delta.has("reasoning_content") && !delta.isNull("reasoning_content")) {
                                val tok = delta.getString("reasoning_content")
                                if (tok.isNotEmpty()) trySend(StreamChunk.ThinkToken(tok))
                            }

                            if (delta.has("content") && !delta.isNull("content")) {
                                val tok = delta.getString("content")
                                if (tok.isNotEmpty()) {
                                    sb.append(tok)
                                    trySend(StreamChunk.Token(tok))
                                }
                            }

                            // finish_reason "stop" — só fecha se [DONE] não vier
                            val finishReason = choice.optString("finish_reason", "")
                            if (finishReason == "stop" && !doneSent) {
                                doneSent = true
                                trySend(StreamChunk.Done(sb.toString()))
                                break
                            }
                        } catch (_: Exception) {}
                    }
                    // fallback: se o stream fechou sem [DONE] nem stop
                    if (!doneSent) {
                        trySend(StreamChunk.Done(sb.toString()))
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

    suspend fun generateTitle(firstUserMessage: String, token: String, language: String = "pt"): String =
        withContext(Dispatchers.IO) {
            runCatching {
                val prompt = if (language == "en")
                    "Generate a short title (max 5 words) for a conversation that starts with: \"$firstUserMessage\". Reply with ONLY the title, no punctuation, no quotes."
                else
                    "Gera um título curto (máx 5 palavras) para uma conversa que começa com: \"$firstUserMessage\". Responde APENAS com o título, sem pontuação, sem aspas."

                val messagesArray = JSONArray().apply {
                    put(JSONObject().apply { put("role", "user"); put("content", prompt) })
                }

                val body = JSONObject().apply {
                    put("messages", messagesArray)
                    put("stream", false)
                    put("language", language)
                    put("think", false)
                }.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$BASE_URL/ai/chat")
                    .addHeader("Authorization", "Bearer $token")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val json = JSONObject(response.body!!.string())
                json.optString("content", "Nova conversa").trim().take(40)
            }.getOrDefault("Nova conversa")
        }

    fun buildSystemPrompt(language: String = "pt"): String {
        val langInstruction = when (language) {
            "en" -> "Always respond in English."
            else -> "Responde sempre em português europeu."
        }
        return """
            És um assistente de IA integrado na app IPC. $langInstruction
            Sê conciso, útil e direto. Quando não souberes algo, diz-o claramente.
            Não uses formatação excessiva. Responde de forma natural e conversacional.
            Quando o utilizador pedir uma tabela, formata em markdown com | separadores.
            Quando o utilizador pedir código de programação /canvas, apresenta o conteúdo do código num bloco de código delimitado por ``` com o tipo canvas.
        """.trimIndent()
    }
}