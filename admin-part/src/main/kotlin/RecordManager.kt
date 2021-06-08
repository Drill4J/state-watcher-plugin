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
import com.epam.drill.plugins.tracer.storage.*
import com.epam.drill.plugins.tracer.util.*
import com.epam.kodux.*


class RecordManager(
    private val plugin: Plugin,
    private val storeClient: StoreClient,
) {

    private val _activeRecords = AtomicCache<String, ActiveRecord>()



    fun startRecord(recordId: String) {
        _activeRecords[recordId] = ActiveRecord(recordId)
    }

    suspend fun stopRecord(recordId: String) =
        _activeRecords.remove(recordId)!!.stopRecording(plugin.maxHeap.value).also { finishedRecord ->
            plugin.sendMetrics(AllStats(series = finishedRecord.metrics))
            storeClient.store(StoredRecord(recordId, finishedRecord))
        }

    suspend fun addMetric(instanceId: String, recordId: String, metric: Metric) =
        _activeRecords[recordId]?.addMetric(instanceId, metric).also {
            plugin.sendMetric(HeapDto(metric.timeStamp, mapOf(instanceId to metric)))
        }


}
