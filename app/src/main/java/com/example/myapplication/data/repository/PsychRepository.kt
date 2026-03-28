// ═══════════════════════════════════════════════════════════════
// Repository for Psychologist chat — single source of truth
// ═══════════════════════════════════════════════════════════════

package com.example.myapplication.data.repository

import com.example.myapplication.data.model.*
import com.example.myapplication.data.remote.PsychNetworkModule
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed class PsychResult<out T> {
    data class Success<T>(val data: T) : PsychResult<T>()
    data class Error(val message: String, val code: Int = 0) : PsychResult<Nothing>()
    data object Loading : PsychResult<Nothing>()
}

class PsychRepository {

    private val api = PsychNetworkModule.getApi()
    private val tokenManager = PsychNetworkModule.tokenManager

    // ── Auth ─────────────────────────────────────────────

    suspend fun register(username: String, password: String, displayName: String?): PsychResult<PsychTokenResponse> {
        return try {
            val response = api.register(PsychRegisterRequest(username, password, displayName))
            if (response.isSuccessful && response.body() != null) {
                val tokens = response.body()!!
                tokenManager.saveTokens(tokens)
                tokenManager.username = username
                PsychResult.Success(tokens)
            } else {
                PsychResult.Error(
                    response.errorBody()?.string() ?: "Registration failed",
                    response.code()
                )
            }
        } catch (e: SocketTimeoutException) {
            PsychResult.Error("Server took too long to respond")
        } catch (e: UnknownHostException) {
            PsychResult.Error("Cannot reach server")
        } catch (e: Exception) {
            PsychResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun login(username: String, password: String): PsychResult<PsychTokenResponse> {
        return try {
            val response = api.login(PsychLoginRequest(username, password))
            if (response.isSuccessful && response.body() != null) {
                val tokens = response.body()!!
                tokenManager.saveTokens(tokens)
                tokenManager.username = username
                PsychResult.Success(tokens)
            } else {
                PsychResult.Error(
                    response.errorBody()?.string() ?: "Login failed",
                    response.code()
                )
            }
        } catch (e: SocketTimeoutException) {
            PsychResult.Error("Server took too long to respond")
        } catch (e: UnknownHostException) {
            PsychResult.Error("Cannot reach server")
        } catch (e: Exception) {
            PsychResult.Error(e.message ?: "Network error")
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
    ): PsychResult<PsychChatMessageResponse> {
        return try {
            val response = api.sendMessage(PsychChatMessageRequest(message, sessionId))
            if (response.isSuccessful && response.body() != null) {
                PsychResult.Success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string()
                PsychResult.Error(errorBody ?: "Failed to send message", response.code())
            }
        } catch (e: SocketTimeoutException) {
            PsychResult.Error("Server took too long to respond")
        } catch (e: UnknownHostException) {
            PsychResult.Error("Cannot reach server")
        } catch (e: Exception) {
            PsychResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun createSession(): PsychResult<PsychSessionCreateResponse> {
        return try {
            val response = api.createSession()
            if (response.isSuccessful && response.body() != null) {
                PsychResult.Success(response.body()!!)
            } else {
                PsychResult.Error("Failed to create session", response.code())
            }
        } catch (e: SocketTimeoutException) {
            PsychResult.Error("Server took too long to respond")
        } catch (e: UnknownHostException) {
            PsychResult.Error("Cannot reach server")
        } catch (e: Exception) {
            PsychResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun listSessions(): PsychResult<PsychSessionListResponse> {
        return try {
            val response = api.listSessions()
            if (response.isSuccessful && response.body() != null) {
                PsychResult.Success(response.body()!!)
            } else {
                PsychResult.Error("Failed to list sessions", response.code())
            }
        } catch (e: SocketTimeoutException) {
            PsychResult.Error("Server took too long to respond")
        } catch (e: UnknownHostException) {
            PsychResult.Error("Cannot reach server")
        } catch (e: Exception) {
            PsychResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun deleteSession(sessionId: String): PsychResult<Boolean> {
        return try {
            val response = api.deleteSession(sessionId)
            if (response.isSuccessful) {
                PsychResult.Success(true)
            } else {
                PsychResult.Error("Failed to delete session", response.code())
            }
        } catch (e: SocketTimeoutException) {
            PsychResult.Error("Server took too long to respond")
        } catch (e: UnknownHostException) {
            PsychResult.Error("Cannot reach server")
        } catch (e: Exception) {
            PsychResult.Error(e.message ?: "Network error")
        }
    }

    // ── Feedback ─────────────────────────────────────────

    suspend fun submitFeedback(
        sessionId: String,
        turn: Int,
        rating: String,
        comment: String? = null
    ): PsychResult<PsychFeedbackResponse> {
        return try {
            val response = api.submitFeedback(
                PsychFeedbackRequest(sessionId, turn, rating, comment)
            )
            if (response.isSuccessful && response.body() != null) {
                PsychResult.Success(response.body()!!)
            } else {
                PsychResult.Error("Failed to submit feedback", response.code())
            }
        } catch (e: SocketTimeoutException) {
            PsychResult.Error("Server took too long to respond")
        } catch (e: UnknownHostException) {
            PsychResult.Error("Cannot reach server")
        } catch (e: Exception) {
            PsychResult.Error(e.message ?: "Network error")
        }
    }

    // ── Health ───────────────────────────────────────────

    suspend fun healthCheck(): PsychResult<PsychHealthResponse> {
        return try {
            val response = api.healthCheck()
            if (response.isSuccessful && response.body() != null) {
                PsychResult.Success(response.body()!!)
            } else {
                PsychResult.Error("Server unhealthy", response.code())
            }
        } catch (e: SocketTimeoutException) {
            PsychResult.Error("Server took too long to respond")
        } catch (e: UnknownHostException) {
            PsychResult.Error("Cannot reach server")
        } catch (e: Exception) {
            PsychResult.Error(e.message ?: "Cannot reach server")
        }
    }
}
