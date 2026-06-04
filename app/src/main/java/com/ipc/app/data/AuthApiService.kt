// AuthApiService.kt
// Mock local — substitui por chamadas HTTP reais quando a API estiver pronta.
// Basta mudar USE_MOCK = false e preencher BASE_URL.
package com.ipc.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class AuthUser(
    val id: String,
    val name: String,
    val email: String,
    val token: String
)

sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val message: String) : AuthResult<Nothing>()
}

object AuthApiService {

    // ── Configuração ──────────────────────────────────────────────────────────
    private const val USE_MOCK = true
    private const val BASE_URL = "https://api.exemplo.com" // substituir quando real

    // Simula uma base de dados local para o mock
    private val mockUsers = mutableListOf(
        Triple("mock-id-1", "Utilizador Demo", "demo@ipc.app") to "demo123"
    )

    // ── Login ─────────────────────────────────────────────────────────────────
    suspend fun login(email: String, password: String): AuthResult<AuthUser> =
        withContext(Dispatchers.IO) {
            if (USE_MOCK) mockLogin(email, password)
            else realLogin(email, password)
        }

    // ── Registo ───────────────────────────────────────────────────────────────
    suspend fun register(name: String, email: String, password: String): AuthResult<AuthUser> =
        withContext(Dispatchers.IO) {
            if (USE_MOCK) mockRegister(name, email, password)
            else realRegister(name, email, password)
        }

    // ── Logout ────────────────────────────────────────────────────────────────
    suspend fun logout(token: String): AuthResult<Unit> =
        withContext(Dispatchers.IO) {
            if (USE_MOCK) mockLogout()
            else realLogout(token)
        }

    // ── Mock implementations ──────────────────────────────────────────────────

    private suspend fun mockLogin(email: String, password: String): AuthResult<AuthUser> {
        delay(900) // simula latência de rede
        val entry = mockUsers.find { it.first.second == email }
            ?: return AuthResult.Error("Email ou password incorretos.")
        if (entry.second != password)
            return AuthResult.Error("Email ou password incorretos.")
        return AuthResult.Success(
            AuthUser(
                id    = entry.first.first,
                name  = entry.first.third,
                email = email,
                token = "mock-token-${System.currentTimeMillis()}"
            )
        )
    }

    private suspend fun mockRegister(
        name: String, email: String, password: String
    ): AuthResult<AuthUser> {
        delay(1100)
        if (mockUsers.any { it.first.second == email })
            return AuthResult.Error("Este email já está registado.")
        val newId = "mock-id-${mockUsers.size + 1}"
        mockUsers.add(Triple(newId, name, email) to password)
        return AuthResult.Success(
            AuthUser(
                id    = newId,
                name  = name,
                email = email,
                token = "mock-token-${System.currentTimeMillis()}"
            )
        )
    }

    private suspend fun mockLogout(): AuthResult<Unit> {
        delay(300)
        return AuthResult.Success(Unit)
    }

    // ── Real API implementations (preencher quando a API estiver pronta) ──────

    private suspend fun realLogin(email: String, password: String): AuthResult<AuthUser> {
        // TODO: POST $BASE_URL/auth/login
        // val response = httpClient.post("$BASE_URL/auth/login") { ... }
        return AuthResult.Error("API real não configurada.")
    }

    private suspend fun realRegister(
        name: String, email: String, password: String
    ): AuthResult<AuthUser> {
        // TODO: POST $BASE_URL/auth/register
        return AuthResult.Error("API real não configurada.")
    }

    private suspend fun realLogout(token: String): AuthResult<Unit> {
        // TODO: POST $BASE_URL/auth/logout com Authorization: Bearer $token
        return AuthResult.Error("API real não configurada.")
    }
}