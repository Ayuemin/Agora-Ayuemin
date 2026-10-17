package com.newoether.agora.tool.github

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject

/**
 * Repository-independent GitHub Contents API transport.
 *
 * Credentials are supplied per operation, never logged or persisted here.
 * The caller must enforce its repository allowlist and obtain confirmation for writes.
 * Writes use the current blob SHA to prevent silently overwriting concurrent changes.
 */
internal class GitHubContentsClient(
    private val http: OkHttpClient = OkHttpClient(),
) {
    private fun segment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun endpoint(repository: String, path: String): String {
        val parts = repository.split("/")
        require(parts.size == 2 && parts.all { it.matches(Regex("[A-Za-z0-9_.-]+")) }) {
            "Expected owner/repository"
        }
        require(path.isNotBlank() && !path.startsWith("/") &&
            path.split("/").none { it == "." || it == ".." || it.isBlank() }) {
            "Invalid repository-relative path"
        }
        return "https://api.github.com/repos/${segment(parts[0])}/${segment(parts[1])}/contents/" +
            path.split("/").joinToString("/") { segment(it) }
    }

    private fun request(token: String, builder: Request.Builder): Request {
        require(token.isNotBlank()) { "GitHub credential is missing" }
        return builder
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
    }

    private suspend fun execute(request: Request): JSONObject = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                // Never return response headers or credentials in model-visible errors.
                throw GitHubContentsException(response.code)
            }
            JSONObject(body)
        }
    }

    suspend fun read(token: String, repository: String, path: String, ref: String? = null): JSONObject {
        val base = endpoint(repository, path)
        val url = if (ref.isNullOrBlank()) base else "$base?ref=${segment(ref)}"
        return execute(request(token, Request.Builder().url(url).get()))
    }

    /**
     * Explicit SHA is required for updates. For new files, pass null.
     * Branch is mandatory so writes never accidentally target a default branch.
     */
    suspend fun write(
        token: String,
        repository: String,
        path: String,
        branch: String,
        content: ByteArray,
        message: String,
        currentSha: String? = null,
    ): JSONObject {
        require(branch.isNotBlank() && message.isNotBlank())
        val payload = JSONObject()
            .put("branch", branch)
            .put("message", message)
            .put("content", android.util.Base64.encodeToString(content, android.util.Base64.NO_WRAP))
        if (currentSha != null) payload.put("sha", currentSha)
        val body = payload.toString().toRequestBody(
            "application/json; charset=utf-8".toMediaType()
        )
        return execute(request(token, Request.Builder().url(endpoint(repository, path)).put(body)))
    }

    suspend fun delete(
        token: String,
        repository: String,
        path: String,
        branch: String,
        message: String,
        currentSha: String,
    ): JSONObject {
        require(branch.isNotBlank() && message.isNotBlank() && currentSha.isNotBlank())
        val payload = JSONObject().put("branch", branch).put("message", message).put("sha", currentSha)
        val body = payload.toString().toRequestBody(
            "application/json; charset=utf-8".toMediaType()
        )
        return execute(request(token, Request.Builder().url(endpoint(repository, path)).delete(body)))
    }
}

internal class GitHubContentsException(val statusCode: Int) :
    IllegalStateException("GitHub request failed (HTTP $statusCode)")
