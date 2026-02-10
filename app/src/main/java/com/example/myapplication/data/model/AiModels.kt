package com.example.myapplication.data.model

import com.google.gson.annotations.SerializedName

data class AiRequest(
    @SerializedName("model") val model: String = "deepseek/deepseek-r1-0528:free",
    @SerializedName("messages") val messages: List<Message>
)

data class Message(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)

data class AiResponse(
    @SerializedName("choices") val choices: List<Choice>?,
    @SerializedName("error") val error: AiError? = null
)

data class AiError(
    @SerializedName("message") val message: String?,
    @SerializedName("code") val code: Int? = null
)

data class Choice(
    @SerializedName("message") val message: Message?
)
