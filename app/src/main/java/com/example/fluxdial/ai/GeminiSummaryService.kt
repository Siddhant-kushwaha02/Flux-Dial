package com.example.fluxdial.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class CallSummaryResult(
    val summary: String,
    val actionItems: List<String>,
    val keyPoints: List<String>
)

object GeminiSummaryService {

    // Gemini 1.5 Flash — free tier, 15 req/min, 1M tokens/day
    private const val API_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/" +
        "gemini-1.5-flash:generateContent"

    suspend fun summarise(
        transcript: String,
        apiKey: String
    ): CallSummaryResult = withContext(Dispatchers.IO) {

        if (transcript.isBlank()) return@withContext CallSummaryResult(
            "Listening...", emptyList(), emptyList()
        )

        val prompt = """
            You are an AI call assistant. Analyse the following transcript from a phone call.
            Provide a summary, action items, and key points in JSON format.
            
            IMPORTANT: Return ONLY the JSON object. Do not include any other text or markdown.
            
            JSON structure:
            {
              "summary": "Concise summary",
              "action_items": ["item 1", "item 2"],
              "key_points": ["point 1", "point 2"]
            }
            
            If transcript is empty or too short, return:
            {"summary": "Listening for conversation...", "action_items": [], "key_points": []}
            
            Transcript:
            $transcript
        """.trimIndent()

        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("maxOutputTokens", 512)
            })
        }.toString()

        return@withContext try {
            val url = URL("$API_URL?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.outputStream.write(requestBody.toByteArray())

            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val json = JSONObject(response)
            var text = json
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
                
            // Handle cases where Gemini might still return markdown backticks
            if (text.startsWith("```json")) {
                text = text.substringAfter("```json").substringBeforeLast("```").trim()
            } else if (text.startsWith("```")) {
                text = text.substringAfter("```").substringBeforeLast("```").trim()
            }

            val result = JSONObject(text)
            val actionItems = mutableListOf<String>()
            val keyPoints = mutableListOf<String>()

            result.optJSONArray("action_items")?.let { arr ->
                for (i in 0 until arr.length()) {
                    actionItems.add(arr.getString(i))
                }
            }
            result.optJSONArray("key_points")?.let { arr ->
                for (i in 0 until arr.length()) {
                    keyPoints.add(arr.getString(i))
                }
            }

            CallSummaryResult(
                summary = result.optString("summary", "Summarising..."),
                actionItems = actionItems,
                keyPoints = keyPoints
            )
        } catch (e: java.io.FileNotFoundException) {
            e.printStackTrace()
            CallSummaryResult(
                summary = "API Error: Invalid Key or URL.",
                actionItems = emptyList(),
                keyPoints = emptyList()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            CallSummaryResult(
                summary = "Error: ${e.message ?: "Unknown error"}",
                actionItems = emptyList(),
                keyPoints = emptyList()
            )
        }
    }
}
