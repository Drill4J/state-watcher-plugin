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


fun Plugin.initSendRecord(activeRecord: ActiveRecord) = activeRecord.initSendHandler { metrics ->
    updateMetric(AgentsActiveStats(maxHeap = activeRecord.maxHeap, series = metrics.toSeries()))
}

fun Plugin.initPersistRecord(activeRecord: ActiveRecord) = activeRecord.initPersistHandler { metrics ->
    storeClient.updateRecordData(
        AgentId(agentId, buildVersion),
        RecordDao(
            maxHeap = activeRecord.maxHeap,
            start = activeRecord.start,
            metrics = metrics.asSequence().associate {
                it.key to it.value.toList()
            }
        )
    )
}
