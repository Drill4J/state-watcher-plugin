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


import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugins.tracer.api.*
import com.epam.drill.plugins.tracer.api.Memory
import com.epam.drill.plugins.tracer.api.Metric
import com.epam.drill.plugins.tracer.api.routes.*
import com.epam.drill.plugins.tracer.common.api.*
import com.epam.drill.plugins.tracer.common.api.StartRecordPayload
import com.epam.drill.plugins.tracer.common.api.StopRecordPayload
import com.epam.drill.plugins.tracer.storage.*
import com.epam.drill.plugins.tracer.util.*
import com.epam.kodux.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.json.*
import mu.*
import java.io.*

@Suppress("unused")
class Plugin(
    adminData: AdminData,
    sender: Sender,
    val storeClient: StoreClient,
    agentInfo: AgentInfo,
    id: String,
) : AdminPluginPart<Action>(
    id = id,
    agentInfo = agentInfo,
    adminData = adminData,
    sender = sender
), Closeable {
    companion object {
        val json = Json { encodeDefaults = true }
    }

    private val logger = logger(agentInfo.id)

    private val buildVersion = agentInfo.buildVersion

    private val agentId = agentInfo.id

    internal val maxHeap = atomic(0L)

    private val _expected = atomic(persistentHashSetOf<String>())

    private val recordManager = RecordManager(this)


    override suspend fun initialize() {
        storeClient.getAll<FinishedRecord>().takeIf { it.isNotEmpty() }?.let {
            val reduce = it.reduce { finishedRecord, finishedRecord1 ->
                FinishedRecord("", finishedRecord.maxHeap, finishedRecord.metrics.merge(finishedRecord1.metrics))
            }
            sendMetrics(AgentsStats("", reduce.maxHeap, reduce.metrics.toSeries()))
        }
    }


    override suspend fun applyPackagesChanges() {
    }

    //Actions from agent
    override suspend fun processData(instanceId: String, content: String): Any {
        when (val message = TracerMessage.serializer() parse content) {
            is InitializedAgent -> {
                logger.info { "Plugin $id for instance $instanceId is initialized, max heap size = ${message.maxHeap}" }
                maxHeap.update { message.maxHeap }
            }
            is StateFromAgent -> message.payload.run {
                val metric = Metric(agentMetric.timeStamp, Memory(agentMetric.memory.heap))
                recordManager.addMetric(instanceId, recordId, metric)
            }
            else -> {
                logger.info { "type $message do not supported yet" }
            }
        }
        return ""
    }


    //Actions from admin
    override suspend fun doAction(
        action: Action,
    ): ActionResult = when (action) {
        is StartRecord -> action.payload.run {
            val recordId = recordId.takeIf { it.isNotBlank() } ?: genUuid()
            recordManager.startRecord(recordId)
            logger.info { "Record has started $recordId" }
            StartAgentRecord(StartRecordPayload(
                recordId,
                refreshRate
            )).toActionResult()
        }
        is StopRecord -> action.payload.run {
            logger.info { "Record has stopped $action" }
            recordManager.stopRecord(recordId)
            StopAgentRecord(StopRecordPayload(recordId)).toActionResult()
        }
        else -> {
            logger.info { "Action '$action' is not supported!" }
            ActionResult(StatusCodes.BAD_REQUEST, "Action '$action' is not supported!")
        }
    }


    override fun parseAction(
        rawAction: String,
    ): Action = Action.serializer() parse rawAction


    override fun close() {
    }

    internal suspend fun sendMetrics(state: AgentsStats) = send(
        buildVersion,
        Routes.Metrics().let { Routes.Metrics.HeapState(it) },
        AgentsStats.serializer() stringify state
    )

    internal suspend fun updateMetric(agentsStats: AgentsStats) = send(
        buildVersion,
        Routes.Metrics.HeapState(Routes.Metrics()).let { Routes.Metrics.HeapState.UpdateHeap(it) },
        AgentsStats.serializer() stringify agentsStats
    )

    internal suspend fun send(buildVersion: String, destination: Any, message: Any) {
        sender.send(AgentSendContext(agentInfo.id, buildVersion), destination, message)
    }

}

internal fun Any.logger(vararg fields: String): KLogger = run {
    val name = "trace"
    val suffix = fields.takeIf { it.any() }?.joinToString(prefix = "(", postfix = ")").orEmpty()
    KotlinLogging.logger("$name$suffix")
}

