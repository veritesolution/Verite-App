// ═══════════════════════════════════════════════════════════════
// File: data/repository/VeriteRepository.kt
// Repository pattern — single source of truth for chat data
// Add to: app/src/main/java/com/yourapp/data/repository/
// ═══════════════════════════════════════════════════════════════

package com.yourapp.data.repository

import com.yourapp.data.model.*
import com.yourapp.data.remote.NetworkModule

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val code: Int = 0) : Result<Nothing>()
    data object Loading : Result<Nothing>()
}

class VeriteRepository {

    private val api = NetworkModule.getApi()
    private val tokenManager = NetworkModule.tokenManager

    // ── Auth ─────────────────────────────────────────────

    suspend fun register(username: String, password: String, displayName: String?): Result<TokenResponse> {
        return try {
            val response = api.register(RegisterRequest(username, password, displayName))
            if (response.isSuccessful && response.body() != null) {
                val tokens = response.body()!!
                tokenManager.saveTokens(tokens)
                tokenManager.username = username
                Result.Success(tokens)
            } else {
                Result.Error(
                    response.errorBody()?.string() ?: "Registration failed",
                    response.code()
                )
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun login(username: String, password: String): Result<TokenResponse> {
        return try {
            val response = api.login(LoginRequest(username, password))
            if (response.isSuccessful && response.body() != null) {
                val tokens = response.body()!!
                tokenManager.saveTokens(tokens)
                tokenManager.username = username
                Result.Success(tokens)
            } else {
                Result.Error(
                    response.errorBody()?.string() ?: "Login failed",
                    response.code()
                )
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    fun logout() {
        tokenManager.clear()
    }

    val isLoggedIn: Boolean
        get() = tokenManager.isLoggedIn

    // ── Chat ─────────────────────────────────────────────

    suspend fun sendMessage(
        message: String,
        sessionId: String? = null
    ): Result<ChatMessageResponse> {
        return try {
            val response = api.sendMessage(ChatMessageRequest(message, sessionId))
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                // Check if it's a crisis response (different schema)
                val errorBody = response.errorBody()?.string()
                Result.Error(errorBody ?: "Failed to send message", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun createSession(): Result<SessionCreateResponse> {
        return try {
            val response = api.createSession()
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("Failed to create session", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun listSessions(): Result<SessionListResponse> {
        return try {
            val response = api.listSessions()
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("Failed to list sessions", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun deleteSession(sessionId: String): Result<Boolean> {
        return try {
            val response = api.deleteSession(sessionId)
            if (response.isSuccessful) {
                Result.Success(true)
            } else {
                Result.Error("Failed to delete session", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    // ── Feedback ─────────────────────────────────────────

    suspend fun submitFeedback(
        sessionId: String,
        turn: Int,
        rating: String,
        comment: String? = null
    ): Result<FeedbackResponse> {
        return try {
            val response = api.submitFeedback(
                FeedbackRequest(sessionId, turn, rating, comment)
            )
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("Failed to submit feedback", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    // ── Health ───────────────────────────────────────────

    suspend fun healthCheck(): Result<HealthResponse> {
        return try {
            val response = api.healthCheck()
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("Server unhealthy", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Cannot reach server")
        }
    }
}
