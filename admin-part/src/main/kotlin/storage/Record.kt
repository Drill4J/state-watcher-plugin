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
package com.epam.drill.plugins.tracer.storage

import com.epam.drill.plugins.tracer.api.*
import com.epam.drill.plugins.tracer.util.*
import com.epam.kodux.*
import kotlinx.serialization.*
import mu.*

val logger = KotlinLogging.logger("Storage")

internal suspend fun StoreClient.loadRecordData(id: CompositeId) = findById<StoredRecordData>(id)

class RecordDao(val maxHeap: Long, val `break`: Break? = null, val metrics: Map<String, List<Metric>>)


@Serializable
data class CompositeId(val agentId: String, val buildVersion: String)

@Serializable
data class InstanceData(
    @Id val instanceId: String,
    val metrics: List<Metric>,
) {
    override fun equals(other: Any?): Boolean = other is InstanceData && instanceId == other.instanceId

    override fun hashCode(): Int = instanceId.hashCode()
}

internal suspend fun StoreClient.loadRecordData(
    id: CompositeId,
    instances: Set<String> = emptySet(),
    range: LongRange,
): AgentsStats = findById<StoredRecordData>(id)?.let { data ->
    val breaks = data.breaks.mapNotNull { it.takeIf { it.to in range } }
    val instancesToLoad = instances.takeIf { it.isNotEmpty() } ?: data.instances
    val series = instancesToLoad.mapNotNull { instanceId ->
        findById<InstanceData>(instanceId)?.let { instanceData ->
            instanceData.copy(
                metrics = instanceData.metrics.filter { it.timeStamp in range }
            )
        }
    }.toSeries()
    AgentsStats(breaks = breaks, series = series)
} ?: AgentsStats()


@Serializable
internal data class StoredRecordData(
    @Id val id: CompositeId,
    val maxHeap: Long,
    val breaks: List<Break> = emptyList(),
    val instances: Set<String> = emptySet(),
)

internal suspend fun StoreClient.updateRecordData(
    compositeId: CompositeId,
    record: RecordDao,
): StoredRecordData {
    val instances = mutableSetOf<InstanceData>()
    for ((instanceId, metrics) in record.metrics) {
        instances.add(findById<InstanceData>(instanceId)?.also {
            store(it.copy(metrics = it.metrics + metrics))
        } ?: store(InstanceData(instanceId, metrics)))
    }
    return findById<StoredRecordData>(compositeId)?.let { recordData ->
        store(recordData.copy(
            breaks = recordData.breaks + (record.`break`?.let { listOf(it) } ?: emptyList()),
            instances = recordData.instances + instances.map { it.instanceId }
        )).also { logger.trace { "Updated recorde saved $it" } }
    } ?: store(
        StoredRecordData(
            id = compositeId,
            maxHeap = record.maxHeap,
            breaks = record.`break`?.let { listOf(it) } ?: emptyList(),
            instances = instances.map { it.instanceId }.toSet()
        )
    ).also { logger.trace { "New Recode saved $it" } }
}
