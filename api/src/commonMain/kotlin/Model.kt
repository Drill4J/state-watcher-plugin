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
package com.epam.drill.plugins.tracer.api

import kotlinx.serialization.*

@Serializable
data class StatePayload(
    val recordId: String,
    val memory: Metric,
)

@Serializable
data class Metric(
    val timeStamp: Long,
    val memory: Memory,
)

@Serializable
data class Memory(
    val heap: Long,
)

@Serializable
data class StartRecordPayload(val refreshRate: Long = 5000L)

@Serializable
data class RecordDataSettings(val from: Long, val to: Long, val instanceIds: Set<String> = emptySet())


@Serializable
data class AgentsStats(
    val maxHeap: Long = 0,
    val isMonitoring: Boolean = false,
    val brakes: List<Long> = emptyList(),
    val series: List<Series> = emptyList(),
)

@Serializable
data class AgentsActiveStats(
    val maxHeap: Long = 0,
    val start: Long = 0,
    val series: List<Series> = emptyList(),
)

@Serializable
data class Series(val instanceId: String, val data: List<Metric>)

