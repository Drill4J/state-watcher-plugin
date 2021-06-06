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


import com.epam.drill.common.AgentInfo
import com.epam.drill.plugin.api.AdminData
import com.epam.drill.plugin.api.end.AdminPluginPart
import com.epam.drill.plugin.api.end.AgentSendContext
import com.epam.drill.plugin.api.end.Sender
import com.epam.drill.plugins.tracer.api.Action
import com.epam.drill.plugins.tracer.api.HeapState
import com.epam.drill.plugins.tracer.api.TracerMessage
import com.epam.drill.plugins.tracer.api.routes.Routes
import com.epam.kodux.StoreClient
import kotlinx.atomicfu.atomic
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import mu.KLogger
import mu.KotlinLogging
import java.io.Closeable

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
    val buildVersion = agentInfo.buildVersion

    private val agentId = agentInfo.id


    private val _expected = atomic(persistentHashSetOf<String>())


    private val storage = mutableMapOf<String, String>()

    @OptIn(ObsoleteCoroutinesApi::class)
    override suspend fun initialize() {
        GlobalScope.launch {
            for (event in ticker(5000)) {
                val map = storage.map {
                    "" + System.currentTimeMillis() +" " + it.key + it.value
                }
                println(map)
                sendHeapState(map)
            }
        }
    }

    override suspend fun applyPackagesChanges() {
    }

    override suspend fun processData(instanceId: String, content: String): Any {
        val message = json.decodeFromString(TracerMessage.serializer(), content)
        when (message) {
            is HeapState -> {
                storage[instanceId] = message.msg
            }
            else -> {
                logger.info { "type $message do not supported yet" }
            }
        }

        return ""
    }

    override suspend fun doAction(action: Action): Any {
        return logger.info { "Action '$action' is not supported!" }
    }

    override fun parseAction(
        rawAction: String,
    ): Action = json.decodeFromString(Action.serializer(), rawAction)


    override fun close() {
    }

    private suspend fun sendHeapState(state: List<String>) = send(
        buildVersion,
        Routes.Metrics().let { Routes.Metrics.HeapState(it) },
        state
    )

    internal suspend fun send(buildVersion: String, destination: Any, message: Any) {
        sender.send(AgentSendContext(agentInfo.id, buildVersion), destination, message)
    }


}

internal fun Any.logger(vararg fields: String): KLogger = run {
    val name = "tst"
    val suffix = fields.takeIf { it.any() }?.joinToString(prefix = "(", postfix = ")").orEmpty()
    KotlinLogging.logger("$name$suffix")
}

