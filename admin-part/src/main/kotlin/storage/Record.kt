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

class RecordDao(val maxHeap: Long, val `break`: Long? = null, val metrics: Map<String, List<Metric>>)

internal suspend fun StoreClient.loadRecordData(agentId: String) = findById<StoredRecordData>(agentId)

@Serializable
data class InstanceData(
    @Id val instanceId: String,
    val metrics: List<Metric>,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return instanceId == (other as InstanceData).instanceId
    }

    override fun hashCode(): Int {
        return instanceId.hashCode()
    }
}

@Serializable
internal data class StoredRecordData(
    @Id val id: String,
    val maxHeap: Long,
    val breaks: List<Long> = emptyList(),
    @StreamSerialization(SerializationType.KRYO, CompressType.ZSTD, [])
    val instances: Set<InstanceData> = emptySet(),
)

internal suspend fun StoreClient.updateRecordData(
    agentId: String,
    record: RecordDao,
): StoredRecordData {
    val instances = mutableSetOf<InstanceData>()
    for ((instanceId, metrics) in record.metrics) {
        instances.add(findById<InstanceData>(instanceId)?.also {
            store(it.copy(metrics = it.metrics + metrics))
        } ?: store(InstanceData(instanceId, metrics)))
    }
    return findById<StoredRecordData>(agentId)?.let {
        store(it.copy(
            breaks = it.breaks + (record.`break`?.let { listOf(it) } ?: emptyList()),
            instances = it.instances + instances
        )).also { logger.info { "Updated recorde saved $it" } }
    } ?: store(
        StoredRecordData(
            id = agentId,
            maxHeap = record.maxHeap,
            breaks = record.`break`?.let { listOf(it) } ?: emptyList(),
            instances = instances
        )
    ).also { logger.info { "New Recode saved $it" } }
}
