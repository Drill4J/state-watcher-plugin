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

import com.epam.drill.plugins.tracer.*
import com.epam.drill.plugins.tracer.api.*
import com.epam.drill.plugins.tracer.util.*
import com.epam.kodux.*
import kotlinx.serialization.*

@Serializable
internal data class ActiveRecordEntity(
    val maxHeap: Long,
    val metrics: Map<String, List<Metric>>,
)

@Serializable
internal class StoredFinishRecord(
    @Id val recordId: String,
    @StreamSerialization(SerializationType.KRYO, CompressType.ZSTD, [])
    val data: FinishedRecord,
)

@Serializable
internal data class StoredActiveRecord(
    @Id val recordId: String,
    @StreamSerialization(SerializationType.KRYO, CompressType.ZSTD, [])
    val data: ActiveRecordEntity,
)

internal suspend fun StoreClient.loadFinishedRecord(
    recordId: String,
): FinishedRecord? = findById<StoredFinishRecord>(recordId)?.data

internal suspend fun StoreClient.loadActiveRecord(
    recordId: String,
): ActiveRecordEntity? = findById<StoredActiveRecord>(recordId)?.data

internal class ActiveRecordDto(val recordId: String, val maxHeap: Long, val metrics: Map<String, List<Metric>>)

internal suspend fun StoreClient.updateRecord(
    activeRecord: ActiveRecordDto,
) = findById<StoredActiveRecord>(activeRecord.recordId)?.let { it ->
    store(it.copy(data = it.data.copy(metrics = it.data.metrics.merge(activeRecord.metrics))))
} ?: store(StoredActiveRecord(activeRecord.recordId,
    ActiveRecordEntity(activeRecord.maxHeap, activeRecord.metrics)))


internal suspend fun StoreClient.storeRecord(
    record: FinishedRecord,
) {
    val activeRecord = findById<StoredActiveRecord>(record.id).also {
        deleteById<StoredActiveRecord>(record.id)
    }
    store(
        StoredFinishRecord(
            recordId = record.id,
            data = record.copy(metrics = record.metrics + (activeRecord?.data?.metrics ?: emptyMap()))
        )
    )

}
