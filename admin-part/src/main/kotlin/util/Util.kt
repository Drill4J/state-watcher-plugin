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
package com.epam.drill.plugins.tracer.util

import com.epam.drill.plugins.tracer.Plugin.Companion.json
import com.epam.drill.plugins.tracer.api.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import java.util.*
import java.util.concurrent.*
import kotlin.collections.HashMap


infix fun <T> KSerializer<T>.parse(rawData: String): T = json.decodeFromString(this, rawData)
infix fun <T> KSerializer<T>.stringify(rawData: T) = json.encodeToString(this, rawData)

fun currentTimeMillis() = System.currentTimeMillis()

fun genUuid() = "${UUID.randomUUID()}"

internal val availableProcessors = Runtime.getRuntime().availableProcessors()

internal object AsyncJobDispatcher : CoroutineScope {
    override val coroutineContext = Executors.newFixedThreadPool(availableProcessors).asCoroutineDispatcher()
}

operator fun Map<String, List<Metric>>.plus(
    map: Map<String, List<Metric>>,
): Map<String, List<Metric>> = HashMap<String, List<Metric>>(this).apply {
    map.asSequence().forEach {
        merge(it.key, it.value) { list, list1 -> list + list1 }
    }
}

fun Map<String, List<Metric>>.toSeries() = map { Series(it.key, it.value) }

