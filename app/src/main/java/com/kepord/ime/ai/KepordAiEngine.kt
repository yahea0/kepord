package com.kepord.ime.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AiReplyResult(
    val success: Boolean,
    val text: String,
    val usedKeyLabel: String = "",
    val switchedKeysCount: Int = 0,
    val usedWebSearch: Boolean = false,
    val sourcesText: String = ""
)

data class FetchModelsResult(
    val success: Boolean,
    val models: List<AiModelInfo> = emptyList(),
    val message: String = ""
)

class KepordAiEngine(private val aiPrefs: KepordAiPrefs) {

    suspend fun fetchAvailableModels(): FetchModelsResult = withContext(Dispatchers.IO) {
        val st = aiPrefs.current()
        val provider = st.provider
        val baseUrl = resolveBaseUrl(st)

        val candidates = selectCandidateKeys(st)
        if (candidates.isEmpty()) {
            return@withContext FetchModelsResult(
                success = false,
                message = "أضف مفتاح API واحد على الأقل لمزود ${provider.titleAr} أولاً."
            )
        }

        var lastErr = "تعذّر جلب النماذج"
        for (keyEntry in candidates) {
            val res = when (provider) {
                AiProvider.GEMINI -> httpGet("$baseUrl/models?pageSize=200", mapOf("x-goog-api-key" to keyEntry.apiKey))
                else -> httpGet("$baseUrl/models", mapOf("Authorization" to "Bearer ${keyEntry.apiKey}"))
            }

            if (res.code in 200..299) {
                val parsed = parseModelsResponse(provider, res.body)
                if (parsed.isNotEmpty()) {
                    markKeyHealthy(keyEntry.id)
                    aiPrefs.update { cur ->
                        val nextModel = if (parsed.any { it.id == cur.selectedModel }) {
                            cur.selectedModel
                        } else {
                            parsed.first().id
                        }
                        cur.copy(fetchedModels = parsed, selectedModel = nextModel)
                    }
                    return@withContext FetchModelsResult(
                        success = true,
                        models = parsed,
                        message = "تم استجلاب ${parsed.size} نموذج متاح بنجاح (${keyEntry.label})"
                    )
                } else {
                    lastErr = "لم يرجع المزود قائمة نماذج صالحة."
                }
            } else {
                lastErr = classifyAndRecordKeyFailure(keyEntry, res.code, res.body)
                if (!shouldRetryNextKey(res.code)) break
            }
        }

        return@withContext FetchModelsResult(success = false, message = lastErr)
    }

    suspend fun sendChat(
        history: List<AiChatMessage>,
        userPrompt: String
    ): AiReplyResult = withContext(Dispatchers.IO) {
        val st = aiPrefs.current()
        val provider = st.provider
        val baseUrl = resolveBaseUrl(st)
        val model = st.selectedModel.ifBlank { provider.defaultModel }

        val candidates = selectCandidateKeys(st)
        if (candidates.isEmpty()) {
            return@withContext AiReplyResult(
                success = false,
                text = "لا يوجد مفتاح API متاح لـ ${provider.titleAr}.\nأضف مفتاحاً (أو عدة مفاتيح للتبديل التلقائي) من إعدادات التطبيق ← تبويب الذكاء."
            )
        }

        // Pre-fetch live web snippet for non-native providers when Web Search is enabled
        val liveWebSnippet = if (st.webSearchEnabled && (provider == AiProvider.OPENAI || provider == AiProvider.CUSTOM_OPENAI)) {
            fetchLiveWebSnippet(userPrompt)
        } else {
            ""
        }

        var switchedCount = 0
        var lastErrorMsg = "تعذّر الاتصال بالنموذج."

        for ((attemptIdx, keyEntry) in candidates.withIndex()) {
            if (attemptIdx > 0) switchedCount++

            val result = when (provider) {
                AiProvider.GEMINI -> callGemini(
                    baseUrl = baseUrl,
                    model = model,
                    apiKey = keyEntry.apiKey,
                    systemPrompt = st.systemPrompt,
                    history = history,
                    userPrompt = userPrompt,
                    webSearch = st.webSearchEnabled
                )
                else -> callOpenAiCompatible(
                    provider = provider,
                    baseUrl = baseUrl,
                    model = model,
                    apiKey = keyEntry.apiKey,
                    systemPrompt = st.systemPrompt,
                    history = history,
                    userPrompt = userPrompt,
                    webSearch = st.webSearchEnabled,
                    liveWebSnippet = liveWebSnippet
                )
            }

            if (result.httpCode in 200..299 && result.reply != null) {
                markKeyHealthy(keyEntry.id, advanceRoundRobin = true)
                return@withContext result.reply.copy(
                    usedKeyLabel = keyEntry.label,
                    switchedKeysCount = switchedCount
                )
            } else {
                lastErrorMsg = classifyAndRecordKeyFailure(keyEntry, result.httpCode, result.rawBody)
                if (!shouldRetryNextKey(result.httpCode) && !isQuotaBody(result.rawBody)) {
                    break
                }
            }
        }

        return@withContext AiReplyResult(
            success = false,
            text = "تعذر الرد بعد تجربة ${candidates.size} مفتاح:\n$lastErrorMsg",
            switchedKeysCount = switchedCount
        )
    }

    private data class InternalCallResult(
        val httpCode: Int,
        val rawBody: String,
        val reply: AiReplyResult?
    )

    private fun callGemini(
        baseUrl: String,
        model: String,
        apiKey: String,
        systemPrompt: String,
        history: List<AiChatMessage>,
        userPrompt: String,
        webSearch: Boolean
    ): InternalCallResult {
        val url = "$baseUrl/models/$model:generateContent"
        val headers = mapOf(
            "x-goog-api-key" to apiKey,
            "Content-Type" to "application/json"
        )

        fun buildBody(includeGoogleSearchTool: Boolean): String {
            val contents = JSONArray()
            for (msg in history.takeLast(10)) {
                contents.put(
                    JSONObject().apply {
                        put("role", if (msg.isUser) "user" else "model")
                        put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
                    }
                )
            }
            contents.put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
                }
            )

            val root = JSONObject().apply {
                put("contents", contents)
                if (systemPrompt.isNotBlank()) {
                    put(
                        "systemInstruction",
                        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                    )
                }
                if (includeGoogleSearchTool) {
                    put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
                }
            }
            return root.toString()
        }

        var res = httpPost(url, headers, buildBody(webSearch))
        // Fallback cleanly if a specific legacy Gemini model rejects google_search tool
        if (webSearch && res.code == 400 && (res.body.contains("google_search", true) || res.body.contains("tool", true))) {
            res = httpPost(url, headers, buildBody(false))
        }

        if (res.code !in 200..299) {
            return InternalCallResult(res.code, res.body, null)
        }

        return try {
            val json = JSONObject(res.body)
            val candidates = json.optJSONArray("candidates")
            val first = candidates?.optJSONObject(0)
            val parts = first?.optJSONObject("content")?.optJSONArray("parts")
            val sb = StringBuilder()
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val t = parts.optJSONObject(i)?.optString("text", "").orEmpty()
                    if (t.isNotEmpty()) sb.append(t)
                }
            }

            val grounding = first?.optJSONObject("groundingMetadata")
            val chunks = grounding?.optJSONArray("groundingChunks")
            val sources = mutableListOf<String>()
            if (chunks != null) {
                for (i in 0 until chunks.length().coerceAtMost(3)) {
                    val web = chunks.optJSONObject(i)?.optJSONObject("web")
                    val title = web?.optString("title", "").orEmpty()
                    if (title.isNotBlank() && title !in sources) sources.add(title)
                }
            }
            val queries = grounding?.optJSONArray("webSearchQueries")
            val didSearch = (queries != null && queries.length() > 0) || sources.isNotEmpty()

            val text = sb.toString().trim().ifEmpty { "لم يرجع النموذج نصاً." }
            InternalCallResult(
                res.code,
                res.body,
                AiReplyResult(
                    success = true,
                    text = text,
                    usedWebSearch = didSearch,
                    sourcesText = if (sources.isNotEmpty()) "المصادر: ${sources.joinToString(" • ")}" else ""
                )
            )
        } catch (t: Throwable) {
            InternalCallResult(500, t.message ?: "Parse error", null)
        }
    }

    private fun callOpenAiCompatible(
        provider: AiProvider,
        baseUrl: String,
        model: String,
        apiKey: String,
        systemPrompt: String,
        history: List<AiChatMessage>,
        userPrompt: String,
        webSearch: Boolean,
        liveWebSnippet: String
    ): InternalCallResult {
        val cleanBase = baseUrl.trimEnd('/')
        val url = "$cleanBase/chat/completions"
        val headers = mutableMapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json"
        )
        if (provider == AiProvider.OPENROUTER) {
            headers["HTTP-Referer"] = "https://github.com/yahea0/kepord"
            headers["X-Title"] = "Kepord Keyboard"
        }

        val messages = JSONArray()
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val fullSystem = buildString {
            append(systemPrompt)
            append("\nالتاريخ والوقت الحالي: ").append(nowStr)
            if (liveWebSnippet.isNotBlank()) {
                append("\n\nبيانات حية من الإنترنت للمساعدة في الإجابة:\n")
                append(liveWebSnippet)
            }
        }
        messages.put(JSONObject().put("role", "system").put("content", fullSystem))

        for (msg in history.takeLast(10)) {
            messages.put(
                JSONObject()
                    .put("role", if (msg.isUser) "user" else "assistant")
                    .put("content", msg.text)
            )
        }
        messages.put(JSONObject().put("role", "user").put("content", userPrompt))

        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            if (provider == AiProvider.OPENROUTER && webSearch) {
                put("plugins", JSONArray().put(JSONObject().put("id", "web")))
            }
        }

        val res = httpPost(url, headers, body.toString())
        if (res.code !in 200..299) {
            return InternalCallResult(res.code, res.body, null)
        }

        return try {
            val json = JSONObject(res.body)
            val content = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
            InternalCallResult(
                res.code,
                res.body,
                AiReplyResult(
                    success = true,
                    text = content,
                    usedWebSearch = webSearch && (provider == AiProvider.OPENROUTER || liveWebSnippet.isNotBlank())
                )
            )
        } catch (t: Throwable) {
            InternalCallResult(500, t.message ?: "Parse error", null)
        }
    }

    private fun fetchLiveWebSnippet(query: String): String {
        val sb = StringBuilder()
        // 1. Live currency exchange rates if user asks about exchange/currency/dollar/shekel/dinar/euro
        val lower = query.lowercase(Locale.ROOT)
        val currencyHints = listOf(
            "صرف", "دولار", "يورو", "شيكل", "دينار", "ريال", "درهم", "جنيه", "ليرة",
            "usd", "eur", "ils", "jod", "sar", "aed", "egp", "rate", "currency", "exchange"
        )
        if (currencyHints.any { lower.contains(it) }) {
            runCatching {
                val res = httpGet("https://open.er-api.com/v6/latest/USD", emptyMap(), timeoutMs = 6000)
                if (res.code == 200) {
                    val rates = JSONObject(res.body).optJSONObject("rates")
                    if (rates != null) {
                        val picked = listOf("ILS", "JOD", "SAR", "AED", "EGP", "EUR", "GBP", "TRY", "KWD", "QAR")
                            .mapNotNull { code ->
                                if (rates.has(code)) "$code=${rates.getDouble(code)}" else null
                            }
                        if (picked.isNotEmpty()) {
                            sb.append("أسعار صرف الدولار الأمريكي المباشرة الآن (1 USD): ")
                                .append(picked.joinToString(", "))
                                .append('\n')
                        }
                    }
                }
            }
        }

        // 2. DuckDuckGo Instant Answer live lookup
        runCatching {
            val encoded = URLEncoder.encode(query.take(180), "UTF-8")
            val res = httpGet(
                "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1",
                emptyMap(),
                timeoutMs = 5000
            )
            if (res.code == 200) {
                val obj = JSONObject(res.body)
                val abs = obj.optString("AbstractText", "")
                val answer = obj.optString("Answer", "")
                if (answer.isNotBlank()) sb.append("إجابة ويب فورية: ").append(answer).append('\n')
                if (abs.isNotBlank()) sb.append("ملخص ويب: ").append(abs).append('\n')
            }
        }
        return sb.toString().trim()
    }

    private fun parseModelsResponse(provider: AiProvider, rawBody: String): List<AiModelInfo> {
        val out = mutableListOf<AiModelInfo>()
        runCatching {
            val json = JSONObject(rawBody)
            if (provider == AiProvider.GEMINI) {
                val arr = json.optJSONArray("models") ?: return@runCatching
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    val methods = m.optJSONArray("supportedGenerationMethods")
                    var supportsGen = false
                    if (methods != null) {
                        for (j in 0 until methods.length()) {
                            if (methods.optString(j) == "generateContent") {
                                supportsGen = true
                                break
                            }
                        }
                    }
                    if (!supportsGen) continue
                    val fullName = m.optString("name", "")
                    val cleanId = fullName.removePrefix("models/")
                    if (cleanId.isNotBlank()) {
                        val disp = m.optString("displayName", cleanId).ifBlank { cleanId }
                        out.add(AiModelInfo(id = cleanId, displayName = disp))
                    }
                }
            } else {
                val arr = json.optJSONArray("data") ?: return@runCatching
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    val id = m.optString("id", "")
                    if (id.isNotBlank()) {
                        val disp = m.optString("name", id).ifBlank { id }
                        out.add(AiModelInfo(id = id, displayName = disp))
                    }
                }
                out.sortBy { it.id }
            }
        }
        return out
    }

    private fun selectCandidateKeys(state: AiConfigState): List<AiApiKeyEntry> {
        val now = System.currentTimeMillis()
        val providerKeys = state.keys.filter { it.provider == state.provider }
        if (providerKeys.isEmpty()) return emptyList()

        // Revive expired cooldown keys automatically
        val refreshed = providerKeys.map { k ->
            if (k.status == AiKeyStatus.COOLDOWN && k.cooldownUntilMs <= now) {
                k.copy(status = AiKeyStatus.ACTIVE, cooldownUntilMs = 0L, lastError = "")
            } else {
                k
            }
        }

        val activeList = refreshed.filter { it.status == AiKeyStatus.ACTIVE }
        if (activeList.isNotEmpty()) {
            val start = state.roundRobinIndex % activeList.size
            return activeList.drop(start) + activeList.take(start)
        }

        // Fallback to cooldown keys sorted by earliest expiry before giving up
        val cooldowns = refreshed.filter { it.status == AiKeyStatus.COOLDOWN }.sortedBy { it.cooldownUntilMs }
        if (cooldowns.isNotEmpty()) return cooldowns

        return emptyList()
    }

    private fun markKeyHealthy(keyId: String, advanceRoundRobin: Boolean = false) {
        aiPrefs.update { s ->
            s.copy(
                roundRobinIndex = if (advanceRoundRobin) s.roundRobinIndex + 1 else s.roundRobinIndex,
                keys = s.keys.map { k ->
                    if (k.id == keyId) {
                        k.copy(status = AiKeyStatus.ACTIVE, cooldownUntilMs = 0L, lastError = "")
                    } else {
                        k
                    }
                }
            )
        }
    }

    private fun classifyAndRecordKeyFailure(entry: AiApiKeyEntry, httpCode: Int, rawBody: String): String {
        val lower = rawBody.lowercase(Locale.ROOT)
        val now = System.currentTimeMillis()
        val newStatus: AiKeyStatus
        val cooldownMs: Long
        val humanErr: String

        when {
            httpCode == 401 || lower.contains("api_key_invalid") || lower.contains("invalid api key") -> {
                newStatus = AiKeyStatus.INVALID
                cooldownMs = 0L
                humanErr = "مفتاح غير صالح (${entry.label})"
            }
            httpCode == 402 || lower.contains("insufficient_quota") || lower.contains("exceeded your current quota") || lower.contains("billing") || lower.contains("credit") -> {
                newStatus = AiKeyStatus.QUOTA_EXCEEDED
                cooldownMs = 0L
                humanErr = "نفدت حصة المفتاح (${entry.label})"
            }
            httpCode == 429 || httpCode == 503 || lower.contains("rate") || lower.contains("quota") || lower.contains("resource_exhausted") -> {
                newStatus = AiKeyStatus.COOLDOWN
                cooldownMs = now + 65_000L
                humanErr = "تم بلوغ حد الطلبات المؤقت (${entry.label})"
            }
            httpCode == 403 -> {
                newStatus = AiKeyStatus.QUOTA_EXCEEDED
                cooldownMs = 0L
                humanErr = "مرفوض الصلاحية/الحصة (${entry.label})"
            }
            else -> {
                newStatus = entry.status
                cooldownMs = entry.cooldownUntilMs
                humanErr = "خطأ ($httpCode): ${extractShortApiError(rawBody)}"
            }
        }

        aiPrefs.update { s ->
            s.copy(
                keys = s.keys.map { k ->
                    if (k.id == entry.id) {
                        k.copy(status = newStatus, cooldownUntilMs = cooldownMs, lastError = humanErr)
                    } else {
                        k
                    }
                }
            )
        }
        return humanErr
    }

    private fun shouldRetryNextKey(code: Int): Boolean {
        return code == 401 || code == 402 || code == 403 || code == 429 || code == 500 || code == 503
    }

    private fun isQuotaBody(body: String): Boolean {
        val l = body.lowercase(Locale.ROOT)
        return l.contains("quota") || l.contains("rate") || l.contains("limit") || l.contains("exhausted")
    }

    private fun extractShortApiError(raw: String): String {
        return runCatching {
            val obj = JSONObject(raw)
            obj.optJSONObject("error")?.optString("message")
                ?: obj.optString("message", raw.take(120))
        }.getOrElse { raw.take(120) }
    }

    private fun resolveBaseUrl(st: AiConfigState): String {
        return if (st.provider == AiProvider.CUSTOM_OPENAI) {
            st.customBaseUrl.trim().trimEnd('/').ifEmpty { AiProvider.CUSTOM_OPENAI.defaultBaseUrl }
        } else {
            st.provider.defaultBaseUrl
        }
    }

    private data class HttpResponse(val code: Int, val body: String)

    private fun httpGet(
        urlStr: String,
        headers: Map<String, String>,
        timeoutMs: Int = 15000
    ): HttpResponse {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                for ((k, v) in headers) setRequestProperty(k, v)
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() }.orEmpty()
            HttpResponse(code, text)
        } catch (t: Throwable) {
            HttpResponse(-1, t.message ?: "Network error")
        } finally {
            conn?.disconnect()
        }
    }

    private fun httpPost(
        urlStr: String,
        headers: Map<String, String>,
        jsonBody: String,
        timeoutMs: Int = 35000
    ): HttpResponse {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                for ((k, v) in headers) setRequestProperty(k, v)
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
                it.write(jsonBody)
                it.flush()
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() }.orEmpty()
            HttpResponse(code, text)
        } catch (t: Throwable) {
            HttpResponse(-1, t.message ?: "Network error")
        } finally {
            conn?.disconnect()
        }
    }
}
