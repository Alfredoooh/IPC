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
                if (!response.isSuccessful) return@runCatching "Nova conversa"
                val json = JSONObject(response.body!!.string())
                json.optString("title", "Nova conversa").trim().take(40)
            }.getOrDefault("Nova conversa")
        }

    fun buildSystemPrompt(language: String = "pt", sheetsEnabled: Boolean = false): String {
        val base = when (language) {
            "en" -> "You are a helpful AI assistant. Respond clearly and accurately."
            else -> "És um assistente de IA útil. Responde de forma clara e precisa em Português."
        }

        val sheetsInstruction = if (sheetsEnabled) {
            if (language == "en") {
                """


When the user asks for mathematical problem-solving, data tables, bar charts, pie charts, or other visual content, embed a widget using this exact format:

<widget type="sheet">
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <style>
    /* Use CSS variables already injected by the app: */
    /* var(--bg), var(--surface), var(--border), var(--text), var(--text-secondary) */
    /* var(--header-bg), var(--row-hover), var(--divider), var(--bar-color)           */
    body { margin: 0; padding: 12px; background: var(--bg, #fff); font-family: Arial, sans-serif; color: var(--text, #111); }
  </style>
</head>
<body>
  <!-- widget content here -->
</body>
</html>
</widget>

Widget types available:
- Mathematical workings: use the lined paper style (SVG with rules and margin line)
- Tables: use a clean table with rounded corners, header row, dividers
- Bar charts: animated bars using CSS/JS
- Pie charts: SVG-based pie chart with labels
- Mixed: combine elements as needed

Always use the CSS variables for colors so the widget adapts to light/dark mode automatically.
Only wrap visual content in <widget> tags. Regular text answers stay outside the tags."""
            } else {
                """


Quando o utilizador pedir resolução de problemas matemáticos, tabelas de dados, gráficos de barras, gráficos de pizza ou outro conteúdo visual, incorpora um widget com este formato exato:

<widget type="sheet">
<!DOCTYPE html>
<html lang="pt">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <style>
    /* Usa as variáveis CSS já injetadas pelo app: */
    /* var(--bg), var(--surface), var(--border), var(--text), var(--text-secondary) */
    /* var(--header-bg), var(--row-hover), var(--divider), var(--bar-color)           */
    body { margin: 0; padding: 12px; background: var(--bg, #fff); font-family: Arial, sans-serif; color: var(--text, #111); }
  </style>
</head>
<body>
  <!-- conteúdo do widget aqui -->
</body>
</html>
</widget>

Tipos de widget disponíveis:
- Resoluções matemáticas: usa o estilo de papel pautado (SVG com linhas e margem vermelha)
- Tabelas: tabela limpa com cantos arredondados, cabeçalho destacado, divisórias
- Gráficos de barras: barras animadas com CSS/JS
- Gráficos de pizza: gráfico SVG com legenda
- Misto: combina elementos conforme necessário

Usa sempre as variáveis CSS de cor para que o widget se adapte automaticamente ao modo claro/escuro.
Coloca apenas conteúdo visual dentro das tags <widget>. O texto normal fica fora das tags."""
            }
        } else ""

        return base + sheetsInstruction
    }
}