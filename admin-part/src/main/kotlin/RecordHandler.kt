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
import kotlinx.atomicfu.*


fun Plugin.initSendRecord(activeRecord: ActiveRecord) = activeRecord.initSendHandler { start, metrics ->
    updateMetric(AgentsActiveStats(maxHeap = activeRecord.maxHeap, start = start, series = metrics.toSeries()))
}

fun Plugin.initPersistRecord(activeRecord: ActiveRecord) = activeRecord.initPersistHandler { metrics ->
    val storedActiveRecord = storeClient.updateRecordData(agentId, RecordDao(
        maxHeap = activeRecord.maxHeap,
        metrics = metrics.asSequence().associate {
            it.key to it.value.toList()
        }))
    val series = storedActiveRecord.instances.toSeries()
    agentStats.update { AgentsStats(activeRecord.maxHeap, true, storedActiveRecord.breaks, series) }
}
