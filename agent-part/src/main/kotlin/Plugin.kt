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

import com.epam.drill.logger.api.LoggerFactory
import com.epam.drill.plugin.api.processing.*
import com.epam.drill.plugins.tracer.common.api.*
import com.epam.drill.plugins.tracer.util.AsyncJobDispatcher
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ticker
import kotlinx.serialization.json.Json
import org.hyperic.sigar.Sigar

@Suppress("unused")
class Plugin(
    id: String,
    agentContext: AgentContext,
    sender: Sender,
    logging: LoggerFactory,
) : AgentPart<AgentAction>(id, agentContext, sender, logging), Instrumenter {
    private val logger = logging.logger("Plugin $id")

    internal val json = Json { encodeDefaults = true }

    private val _enabled = atomic(false)

    private val enabled: Boolean get() = _enabled.value

    private val sigar: Sigar

    init {
        initNativeLibraries(logger)
        sigar = Sigar()
    }

    override fun on() {
        sendMessage(InitializedAgent(msg = "Initialized", sigar.mem.total))
        logger.info { "Plugin $id initialized!" }
    }

    override fun off() {
        logger.info { "Enabled $enabled" }
        logger.info { "Plugin $id is off" }
    }

    override fun instrument(
        className: String,
        initialBytes: ByteArray,
    ): ByteArray? = takeIf { enabled }?.run {
        null
    }

    override fun destroyPlugin(unloadReason: UnloadReason) {}

    override fun initPlugin() {
        logger.info { "Plugin $id: initializing..." }
    }

    private val _activeRecord = atomic<Job?>(null)

    private fun memJob(refreshRate: Long = 5000) = AsyncJobDispatcher.launch {
        for (event in ticker(refreshRate)) {
            sendMessage(StateFromAgent(StatePayload(
                AgentMetric(System.currentTimeMillis(), Memory(sigar.mem.used))))
            )
        }
    }

    override suspend fun doAction(action: AgentAction) {
        when (action) {
            is StartAgentRecord -> action.payload.run {
                _activeRecord.updateAndGet { records ->
                    records?.let {
                        logger.warn { "Record already started" }
                        records
                    } ?: memJob(refreshRate)
                }
            }
            is StopAgentRecord -> {
                _activeRecord.updateAndGet { records ->
                    records?.cancel()
                    null
                }
            }
            else -> Unit
        }
    }

    override fun parseAction(rawAction: String): AgentAction {
        return json.decodeFromString(AgentAction.serializer(), rawAction)
    }
}

fun Plugin.sendMessage(message: TracerMessage) {
    val messageStr = json.encodeToString(TracerMessage.serializer(), message)
    send(messageStr)
}
