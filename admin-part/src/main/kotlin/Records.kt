/**
 * Copyright 2020 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.plugins.tracer

import com.epam.drill.plugins.tracer.api.*
import com.epam.drill.plugins.tracer.util.*
import com.epam.drill.plugins.tracer.util.AsyncJobDispatcher
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.serialization.*


@Serializable
sealed class Record {
    abstract val id: String
    //TODO i think we can specify type of record here fore example memory or cpy profile
}

typealias ActiveRecordHandler = suspend ActiveRecord.(String, Map<String, List<Metric>>) -> Unit

class ActiveRecord(
    override val id: String,
    val maxHeap: Long,
) : Record() {

    private val _metrics = atomic(persistentHashMapOf<String, PersistentList<Metric>>())

    private val _metricsToPersist = atomic(persistentHashMapOf<String, PersistentList<Metric>>())

    private val _sendHandler = atomic<ActiveRecordHandler?>(null)

    private val _persistHandler = atomic<ActiveRecordHandler?>(null)

    private val sendJob = AsyncJobDispatcher.launch {
        while (true) {
            delay(5000)
            val metrics = _metrics.getAndUpdate { it.clear() }
            _sendHandler.value?.let { handler ->
                handler(id, metrics)
            }
            _metricsToPersist.update { map ->
                map.merge(metrics).asSequence().associate {
                    it.key to it.value.toPersistentList()
                }.toPersistentHashMap()
            }
        }
    }
    private val persistJob = AsyncJobDispatcher.launch {
        while (true) {
            delay(10000)
            _persistHandler.value?.let { handler ->
                handler(id, _metricsToPersist.getAndUpdate { it.clear() })
            }
        }
    }

    fun addMetric(instanceId: String, metric: Metric) = _metrics.updateAndGet {
        val map = it[instanceId] ?: persistentListOf()
        it.put(instanceId, map.add(metric))
    }

    fun stopRecording() = FinishedRecord(id, maxHeap, _metrics.value.asSequence().associate {
        it.key to it.value.toList()
    }.toMap()).also { cancelJobs() }

    private fun cancelJobs() {
        sendJob.cancel()
        persistJob.cancel()
    }

    fun initSendHandler(handler: ActiveRecordHandler) = _sendHandler.update {
        it ?: handler
    }

    fun initPersistHandler(handler: ActiveRecordHandler) = _persistHandler.update {
        it ?: handler
    }

}

@Serializable
data class FinishedRecord(
    override val id: String,
    val maxHeap: Long,
    val metrics: Map<String, List<Metric>>,
) : Record() {

    override fun equals(other: Any?): Boolean = other is FinishedRecord && id == other.id

    override fun hashCode(): Int = id.hashCode()
}
