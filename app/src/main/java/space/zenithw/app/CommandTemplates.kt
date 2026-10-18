package space.zenithw.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CommandTemplate(val id: String = UUID.randomUUID().toString(), val name: String, val arguments: String)

class CommandTemplateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("zenithw_command_templates", Context.MODE_PRIVATE)

    fun load(): List<CommandTemplate> = runCatching {
        val saved = preferences.getString("templates", null) ?: return@runCatching emptyList()
        val array = JSONArray(saved)
        (0 until minOf(array.length(), 100)).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                CommandTemplate(item.getString("id"), item.getString("name").take(80), item.getString("arguments")).also {
                    require(it.id.isNotBlank() && it.name.isNotBlank())
                    validateCustomArguments(it.arguments)
                }
            }.getOrNull()
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())

    fun save(templates: List<CommandTemplate>) {
        require(templates.size <= 100)
        val array = JSONArray()
        templates.forEach { template ->
            require(template.name.isNotBlank() && template.name.length <= 80)
            validateCustomArguments(template.arguments)
            array.put(JSONObject().put("id", template.id).put("name", template.name).put("arguments", template.arguments))
        }
        preferences.edit().putString("templates", array.toString()).apply()
    }
}
