package com.kepord.ime.ai

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class AiProvider(
    val titleAr: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val supportsNativeWebSearch: Boolean
) {
    GEMINI(
        titleAr = "Google Gemini (بحث جوجل المباشر)",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        defaultModel = "gemini-3.8-flash",
        supportsNativeWebSearch = true
    ),
    OPENROUTER(
        titleAr = "OpenRouter (كل النماذج + ويب)",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "openai/gpt-4o-mini",
        supportsNativeWebSearch = true
    ),
    OPENAI(
        titleAr = "OpenAI API",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        supportsNativeWebSearch = true
    ),
    CUSTOM_OPENAI(
        titleAr = "رابط مخصص (Groq / DeepSeek / أي OpenAI API)",
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        defaultModel = "llama-3.3-70b-versatile",
        supportsNativeWebSearch = true
    )
}

enum class AiKeyStatus(val titleAr: String) {
    ACTIVE("نشط"),
    COOLDOWN("ممتلئ مؤقتاً"),
    QUOTA_EXCEEDED("نفدت الحصة"),
    INVALID("مفتاح غير صالح")
}

data class AiApiKeyEntry(
    val id: String = UUID.randomUUID().toString(),
    val provider: AiProvider,
    val label: String,
    val apiKey: String,
    val status: AiKeyStatus = AiKeyStatus.ACTIVE,
    val cooldownUntilMs: Long = 0L,
    val lastError: String = ""
)

data class AiModelInfo(
    val id: String,
    val displayName: String
)

data class AiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val meta: String = ""
)

data class AiConfigState(
    val provider: AiProvider = AiProvider.GEMINI,
    val customBaseUrl: String = AiProvider.CUSTOM_OPENAI.defaultBaseUrl,
    val selectedModel: String = AiProvider.GEMINI.defaultModel,
    val webSearchEnabled: Boolean = true,
    val systemPrompt: String = "أنت مساعد ذكي مدمج داخل كيبورد Kepord. أجب بإيجاز ودقة وبالعربية ما لم يُطلب غير ذلك. عند الترجمة أعطِ الترجمة المباشرة والواضحة.",
    val keys: List<AiApiKeyEntry> = emptyList(),
    val fetchedModels: List<AiModelInfo> = emptyList(),
    val roundRobinIndex: Int = 0
)

class KepordAiPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<AiConfigState> = _state.asStateFlow()

    fun current(): AiConfigState = _state.value

    fun update(transform: (AiConfigState) -> AiConfigState) {
        val next = transform(_state.value)
        writeState(next)
        _state.value = next
    }

    fun addKeysBulk(provider: AiProvider, rawLines: String, labelPrefix: String = "مفتاح") {
        val newKeys = rawLines
            .split('\n', ',', ';')
            .map { it.trim() }
            .filter { it.length >= 8 }
        if (newKeys.isEmpty()) return

        update { s ->
            val existingSet = s.keys.filter { it.provider == provider }.map { it.apiKey }.toSet()
            val added = mutableListOf<AiApiKeyEntry>()
            var idx = s.keys.count { it.provider == provider } + 1
            for (k in newKeys) {
                if (k !in existingSet) {
                    added.add(
                        AiApiKeyEntry(
                            provider = provider,
                            label = "$labelPrefix #$idx",
                            apiKey = k
                        )
                    )
                    idx++
                }
            }
            s.copy(keys = s.keys + added)
        }
    }

    fun removeKey(id: String) {
        update { s -> s.copy(keys = s.keys.filterNot { it.id == id }) }
    }

    fun resetKeyStatuses(provider: AiProvider? = null) {
        update { s ->
            s.copy(
                keys = s.keys.map { k ->
                    if (provider == null || k.provider == provider) {
                        k.copy(status = AiKeyStatus.ACTIVE, cooldownUntilMs = 0L, lastError = "")
                    } else {
                        k
                    }
                }
            )
        }
    }

    private fun readState(): AiConfigState {
        val provider = prefs.getString("provider", null)
            ?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() }
            ?: AiProvider.GEMINI

        val keys = parseKeys(prefs.getString("keys_json", "[]") ?: "[]")
        val models = parseModels(prefs.getString("models_json_${provider.name}", "[]") ?: "[]")

        return AiConfigState(
            provider = provider,
            customBaseUrl = prefs.getString("custom_base_url", AiProvider.CUSTOM_OPENAI.defaultBaseUrl)
                ?: AiProvider.CUSTOM_OPENAI.defaultBaseUrl,
            selectedModel = prefs.getString("selected_model_${provider.name}", provider.defaultModel)
                ?: provider.defaultModel,
            webSearchEnabled = prefs.getBoolean("web_search_enabled", true),
            systemPrompt = prefs.getString(
                "system_prompt",
                "أنت مساعد ذكي مدمج داخل كيبورد Kepord. أجب بإيجاز ودقة وبالعربية ما لم يُطلب غير ذلك. عند الترجمة أعطِ الترجمة المباشرة والواضحة."
            ) ?: "",
            keys = keys,
            fetchedModels = models,
            roundRobinIndex = prefs.getInt("rr_index", 0)
        )
    }

    private fun writeState(s: AiConfigState) {
        val keysArr = JSONArray()
        for (k in s.keys) {
            keysArr.put(
                JSONObject().apply {
                    put("id", k.id)
                    put("provider", k.provider.name)
                    put("label", k.label)
                    put("apiKey", k.apiKey)
                    put("status", k.status.name)
                    put("cooldownUntilMs", k.cooldownUntilMs)
                    put("lastError", k.lastError)
                }
            )
        }

        val modelsArr = JSONArray()
        for (m in s.fetchedModels) {
            modelsArr.put(
                JSONObject().apply {
                    put("id", m.id)
                    put("displayName", m.displayName)
                }
            )
        }

        prefs.edit()
            .putString("provider", s.provider.name)
            .putString("custom_base_url", s.customBaseUrl)
            .putString("selected_model_${s.provider.name}", s.selectedModel)
            .putBoolean("web_search_enabled", s.webSearchEnabled)
            .putString("system_prompt", s.systemPrompt)
            .putString("keys_json", keysArr.toString())
            .putString("models_json_${s.provider.name}", modelsArr.toString())
            .putInt("rr_index", s.roundRobinIndex)
            .apply()
    }

    fun loadModelsForProvider(provider: AiProvider): List<AiModelInfo> {
        return parseModels(prefs.getString("models_json_${provider.name}", "[]") ?: "[]")
    }

    fun loadSelectedModelForProvider(provider: AiProvider): String {
        return prefs.getString("selected_model_${provider.name}", provider.defaultModel)
            ?: provider.defaultModel
    }

    private fun parseKeys(raw: String): List<AiApiKeyEntry> {
        val out = mutableListOf<AiApiKeyEntry>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val prov = runCatching { AiProvider.valueOf(obj.getString("provider")) }.getOrNull() ?: continue
                val status = runCatching { AiKeyStatus.valueOf(obj.optString("status")) }.getOrDefault(AiKeyStatus.ACTIVE)
                out.add(
                    AiApiKeyEntry(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        provider = prov,
                        label = obj.optString("label", "مفتاح"),
                        apiKey = obj.getString("apiKey"),
                        status = status,
                        cooldownUntilMs = obj.optLong("cooldownUntilMs", 0L),
                        lastError = obj.optString("lastError", "")
                    )
                )
            }
        }
        return out
    }

    private fun parseModels(raw: String): List<AiModelInfo> {
        val out = mutableListOf<AiModelInfo>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                out.add(
                    AiModelInfo(
                        id = obj.getString("id"),
                        displayName = obj.optString("displayName", obj.getString("id"))
                    )
                )
            }
        }
        return out
    }

    companion object {
        private const val PREFS_NAME = "kepord_ai_prefs_v1"

        fun maskKey(key: String): String {
            if (key.length <= 10) return "••••"
            return "${key.take(5)}••••${key.takeLast(4)}"
        }
    }
}
