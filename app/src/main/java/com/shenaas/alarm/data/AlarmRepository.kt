package com.shenaas.alarm.data

import android.content.Context
import android.util.AtomicFile
import com.shenaas.alarm.model.Alarm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

object AlarmRepository {
    private lateinit var file: AtomicFile
    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            file = Storage.file(context, "alarms.json")
            _alarms.value = load()
            initialized = true
        }
    }

    fun all(): List<Alarm> = _alarms.value

    fun get(id: Int): Alarm? = _alarms.value.firstOrNull { it.id == id }

    fun nextId(): Int = (_alarms.value.maxOfOrNull { it.id } ?: 0) + 1

    @Synchronized
    fun upsert(alarm: Alarm) {
        val list = _alarms.value.toMutableList()
        val index = list.indexOfFirst { it.id == alarm.id }
        if (index >= 0) list[index] = alarm else list += alarm
        commit(list)
    }

    @Synchronized
    fun update(id: Int, transform: (Alarm) -> Alarm): Alarm? {
        val current = get(id) ?: return null
        return transform(current).also { upsert(it) }
    }

    @Synchronized
    fun delete(id: Int) {
        commit(_alarms.value.filterNot { it.id == id })
    }

    private fun commit(list: List<Alarm>) {
        val sorted = list.sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))
        _alarms.value = sorted
        file.writeText(JSONArray(sorted.map { it.toJson() }).toString())
    }

    private fun load(): List<Alarm> {
        val text = file.readText() ?: return emptyList()
        return runCatching {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { runCatching { Alarm.fromJson(array.getJSONObject(it)) }.getOrNull() }
        }.getOrDefault(emptyList())
    }
}
