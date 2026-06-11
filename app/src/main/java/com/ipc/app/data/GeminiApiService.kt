Agora o Worker — precisa de usar o `systemPrompt` do body quando presente:

```javascript
// worker.js — só a função handleAiChat e buildSystemInstruction alteradas
// Substitui as duas funções existentes no worker

function buildSystemInstruction(language, customSystemPrompt) {
  // Se vier systemPrompt do body, usa-o directamente
  if (customSystemPrompt && customSystemPrompt.trim().length > 0) {
    return customSystemPrompt;
  }
  return language === "en"
    ? "You are a helpful AI assistant. Always respond in English. Be concise and direct. When the user asks for a table, use markdown table format. When providing code, always wrap it in fenced code blocks with the language identifier."
    : "Es um assistente de IA util. Responde sempre em portugues europeu. Se conciso e direto. Quando o utilizador pedir uma tabela, usa formato de tabela markdown. Quando deres codigo, coloca-o sempre em blocos com o identificador de linguagem.";
}

async function geminiGenerate(apiKey, messages, language, stream, thinkingBudget, customSystemPrompt) {
  const systemText = buildSystemInstruction(language, customSystemPrompt);
  const contents   = buildGeminiContents(messages);

  const generationConfig = {
    maxOutputTokens: 16384,
    temperature: 1,
    topP: 0.95,
  };

  const thinkingConfig = thinkingBudget > 0
    ? { thinkingConfig: { thinkingBudget: thinkingBudget } }
    : { thinkingConfig: { thinkingBudget: 0 } };

  const bodyObj = {
    system_instruction: { parts: [{ text: systemText }] },
    contents: contents,
    generationConfig: Object.assign({}, generationConfig, thinkingConfig),
  };

  const endpoint = stream
    ? GEMINI_BASE + "/" + GEMINI_MODEL + ":streamGenerateContent?alt=sse&key=" + apiKey
    : GEMINI_BASE + "/" + GEMINI_MODEL + ":generateContent?key=" + apiKey;

  return fetch(endpoint, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(bodyObj),
  });
}

async function handleAiChat(request, env) {
  const payload = await getAuthUser(request, env);
  if (!payload) return error("Não autenticado", 401);
  const body = await request.json().catch(function() { return null; });
  if (!body || !body.messages) return error("Messages obrigatório");

  const messages          = body.messages;
  const stream            = body.stream !== undefined ? body.stream : false;
  const language          = body.language || "pt";
  const thinkingBudget    = body.think ? 8000 : 0;
  const customSystemPrompt = body.systemPrompt || "";

  const gemRes = await geminiGenerate(env.GEMINI_API_KEY, messages, language, stream, thinkingBudget, customSystemPrompt);

  if (!gemRes.ok) {
    const errText = await gemRes.text();
    console.error("[CHAT ERROR]", gemRes.status, errText);
    return error("Erro Gemini API: " + errText, gemRes.status);
  }

  if (stream) {
    return new Response(gemRes.body, {
      headers: Object.assign({}, CORS_HEADERS, {
        "Content-Type": "text/event-stream",
        "Cache-Control": "no-cache",
        "X-Accel-Buffering": "no",
      }),
    });
  }

  const data = await gemRes.json();
  const candidate = data.candidates?.[0];
  const parts     = candidate?.content?.parts || [];
  let content   = "";
  let reasoning = null;
  for (const part of parts) {
    if (part.thought) { reasoning = part.text; }
    else { content += part.text || ""; }
  }
  return json({ content, reasoning, model: GEMINI_MODEL, usage: data.usageMetadata || null });
}