package dev.sidk.scrubber.tv

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Recap {
    /** Blocking; call off the main thread. */
    fun fetch(url: String, title: String, position: String, duration: String, transcript: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 30_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            if (BuildConfig.RECAP_TOKEN.isNotBlank()) conn.setRequestProperty("x-recap-token", BuildConfig.RECAP_TOKEN)
            val body = JSONObject().put("title", title).put("position", position)
                .put("duration", duration).put("transcript", transcript)
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = try { JSONObject(text) } catch (e: Exception) { throw Exception("Recap service returned HTTP ${conn.responseCode}") }
            if (json.has("error")) throw Exception(json.getString("error"))
            return json.optString("recap").ifBlank { throw Exception("Recap service returned no recap") }
        } finally {
            conn.disconnect()
        }
    }
}
