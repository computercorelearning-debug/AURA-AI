package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

object GeminiModels {
    const val FLASH = "gemini-3.5-flash"
    const val PRO = "gemini-3.1-pro-preview"
    const val LITE = "gemini-3.1-flash-lite-preview"

    val AVAILABLE_MODELS = listOf(
        ModelOption(
            id = FLASH,
            displayName = "Gemini 3.5 Flash",
            badge = "Recommended",
            description = "High intelligence, lightning fast response speed."
        ),
        ModelOption(
            id = PRO,
            displayName = "Gemini 3.1 Pro",
            badge = "Deep Reasoning",
            description = "Advanced STEM, multi-step reasoning, and coding precision."
        ),
        ModelOption(
            id = LITE,
            displayName = "Gemini 3.1 Flash Lite",
            badge = "Ultra Lightweight",
            description = "Optimized for quick queries and maximum efficiency."
        )
    )
}

data class ModelOption(
    val id: String,
    val displayName: String,
    val badge: String,
    val description: String
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val role: String? = null,
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val maxOutputTokens: Int? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null,
    val promptFeedback: PromptFeedback? = null,
    val error: ApiErrorDetail? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class PromptFeedback(
    val blockReason: String? = null
)

@JsonClass(generateAdapter = true)
data class ApiErrorDetail(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)
