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

class RecordDao(val maxHeap: Long, val start: Long, val stop: Long? = null, val metrics: Map<String, List<Metric>>)

internal suspend fun StoreClient.loadRecordData(id: AgentId) = findById<StoredRecordData>(id)

@Serializable
data class AgentId(val agentId: String, val buildVersion: String)

@Serializable
data class InstanceData(
    @Id val instanceId: String,
    val metrics: List<Metric>,
) {
    override fun equals(other: Any?): Boolean = other is InstanceData && instanceId == other.instanceId

    override fun hashCode(): Int = instanceId.hashCode()
}

@Serializable
internal data class StoredRecordData(
    @Id val id: AgentId,
    val maxHeap: Long,
    val hasRecord: Boolean = true,
    val breaks: List<Break> = emptyList(),
    val instances: Set<String> = emptySet(),
)

internal suspend fun StoreClient.loadRecordData(
    id: AgentId,
    instances: Set<String> = emptySet(),
    range: LongRange = LongRange.EMPTY,
): AgentsStats = findById<StoredRecordData>(id)?.let { data ->
    val instancesToLoad = instances.takeIf { it.isNotEmpty() } ?: data.instances
    val series = instancesToLoad.mapNotNull { instanceId ->
        findById<InstanceData>(instanceId)?.let { instanceData ->
            instanceData.copy(
                metrics = instanceData.metrics.filter { it.timeStamp in range || range.isEmpty() }
            )
        }
    }.toSeries()
    AgentsStats(breaks = data.breaks, series = series, hasRecord = series.any())
} ?: AgentsStats()

internal suspend fun StoreClient.updateRecordData(
    agentId: AgentId,
    record: RecordDao,
): StoredRecordData {
    val instances = mutableSetOf<InstanceData>()
    record.metrics.forEach { (instanceId, metrics) ->
        instances.add(findById<InstanceData>(instanceId)?.also {
            store(it.copy(metrics = it.metrics + metrics))
        } ?: store(InstanceData(instanceId, metrics)))
    }
    return findById<StoredRecordData>(agentId)?.let { recordData ->
        store(recordData.copy(
            breaks = recordData.breaks + (record.stop?.let { listOf(Break(record.start, it)) } ?: emptyList()),
            instances = recordData.instances + instances.map { it.instanceId }
        )).also { logger.trace { "Updated recorde saved $it" } }
    } ?: store(
        StoredRecordData(
            id = agentId,
            maxHeap = record.maxHeap,
            breaks = record.stop?.let { listOf(Break(record.start, it)) } ?: emptyList(),
            instances = instances.map { it.instanceId }.toSet(),
            hasRecord = true
        )
    ).also { logger.trace { "New Recode saved $it" } }
}
