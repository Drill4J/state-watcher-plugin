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
import com.epam.drill.plugins.tracer.api.routes.*
import com.epam.drill.plugins.tracer.common.api.*
import com.epam.drill.plugins.tracer.common.api.AgentBreak
import com.epam.drill.plugins.tracer.common.api.StartRecordPayload
import com.epam.drill.plugins.tracer.storage.*
import com.epam.drill.plugins.tracer.util.*
import com.epam.kodux.*
import kotlinx.atomicfu.*
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

    internal val buildVersion = agentInfo.buildVersion

    internal val agentId = agentInfo.id

    private val _activeRecord = atomic<ActiveRecord?>(null)

    private val maxHeap = atomic(0L)



    override suspend fun initialize() {
        storeClient.loadRecordData(AgentId(agentId, buildVersion))?.let { record ->
            maxHeap.value = record.maxHeap
        }
    }

    override suspend fun applyPackagesChanges() {
    }

    //Actions from agent
    override suspend fun processData(instanceId: String, content: String): Any {
        when (val message = TracerMessage.serializer() parse content) {
            is InitializedAgent -> {
                logger.info { "Plugin $id for instance $instanceId is initialized, max heap size = ${message.maxHeap}" }
                storeClient.store(
                    storeClient.loadRecordData(AgentId(agentId, buildVersion))?.copy(
                        maxHeap = message.maxHeap
                    ) ?: StoredRecordData(AgentId(agentId, buildVersion), maxHeap = message.maxHeap)
                )
                maxHeap.update { message.maxHeap }
            }
            is StateFromAgent -> message.payload.run {
                val metric = Metric(agentMetric.timeStamp, Memory(agentMetric.memory.heap))
                _activeRecord.value?.addMetric(instanceId, metric)
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
        data: Any?,
    ): ActionResult = when (action) {
        is StartRecord -> action.payload.run {
            if (_activeRecord.value == null) {
                val record = _activeRecord.updateAndGet {
                    ActiveRecord(currentTimeMillis(), maxHeap.value).also {
                        initSendRecord(it)
                        initPersistRecord(it)
                    }
                }
                logger.info { "Record has started at ${record?.start}" }
                val breaks = storeClient.loadRecordData(
                    AgentId(agentId, buildVersion)
                )?.breaks?.getGap(record?.start)?.map {
                    AgentBreak(it.from, it.to)
                } ?: emptyList()
                StartAgentRecord(StartRecordPayload(
                    refreshRate = refreshRate,
                    breaks = breaks
                )).toActionResult()
            } else ActionResult(StatusCodes.BAD_REQUEST, "Recode already started")
        }
        is StopRecord -> {
            val activeRecord = _activeRecord.getAndUpdate { null }
            val recordData = activeRecord?.stopRecording()?.let { dao ->
                logger.info { "Record has stopped at ${dao.stop}" }
                storeClient.updateRecordData(AgentId(agentId, buildVersion), dao)
            }
            //TODO fix
            StopAgentRecord(StopRecordPayload(false,
                recordData?.breaks?.getGap(activeRecord.start)?.map { AgentBreak(it.from, it.to) }
                    ?: emptyList())).toActionResult()
        }
        is RecordData -> action.payload.run {
            val stats = storeClient.loadRecordData(
                AgentId(agentId, buildVersion),
                instanceIds,
                from..to,
            )
            ActionResult(StatusCodes.OK,
                stats.copy(
                    maxHeap = maxHeap.value,
                    isMonitoring = _activeRecord.value != null,
                    breaks = stats.breaks.getGap(_activeRecord.value?.start),
                ))
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

    internal suspend fun updateMetric(agentsStats: AgentsActiveStats) = send(
        buildVersion,
        Routes.Metrics.HeapState(Routes.Metrics()).let { Routes.Metrics.HeapState.UpdateHeap(it) },
        agentsStats
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

