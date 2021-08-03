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
import com.epam.drill.plugins.tracer.storage.RecordDao
import com.epam.drill.plugins.tracer.util.*
import com.epam.drill.plugins.tracer.util.AsyncJobDispatcher
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*


typealias ActiveRecordHandler = suspend ActiveRecord.(Map<String, List<Metric>>) -> Unit
typealias PersistRecordHandler = suspend ActiveRecord.(Map<String, List<Metric>>) -> Unit

class ActiveRecord(
    val start: Long,
    val maxHeap: Long,
) {
    private val _metrics = atomic(persistentHashMapOf<String, PersistentList<Metric>>())

    private val _metricsToPersist = atomic(persistentHashMapOf<String, PersistentList<Metric>>())

    private val _sendHandler = atomic<ActiveRecordHandler?>(null)

    private val _persistHandler = atomic<PersistRecordHandler?>(null)

    @Volatile
    private var isJobsActive = true

    private val sendJob = AsyncJobDispatcher.launch {
        while (isJobsActive) {
            delay(5000L.takeIf { isJobsActive } ?: 0L)
            val metrics = _metrics.getAndUpdate { it.clear() }
            _sendHandler.value?.let { handler ->
                handler(metrics)
            }
            _metricsToPersist.update { persistentMap ->
                (persistentMap + metrics).asSequence().associate {
                    it.key to it.value.toPersistentList()
                }.toPersistentHashMap()
            }
        }
    }


    private val persistJob = AsyncJobDispatcher.launch {
        while (isJobsActive) {
            _persistHandler.value?.let { handler ->
                delay(10000L.takeIf { isJobsActive } ?: 0L)
                handler(_metricsToPersist.getAndUpdate { it.clear() })
            }
        }
    }

    fun addMetric(instanceId: String, metric: Metric) = _metrics.updateAndGet {
        val map = it[instanceId] ?: persistentListOf()
        it.put(instanceId, map.add(metric))
    }

    suspend fun stopRecording() = run {
        joinJobs()
        val metrics = _metrics.value
        val stopRecordTimeStamp = metrics.values.firstOrNull()?.firstOrNull()?.timeStamp ?: currentTimeMillis()
        RecordDao(maxHeap, start, stopRecordTimeStamp, metrics.asSequence().associate {
            it.key to it.value.toList()
        }.toMap())
    }

    private suspend fun joinJobs() {
        isJobsActive = false
        sendJob.join()
        persistJob.join()
    }

    fun initSendHandler(handler: ActiveRecordHandler) = _sendHandler.update {
        it ?: handler
    }

    fun initPersistHandler(handler: PersistRecordHandler) = _persistHandler.update {
        it ?: handler
    }
}
