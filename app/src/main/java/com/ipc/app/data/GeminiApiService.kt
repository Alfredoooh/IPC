// GeminiApiService.kt
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

object GeminiApiService {

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
            put("systemPrompt", systemPrompt)
            put("language", if (systemPrompt.contains("English") || systemPrompt.contains("en")) "en" else "pt")
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
                    trySend(StreamChunk.Error("Erro ${response.code}: verifica a tua ligação"))
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
                            val json = JSONObject(data)
                            val candidates = json.optJSONArray("candidates") ?: continue
                            val candidate  = candidates.optJSONObject(0) ?: continue
                            val content    = candidate.optJSONObject("content") ?: continue
                            val parts      = content.optJSONArray("parts") ?: continue

                            for (i in 0 until parts.length()) {
                                val part = parts.getJSONObject(i)
                                val text = part.optString("text", "")
                                if (text.isEmpty()) continue
                                if (part.optBoolean("thought", false)) {
                                    trySend(StreamChunk.ThinkToken(text))
                                } else {
                                    sb.append(text)
                                    trySend(StreamChunk.Token(text))
                                }
                            }

                            val finishReason = candidate.optString("finishReason", "")
                            if ((finishReason == "STOP" || finishReason == "MAX_TOKENS") && !doneSent) {
                                doneSent = true
                                trySend(StreamChunk.Done(sb.toString()))
                                break
                            }
                        } catch (_: Exception) {}
                    }
                    if (!doneSent) trySend(StreamChunk.Done(sb.toString()))
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
                val body = JSONObject().apply {
                    put("message", firstUserMessage)
                    put("language", language)
                }.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$BASE_URL/ai/title")
                    .addHeader("Authorization", "Bearer $token")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@runCatching ""
                val json = JSONObject(response.body!!.string())
                val title = json.optString("title", "").trim().take(40)
                if (title.equals("Nova conversa", ignoreCase = true)) "" else title
            }.getOrDefault("")
        }

    fun buildSystemPrompt(language: String = "pt", sheetsEnabled: Boolean = false): String {
        val base = when (language) {
            "en" -> "You are a helpful AI assistant integrated in the IPC app. Always respond in English. Be concise and direct. When the user asks for a table, use markdown table format. When providing code, always wrap it in fenced code blocks with the language identifier."
            else -> "És um assistente de IA integrado na app IPC. Responde sempre em português europeu. Sê conciso e direto. Quando o utilizador pedir uma tabela, usa formato de tabela markdown. Quando deres código, coloca-o sempre em blocos com o identificador de linguagem."
        }

        val sheetsInstruction = if (sheetsEnabled) {
            if (language == "en") {
                "\n\nWhen the user asks for a bar chart, respond with a JSON block tagged as widget_bar like this:\n" +
                "```widget_bar\n" +
                "{\"title\":\"Chart Title\",\"items\":[{\"label\":\"Jan\",\"value\":35},{\"label\":\"Feb\",\"value\":60}]}\n" +
                "```\n" +
                "When the user asks for a pie chart, respond with a JSON block tagged as widget_pie like this:\n" +
                "```widget_pie\n" +
                "{\"title\":\"Chart Title\",\"slices\":[{\"label\":\"A\",\"value\":40},{\"label\":\"B\",\"value\":30}]}\n" +
                "```\n" +
                "When the user asks for a data table, respond with a JSON block tagged as widget_table like this:\n" +
                "```widget_table\n" +
                "{\"headers\":[\"Col1\",\"Col2\"],\"rows\":[[\"A\",\"B\"],[\"C\",\"D\"]]}\n" +
                "```\n" +
                "When the user asks for mathematical workings, respond with a JSON block tagged as widget_sheet like this:\n" +
                "```widget_sheet\n" +
                "{\"lines\":[{\"text\":\"Resolution\",\"title\":true},{\"text\":\"Step 1: x = 5\"},{\"text\":\"Step 2: y = 10\"}]}\n" +
                "```\n" +
                "Always place explanatory text outside the JSON block. Only the structured data goes inside."
            } else {
                "\n\nQuando o utilizador pedir um gráfico de barras, responde com um bloco JSON com a tag widget_bar assim:\n" +
                "```widget_bar\n" +
                "{\"title\":\"Título do Gráfico\",\"items\":[{\"label\":\"Jan\",\"value\":35},{\"label\":\"Fev\",\"value\":60}]}\n" +
                "```\n" +
                "Quando o utilizador pedir um gráfico de pizza, responde com um bloco JSON com a tag widget_pie assim:\n" +
                "```widget_pie\n" +
                "{\"title\":\"Título do Gráfico\",\"slices\":[{\"label\":\"A\",\"value\":40},{\"label\":\"B\",\"value\":30}]}\n" +
                "```\n" +
                "Quando o utilizador pedir uma tabela de dados, responde com um bloco JSON com a tag widget_table assim:\n" +
                "```widget_table\n" +
                "{\"headers\":[\"Col1\",\"Col2\"],\"rows\":[[\"A\",\"B\"],[\"C\",\"D\"]]}\n" +
                "```\n" +
                "Quando o utilizador pedir resolução matemática, responde com um bloco JSON com a tag widget_sheet assim:\n" +
                "```widget_sheet\n" +
                "{\"lines\":[{\"text\":\"Resolução\",\"title\":true},{\"text\":\"Passo 1: x = 5\"},{\"text\":\"Passo 2: y = 10\"}]}\n" +
                "```\n" +
                "Coloca sempre o texto explicativo fora do bloco JSON. Só os dados estruturados vão dentro."
            }
        } else ""

        return base + sheetsInstruction
    }
}