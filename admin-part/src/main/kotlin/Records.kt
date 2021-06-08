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
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.*


@Serializable
sealed class Record {
    abstract val id: String
    //TODO i think we can specify type of record here fore example memory or cpy profile
}


class ActiveRecord(
    override val id: String,
) : Record() {

    private val _metrics = atomic(persistentHashMapOf<String, PersistentList<Metric>>())

    // override fun iterator(): Iterator<Metric> = _metrics.value.values.flatten().iterator()



    fun addMetric(instanceId: String, metric: Metric) = _metrics.updateAndGet {
        val map = it[instanceId] ?: persistentListOf()
        it.put(instanceId, map.add(metric))
    }

    fun stopRecording(maxHeap: Long) = FinishedRecord(id, maxHeap, _metrics.value.asSequence().associate {
        it.key to it.value.toList()
    }.toMap())

}

@Serializable
data class FinishedRecord(
    override val id: String,
    val maxHeap: Long,
    val metrics: Map<String, List<Metric>>,
) : Record() {

    //  override fun iterator(): Iterator<Metric> = metrics.iterator()

    override fun equals(other: Any?): Boolean = other is FinishedRecord && id == other.id

    override fun hashCode(): Int = id.hashCode()
}
