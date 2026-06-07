const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization",
};

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: Object.assign({}, CORS_HEADERS, { "Content-Type": "application/json" }),
  });
}

function error(msg, status = 400) {
  return json({ error: msg }, status);
}

async function hashPassword(password) {
  const enc = new TextEncoder().encode(password);
  const hash = await crypto.subtle.digest("SHA-256", enc);
  return btoa(String.fromCharCode(...new Uint8Array(hash)));
}

async function generateToken(payload, secret) {
  const header = btoa(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const body   = btoa(JSON.stringify(Object.assign({}, payload, { iat: Date.now(), exp: Date.now() + 30 * 24 * 60 * 60 * 1000 })));
  const msg    = header + "." + body;
  const key    = await crypto.subtle.importKey(
    "raw", new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" }, false, ["sign"]
  );
  const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(msg));
  const sigB64 = btoa(String.fromCharCode(...new Uint8Array(sig)))
    .replace(/\+/g, "-").replace(/\//g, "_").replace(/=/g, "");
  return msg + "." + sigB64;
}

async function verifyToken(token, secret) {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const msg = parts[0] + "." + parts[1];
    const key = await crypto.subtle.importKey(
      "raw", new TextEncoder().encode(secret),
      { name: "HMAC", hash: "SHA-256" }, false, ["verify"]
    );
    const sigBytes = Uint8Array.from(atob(parts[2].replace(/-/g, "+").replace(/_/g, "/")), function(c) { return c.charCodeAt(0); });
    const valid = await crypto.subtle.verify("HMAC", key, sigBytes, new TextEncoder().encode(msg));
    if (!valid) return null;
    const payload = JSON.parse(atob(parts[1]));
    if (payload.exp < Date.now()) return null;
    return payload;
  } catch (e) { return null; }
}

async function getAuthUser(request, env) {
  const auth = request.headers.get("Authorization") || "";
  if (!auth.startsWith("Bearer ")) return null;
  return verifyToken(auth.slice(7), env.JWT_SECRET);
}

// ─── Gemini helpers ───────────────────────────────────────────────────────────

const GEMINI_MODEL = "gemini-2.5-flash";
const GEMINI_BASE  = "https://generativelanguage.googleapis.com/v1beta/models";

function buildGeminiContents(messages) {
  // messages: [{role:"user"|"assistant", content:"..."}]
  // Gemini espera role "user" ou "model"
  return messages
    .filter(function(m) { return m.role !== "system"; })
    .map(function(m) {
      return {
        role: m.role === "assistant" ? "model" : "user",
        parts: [{ text: m.content }],
      };
    });
}

function buildSystemInstruction(language) {
  return language === "en"
    ? "You are a helpful AI assistant. Always respond in English. Be concise and direct. When the user asks for a table, use markdown table format. When providing code, always wrap it in fenced code blocks with the language identifier."
    : "Es um assistente de IA util. Responde sempre em portugues europeu. Se conciso e direto. Quando o utilizador pedir uma tabela, usa formato de tabela markdown. Quando deres codigo, coloca-o sempre em blocos com o identificador de linguagem.";
}

async function geminiGenerate(apiKey, messages, language, stream, thinkingBudget) {
  const systemText = buildSystemInstruction(language);
  const contents   = buildGeminiContents(messages);

  const generationConfig = {
    maxOutputTokens: 16384,
    temperature: 1,
    topP: 0.95,
  };

  // thinking budget: 0 = desativado, >0 = tokens de raciocínio
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

async function geminiGenerateTitle(apiKey, message, language) {
  const prompt = language === "en"
    ? "Generate a short title (max 5 words) for a conversation that starts with: \"" + message + "\". Reply with ONLY the title, no punctuation, no quotes."
    : "Gera um titulo curto (max 5 palavras) para uma conversa que comeca com: \"" + message + "\". Responde APENAS com o titulo, sem pontuacao, sem aspas.";

  const res = await fetch(GEMINI_BASE + "/gemini-2.0-flash-lite:generateContent?key=" + apiKey, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      contents: [{ role: "user", parts: [{ text: prompt }] }],
      generationConfig: { maxOutputTokens: 20, temperature: 0.5 },
    }),
  });
  if (!res.ok) return "Nova conversa";
  const data = await res.json();
  const text = data.candidates?.[0]?.content?.parts?.[0]?.text || "Nova conversa";
  return text.trim().slice(0, 40);
}

// ─── Router ───────────────────────────────────────────────────────────────────

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { headers: CORS_HEADERS });

    const url  = new URL(request.url);
    const path = url.pathname;

    if (path === "/auth/register"                        && request.method === "POST")   return handleRegister(request, env);
    if (path === "/auth/login"                           && request.method === "POST")   return handleLogin(request, env);
    if (path === "/auth/logout"                          && request.method === "POST")   return handleLogout(request, env);
    if (path === "/auth/forgot-password"                 && request.method === "POST")   return handleForgotPassword(request, env);
    if (path === "/auth/reset-password"                  && request.method === "POST")   return handleResetPassword(request, env);
    if (path === "/user/me"                              && request.method === "GET")    return handleGetMe(request, env);
    if (path === "/user/me"                              && request.method === "PUT")    return handleUpdateMe(request, env);
    if (path === "/user/avatar"                          && request.method === "PUT")    return handleUpdateAvatar(request, env);
    if (path === "/ai/chat"                              && request.method === "POST")   return handleAiChat(request, env);
    if (path === "/ai/title"                             && request.method === "POST")   return handleAiTitle(request, env);
    if (path === "/ai/summarize"                         && request.method === "POST")   return handleAiSummarize(request, env);
    if (path === "/conversations"                        && request.method === "GET")    return handleListConversations(request, env);
    if (path === "/conversations"                        && request.method === "POST")   return handleCreateConversation(request, env);
    if (path === "/conversations/all"                    && request.method === "DELETE") return handleDeleteAllConversations(request, env);
    if (path.match(/^\/conversations\/[^\/]+$/)          && request.method === "GET")    return handleGetConversation(request, env);
    if (path.match(/^\/conversations\/[^\/]+$/)          && request.method === "PUT")    return handleUpdateConversation(request, env);
    if (path.match(/^\/conversations\/[^\/]+$/)          && request.method === "DELETE") return handleDeleteConversation(request, env);
    if (path.match(/^\/conversations\/[^\/]+\/pin$/)     && request.method === "PUT")    return handlePinConversation(request, env);
    if (path.match(/^\/conversations\/[^\/]+\/archive$/) && request.method === "PUT")    return handleArchiveConversation(request, env);
    if (path === "/conversations/search"                 && request.method === "GET")    return handleSearchConversations(request, env);

    return error("Not found", 404);
  },
};

// ─── Auth ─────────────────────────────────────────────────────────────────────

async function handleRegister(request, env) {
  const body = await request.json().catch(function() { return null; });
  if (!body) return error("Body inválido");
  const name     = body.name;
  const email    = body.email;
  const password = body.password;
  if (!name || !email || !password) return error("Campos obrigatórios em falta");
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return error("Email inválido");
  if (password.length < 6) return error("Password deve ter pelo menos 6 caracteres");
  const existing = await env.IPC_USERS.get("email:" + email.toLowerCase());
  if (existing) return error("Este email já está registado");
  const id           = crypto.randomUUID();
  const passwordHash = await hashPassword(password);
  const user         = {
    id: id,
    name: name,
    email: email.toLowerCase(),
    passwordHash: passwordHash,
    avatar: null,
    preferences: { language: "pt", theme: "system", fontSize: "medium" },
    stats: { totalConversations: 0, totalMessages: 0 },
    createdAt: Date.now(),
  };
  await env.IPC_USERS.put("user:" + id, JSON.stringify(user));
  await env.IPC_USERS.put("email:" + email.toLowerCase(), id);
  const token = await generateToken({ id: id, email: user.email, name: name }, env.JWT_SECRET);
  return json({ token: token, id: id, name: name, email: user.email });
}

async function handleLogin(request, env) {
  const body = await request.json().catch(function() { return null; });
  if (!body) return error("Body inválido");
  const email    = body.email;
  const password = body.password;
  if (!email || !password) return error("Campos obrigatórios em falta");
  const userId = await env.IPC_USERS.get("email:" + email.toLowerCase());
  if (!userId) return error("Email ou password incorretos", 401);
  const userData = await env.IPC_USERS.get("user:" + userId);
  if (!userData) return error("Email ou password incorretos", 401);
  const user = JSON.parse(userData);
  if (user.passwordHash !== await hashPassword(password)) return error("Email ou password incorretos", 401);
  const token = await generateToken({ id: user.id, email: user.email, name: user.name }, env.JWT_SECRET);
  return json({ token: token, id: user.id, name: user.name, email: user.email, preferences: user.preferences || {} });
}

async function handleLogout(request, env) {
  return json({ success: true });
}

async function handleForgotPassword(request, env) {
  const body = await request.json().catch(function() { return null; });
  if (!body || !body.email) return error("Email obrigatório");
  const email  = body.email.toLowerCase();
  const userId = await env.IPC_USERS.get("email:" + email);
  if (userId) {
    const resetToken = crypto.randomUUID().replace(/-/g, "");
    await env.IPC_USERS.put("reset:" + resetToken, JSON.stringify({ userId: userId, email: email, createdAt: Date.now() }), { expirationTtl: 3600 });
    console.log("[RESET] Token para " + email + ": " + resetToken);
  }
  return json({ success: true, message: "Se a conta existir, receberás um email com as instruções." });
}

async function handleResetPassword(request, env) {
  const body = await request.json().catch(function() { return null; });
  if (!body || !body.token || !body.password) return error("Token e password obrigatórios");
  if (body.password.length < 6) return error("Password deve ter pelo menos 6 caracteres");
  const resetData = await env.IPC_USERS.get("reset:" + body.token);
  if (!resetData) return error("Token inválido ou expirado", 400);
  const userId   = JSON.parse(resetData).userId;
  const userData = await env.IPC_USERS.get("user:" + userId);
  if (!userData) return error("Utilizador não encontrado", 404);
  const user = JSON.parse(userData);
  user.passwordHash = await hashPassword(body.password);
  await env.IPC_USERS.put("user:" + userId, JSON.stringify(user));
  await env.IPC_USERS.delete("reset:" + body.token);
  return json({ success: true, message: "Password atualizada com sucesso." });
}

// ─── User ─────────────────────────────────────────────────────────────────────

async function handleGetMe(request, env) {
  const payload = await getAuthUser(request, env);
  if (!payload) return error("Não autenticado", 401);
  const userData = await env.IPC_USERS.get("user:" + payload.id);
  if (!userData) return error("Utilizador não encontrado", 404);
  const user = JSON.parse(userData);
  return json({
    id: user.id,
    name: user.name,
    email: user.email,
    avatar: user.avatar || null,
    preferences: user.preferences || {},
    stats: user.stats || {},
    createdAt: user.createdAt,
  });
}

async function handleUpdateMe(request, env) {
  const payload = await getAuthUser(request, env);
  if (!payload) return error("Não autenticado", 401);
  const body = await request.json().catch(function() { return null; });
  if (!body) return error("Body inválido");
  const userData = await env.IPC_USERS.get("user:" + payload.id);
  if (!userData) return error("Utilizador não encontrado", 404);
  const user = JSON.parse(userData);
  if (body.name) user.name = body.name.trim();
  if (body.password) {
    if (body.password.length < 6) return error("Password deve ter pelo menos 6 caracteres");
    user.passwordHash = await hashPassword(body.password);
  }
  // preferências: language, theme, fontSize, defaultModel
  if (body.preferences && typeof body.preferences === "object") {
    user.preferences = Object.assign({}, user.preferences || {}, body.preferences);
  }
  await env.IPC_USERS.put("user:" + user.id, JSON.stringify(user));
  return json({ id: user.id, name: user.name, email: user.email, avatar: user.avatar || null, preferences: user.preferences || {}, createdAt: user.createdAt });
}

// Avatar: base64 string guardado no KV (simples, sem R2)
async function handleUpdateAvatar(request, env) {
  const payload = await getAuthUser(request, env);
  if (!payload) return error("Não autenticado", 401);
  const body = await request.json().catch(function() { return null; });
  if (!body || !body.avatar) return error("avatar obrigatório");
  // Limitar tamanho: ~200KB base64
  if (body.avatar.length > 270000) return error("Imagem demasiado grande (máx ~200KB)");
  const userData = await env.IPC_USERS.get("user:" + payload.id);
  if (!userData) return error("Utilizador não encontrado", 404);
  const user = JSON.parse(userData);
  user.avatar = body.avatar;
  await env.IPC_USERS.put("user:" + user.id, JSON.stringify(user));
  return json({ avatar: user.avatar });
}

// ─── Conversations ────────────────────────────────────────────────────────────

async function handleListConversations(request, env) {
  const payload = await getAuthUser(request, env);
  if (!payload) return error("Não autenticado", 401);
  const url      = new URL(request.url);
  const archived = url.searchParams.get("archived") === "true";
  const raw      = await env.IPC_USERS.get("convs:" + payload.id);
  const ids      = raw ? JSON.parse(raw) : [];
  const all = await Promise.all(ids.map(async function(id) {
    const data = await env.IPC_USERS.get("conv:" + id);
    return data ? JSON.parse(data) : null;
  }));
  const conversations = all
    .filter(function(c) { return c !== null && (archived ? c.archived === true : !c.archived); })
    .sort(function(a, b) {
      // pinned primeiro, depois por updatedAt
      if (a.pinned && !b.pinned) return -1;
      if (!a.pinned && b.pinned) return 1;
      return b.updatedAt - a.updatedAt;
    });
  return json({ conversations: conversations });
}

async function handleCreateConversation(request, env) {
  const payload = await getAuthUser(request, env);
  if (!payload) return error("Não autenticado", 401);
  const body = await request.json().catch(function() { return null; });
  if (!body) return error("Body inválido");
  const id  = crypto.randomUUID();
  const now = Date.now();
  const conversation = {
    id: id,
    userId: payload.id,
    title: body.title || "Nova conversa",
    messages: body.messages || [],
    model: body.model || GEMINI_MODEL,
    pinned: false,
    archived: false,
    tags: body.tags || [],
    createdAt: now,
    updatedAt: now,
  };
  await env.IPC_USERS.put("conv:" + id, JSON.stringify(conversation));
  const raw = await env.IPC_USERS.get("convs:" + payload.id);
  const ids = raw ? JSON.parse(raw) : [];
  ids.unshift(id);
  await env.IPC_USERS.put("convs:" + payload.id, JSON.stringify(ids));
  // atualizar stats
  await incrementUserStat(env, payload.id, "totalConversations", 1);
  return json(conversation, 201);
}

async function handleGetConversation(request, env) {
  const payload = await getAuthUser(request, env);
  if (!payload) return error("Não autenticado", 401);
  const id   = new URL(request.url).pathname.split("/").pop();
  const data = await env.IPC_USERS.get("conv:" + id);
  if (!data) return error("Conversa não encontrada", 404);
  const conversation = JSON.parse(data);
  if (conversation.userId !== payload.id) return error("Acesso negado", 403);
  return json(conversation);
}

async function handleUpdateConversation(request, env) {
  const payload = await getAuthUser(request, env);
  if (!payload) return error("Não autenticado", 401);
  const id   = new URL(request.url).pathname.split("/").pop();
  const data = await env.IPC_USERS.get("conv:" + id);
  if (!data) return error("Conversa não encontrada", 404);
  const conversation = JSON.parse(data);
  if (conversation.userId !== payload.id) return error("Acesso negado", 403);
  const body = await request.json().catch(function() { return null; });
  if (!body) return error("Body inválido");
  if (body.title    !== undefined) conversation.title    = body.title;
  if (body.messages !== undefined) {
    // atualizar stat de mensagens
    const added = body.messages.length - conversation.messages.length;
    if (added > 0) await incrementUserStat(env, payload.id, "totalMessages", added);
    conversation.messages = body.messages;
  }
  if (body.model    !== undefined) conversation.model    = body.model;
  if (body.tags     !== undefined) conversation.tags     = body.tags;
  conversation.updatedAt = Date.now();
  await env.IPC_USERS.put("conv:" + id, JSON.stringify(conversation));
  return json(conversation);
}

async function handleDeleteConversation(request, env) {
  const payload = await getAuthUser(request, env);
  if (!payload) return error("Não autenticado", 401);
  const id   = new URL(request.url).pathname.split("/").pop();
  const data = await env.IPC_USERS.get("conv:" + id);
  if (!data) return error("Conversa não encontrada", 404);
  const conversation = JSON.parse(data);
  if (conversation.userId !== payload.id) return error("Acesso negado", 403);
  await env.IPC_USERS.delete("conv:" + id);
  const raw     = await env.IPC_USERS.get("convs:" + payload.id);
  const ids     = raw ? JSON.parse(raw) : [];
  const updated = ids.filter(function(i) { return i !== id; });
  await env.IPC_USERS.put("convs:" + payload.id, JSON.stringify(updated));
  await incrementUserStat(env, payload.id, "totalConversations", -1);
  return json({ success: true });
}

// Eliminar TODAS as conversas do utilizador
async function handleDeleteAllConversations(request, env) {
  const payload = await getAuthUser(request, env);
  if (!payload) return error("Não autenticado", 401);
  const raw = await env.IPC_USERS.get("convs:" + payload.id);
  const ids = raw ? JSON.parse(raw) : [];
  await Promise.all(ids.map(function(id) { return env.IPC_USERS.delete("conv:" + id); }));
  await env.IPC_USERS.put("convs:" + payload.id, JSON.stringify([]));
  // reset stat
  const userData = await env.IPC_USERS.get("user:" + payload.id);
  if (userData) {
    const user = JSON.parse(userData);
    if (user.stats) user.stats.totalConversations = 0;
    await env.IPC_USERS.put("user:" + payload.id, JSON.stringify(user));
  }
  return json({ success: true, deleted: ids.length });
}

// Pin / Unpin
async function handlePinConversation(request, env) {
  const payload = await getAuthUser(request, env);
  if (!payload) return error("Não autenticado", 401);
  const parts = new URL(request.url).pathname.split("/");
  const id    = parts[2]; // /conversations/{id}/pin
  const data  = await env.IPC_USERS.get("conv:" + id);
  if (!data) return error("Conversa não encontrada", 404);
  const conversation = JSON.parse(data);
  if (conversation.userId !== payload.id) return error("Acesso negado", 403);
  const body = await request.json().catch(function() { return {}; });
  conversation.pinned    = body.pinned !== undefined ? body.pinned : !conversation.pinned;
  conversation.updatedAt = Date.now();
  await env.IPC_USERS.put("conv:" + id, JSON.stringify(conversation));
  return json({ id: id, pinned: conversation.pinned });
}

// Archive / Unarchive
async function handleArchiveConversation(request, env) {
  const payload = await getAuthUser(request, env);
  if (!payload) return error("Não autenticado", 401);
  const parts = new URL(request.url).pathname.split("/");
  const id    = parts[2]; // /conversations/{id}/archive
  const data  = await env.IPC_USERS.get("conv:" + id);
  if (!data) return error("Conversa não encontrada", 404);
  const conversation = JSON.parse(data);
  if (conversation.userId !== payload.id) return error("Acesso negado", 403);
  const body = await request.json().catch(function() { return {}; });
  conversation.archived  = body.archived !== undefined ? body.archived : !conversation.archived;
  conversation.pinned    = false; // arquivada não pode estar pinned
  conversation.updatedAt = Date.now();
  await env.IPC_USERS.put("conv:" + id, JSON.stringify(conversation));
  return json({ id: id, archived: conversation.archived });
}

// Pesquisa de conversas por texto
async function handleSearchConversations(request, env) {
  const payload = await getAuthUser(request, env);
  if (!payload) return error("Não autenticado", 401);
  const url   = new URL(request.url);
  const query = (url.searchParams.get("q") || "").toLowerCase().trim();
  if (!query) return json({ conversations: [] });
  const raw = await env.IPC_USERS.get("convs:" + payload.id);
  const ids = raw ? JSON.parse(raw) : [];
  const all = await Promise.all(ids.map(async function(id) {
    const data = await env.IPC_USERS.get("conv:" + id);
    return data ? JSON.parse(data) : null;
  }));
  const results = all.filter(function(c) {
    if (!c || c.archived) return false;
    if (c.title.toLowerCase().includes(query)) return true;
    // pesquisar também no conteúdo das mensagens
    return c.messages.some(function(m) {
      return m.content && m.content.toLowerCase().includes(query);
    });
  }).sort(function(a, b) { return b.updatedAt - a.updatedAt; });
  return json({ conversations: results });
}

// ─── AI ───────────────────────────────────────────────────────────────────────

async function handleAiTitle(request, env) {
  const payload = await getAuthUser(request, env);
  if (!payload) return error("Não autenticado", 401);
  const body = await request.json().catch(function() { return null; });
  if (!body || !body.message) return error("message obrigatório");
  const title = await geminiGenerateTitle(env.GEMINI_API_KEY, body.message, body.language || "pt");
  return json({ title: title });
}

async function handleAiChat(request, env) {
  const payload = await getAuthUser(request, env);
  if (!payload) return error("Não autenticado", 401);
  const body = await request.json().catch(function() { return null; });
  if (!body || !body.messages) return error("Messages obrigatório");

  const messages       = body.messages;
  const stream         = body.stream !== undefined ? body.stream : false;
  const language       = body.language || "pt";
  const thinkingBudget = body.think ? 8000 : 0; // tokens de raciocínio quando think=true

  const gemRes = await geminiGenerate(env.GEMINI_API_KEY, messages, language, stream, thinkingBudget);

  if (!gemRes.ok) {
    const errText = await gemRes.text();
    console.error("[CHAT ERROR]", gemRes.status, errText);
    return error("Erro Gemini API: " + errText, gemRes.status);
  }

  if (stream) {
    // passar o SSE stream diretamente para o cliente
    return new Response(gemRes.body, {
      headers: Object.assign({}, CORS_HEADERS, {
        "Content-Type": "text/event-stream",
        "Cache-Control": "no-cache",
        "X-Accel-Buffering": "no",
      }),
    });
  }

  // resposta não-stream
  const data = await gemRes.json();
  const candidate = data.candidates?.[0];
  const parts     = candidate?.content?.parts || [];
  // Gemini pode devolver thinking + text em parts separados
  let content   = "";
  let reasoning = null;
  for (const part of parts) {
    if (part.thought) {
      reasoning = part.text;
    } else {
      content += part.text || "";
    }
  }
  return json({
    content: content,
    reasoning: reasoning,
    model: GEMINI_MODEL,
    usage: data.usageMetadata || null,
  });
}

// Resumir uma conversa longa (útil para comprimir histórico no cliente)
async function handleAiSummarize(request, env) {
  const payload = await getAuthUser(request, env);
  if (!payload) return error("Não autenticado", 401);
  const body = await request.json().catch(function() { return null; });
  if (!body || !body.messages) return error("messages obrigatório");
  const language = body.language || "pt";
  const prompt   = language === "en"
    ? "Summarize the following conversation in a few sentences, keeping the main points and context:\n\n"
    : "Resume a seguinte conversa em poucas frases, mantendo os pontos principais e o contexto:\n\n";
  const text = body.messages.map(function(m) {
    return (m.role === "user" ? "User: " : "Assistant: ") + m.content;
  }).join("\n");
  const gemRes = await fetch(GEMINI_BASE + "/" + GEMINI_MODEL + ":generateContent?key=" + env.GEMINI_API_KEY, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      contents: [{ role: "user", parts: [{ text: prompt + text }] }],
      generationConfig: { maxOutputTokens: 512, temperature: 0.5 },
    }),
  });
  if (!gemRes.ok) return error("Erro ao resumir", gemRes.status);
  const data    = await gemRes.json();
  const summary = data.candidates?.[0]?.content?.parts?.[0]?.text || "";
  return json({ summary: summary });
}

// ─── Utils ────────────────────────────────────────────────────────────────────

async function incrementUserStat(env, userId, stat, delta) {
  try {
    const userData = await env.IPC_USERS.get("user:" + userId);
    if (!userData) return;
    const user = JSON.parse(userData);
    if (!user.stats) user.stats = {};
    user.stats[stat] = (user.stats[stat] || 0) + delta;
    if (user.stats[stat] < 0) user.stats[stat] = 0;
    await env.IPC_USERS.put("user:" + userId, JSON.stringify(user));
  } catch (e) {
    console.error("[STAT ERROR]", e);
  }
}