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

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { headers: CORS_HEADERS });

    const url  = new URL(request.url);
    const path = url.pathname;

    if (path === "/auth/register"               && request.method === "POST")   return handleRegister(request, env);
    if (path === "/auth/login"                  && request.method === "POST")   return handleLogin(request, env);
    if (path === "/auth/logout"                 && request.method === "POST")   return handleLogout(request, env);
    if (path === "/auth/forgot-password"        && request.method === "POST")   return handleForgotPassword(request, env);
    if (path === "/auth/reset-password"         && request.method === "POST")   return handleResetPassword(request, env);
    if (path === "/user/me"                     && request.method === "GET")    return handleGetMe(request, env);
    if (path === "/user/me"                     && request.method === "PUT")    return handleUpdateMe(request, env);
    if (path === "/ai/chat"                     && request.method === "POST")   return handleAiChat(request, env);
    if (path === "/ai/title"                    && request.method === "POST")   return handleAiTitle(request, env);
    if (path === "/conversations"               && request.method === "GET")    return handleListConversations(request, env);
    if (path === "/conversations"               && request.method === "POST")   return handleCreateConversation(request, env);
    if (path.match(/^\/conversations\/[^\/]+$/) && request.method === "GET")    return handleGetConversation(request, env);
    if (path.match(/^\/conversations\/[^\/]+$/) && request.method === "PUT")    return handleUpdateConversation(request, env);
    if (path.match(/^\/conversations\/[^\/]+$/) && request.method === "DELETE") return handleDeleteConversation(request, env);

    return error("Not found", 404);
  },
};

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
  const user         = { id: id, name: name, email: email.toLowerCase(), passwordHash: passwordHash, createdAt: Date.now() };
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
  return json({ token: token, id: user.id, name: user.name, email: user.email });
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

async function handleGetMe(request, env) {
  const payload = await getAuthUser(request, env);
  if (!payload) return error("Não autenticado", 401);
  const userData = await env.IPC_USERS.get("user:" + payload.id);
  if (!userData) return error("Utilizador não encontrado", 404);
  const user = JSON.parse(userData);
  return json({ id: user.id, name: user.name, email: user.email, createdAt: user.createdAt });
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
  await env.IPC_USERS.put("user:" + user.id, JSON.stringify(user));
  return json({ id: user.id, name: user.name, email: user.email, createdAt: user.createdAt });
}

async function handleListConversations(request, env) {
  const payload = await getAuthUser(request, env);
  if (!payload) return error("Não autenticado", 401);
  const raw = await env.IPC_USERS.get("convs:" + payload.id);
  const ids = raw ? JSON.parse(raw) : [];
  const all = await Promise.all(ids.map(async function(id) {
    const data = await env.IPC_USERS.get("conv:" + id);
    return data ? JSON.parse(data) : null;
  }));
  const conversations = all.filter(function(c) { return c !== null; }).sort(function(a, b) { return b.updatedAt - a.updatedAt; });
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
    createdAt: now,
    updatedAt: now,
  };
  await env.IPC_USERS.put("conv:" + id, JSON.stringify(conversation));
  const raw = await env.IPC_USERS.get("convs:" + payload.id);
  const ids = raw ? JSON.parse(raw) : [];
  ids.unshift(id);
  await env.IPC_USERS.put("convs:" + payload.id, JSON.stringify(ids));
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
  if (body.messages !== undefined) conversation.messages = body.messages;
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
  return json({ success: true });
}

async function handleAiTitle(request, env) {
  const payload = await getAuthUser(request, env);
  if (!payload) return error("Não autenticado", 401);
  const body = await request.json().catch(function() { return null; });
  if (!body || !body.message) return error("message obrigatório");
  const message  = body.message;
  const language = body.language || "pt";
  const prompt = language === "en"
    ? "Generate a short title (max 5 words) for a conversation that starts with: \"" + message + "\". Reply with ONLY the title, no punctuation, no quotes."
    : "Gera um titulo curto (max 5 palavras) para uma conversa que comeca com: \"" + message + "\". Responde APENAS com o titulo, sem pontuacao, sem aspas.";
  const nvidiaRes = await fetch("https://integrate.api.nvidia.com/v1/chat/completions", {
    method: "POST",
    headers: { "Content-Type": "application/json", "Authorization": "Bearer " + env.NVIDIA_API_KEY },
    body: JSON.stringify({
      model: "deepseek-ai/deepseek-v4-pro",
      messages: [{ role: "user", content: prompt }],
      temperature: 0.5,
      max_tokens: 20,
      stream: false,
      extra_body: { chat_template_kwargs: { thinking: false } },
    }),
  });
  if (!nvidiaRes.ok) {
    const errText = await nvidiaRes.text();
    console.error("[TITLE ERROR]", nvidiaRes.status, errText);
    return error("Erro ao gerar titulo", nvidiaRes.status);
  }
  const data  = await nvidiaRes.json();
  const title = data.choices && data.choices[0] && data.choices[0].message && data.choices[0].message.content
    ? data.choices[0].message.content.trim()
    : "Nova conversa";
  return json({ title: title.slice(0, 40) });
}

async function handleAiChat(request, env) {
  const payload = await getAuthUser(request, env);
  if (!payload) return error("Não autenticado", 401);
  const body = await request.json().catch(function() { return null; });
  if (!body || !body.messages) return error("Messages obrigatório");
  const messages     = body.messages;
  const stream       = body.stream   !== undefined ? body.stream : false;
  const language     = body.language || "pt";
  const think        = body.think    || false;
  const systemPrompt = language === "en"
    ? "You are a helpful AI assistant. Always respond in English. Be concise and direct. When the user asks for a table, use markdown table format."
    : "Es um assistente de IA util. Responde sempre em portugues europeu. Se conciso e direto. Quando o utilizador pedir uma tabela, usa formato de tabela markdown.";
  const allMessages = [{ role: "system", content: systemPrompt }].concat(messages);

  const nvidiaRes = await fetch("https://integrate.api.nvidia.com/v1/chat/completions", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": "Bearer " + env.NVIDIA_API_KEY,
      "Accept": stream ? "text/event-stream" : "application/json",
    },
    body: JSON.stringify({
      model: "deepseek-ai/deepseek-v4-pro",
      messages: allMessages,
      temperature: 1,
      top_p: 0.95,
      max_tokens: 16384,
      extra_body: { chat_template_kwargs: { thinking: think } },
      stream: stream,
    }),
  });

  if (!nvidiaRes.ok) {
    const errText = await nvidiaRes.text();
    console.error("[CHAT ERROR]", nvidiaRes.status, errText);
    return error("Erro NVIDIA API: " + errText, nvidiaRes.status);
  }

  if (stream) {
    return new Response(nvidiaRes.body, {
      headers: Object.assign({}, CORS_HEADERS, {
        "Content-Type": "text/event-stream",
        "Cache-Control": "no-cache",
        "X-Accel-Buffering": "no",
      }),
    });
  }

  const data      = await nvidiaRes.json();
  const content   = data.choices && data.choices[0] && data.choices[0].message ? data.choices[0].message.content : "";
  const reasoning = data.choices && data.choices[0] && data.choices[0].message ? data.choices[0].message.reasoning_content : null;
  return json({ content: content, reasoning: reasoning, model: data.model, usage: data.usage });
}