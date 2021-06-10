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

@Serializable
internal data class ActiveRecordEntity(
    val maxHeap: Long,
    val breaks: List<Long>,
    val metrics: Map<String, List<Metric>>,
)

class RecordDao(val maxHeap: Long, val `break`: Long? = null, val metrics: Map<String, List<Metric>>)

private const val storeId = "id"

@Serializable
internal data class StoredActiveRecord(
    @Id val id: String = storeId,
    @StreamSerialization(SerializationType.KRYO, CompressType.ZSTD, [])
    val data: ActiveRecordEntity,
)


internal suspend fun StoreClient.updateRecordData(
    record: RecordDao,
) = findById<StoredActiveRecord>(storeId)?.let { activeRecord ->
    val data = activeRecord.data
    val metrics = data.metrics.asSequence().associate { entry ->
        val list = entry.value
        entry.key to (list.takeIf { it.size < 100 } ?: list.subList(list.size / 4, list.size))
    } + record.metrics
    store(activeRecord.copy(data = data.copy(metrics = metrics,
        breaks = record.`break`?.let { data.breaks + it } ?: data.breaks)))
} ?: store(
    StoredActiveRecord(
        data = ActiveRecordEntity(
            record.maxHeap,
            record.`break`?.let { listOf(it) } ?: emptyList(),
            record.metrics
        )
    )
)

internal suspend fun StoreClient.loadRecordData() = findById<StoredActiveRecord>(storeId)

