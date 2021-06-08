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
sealed class TraceMessage

@SerialName("AGENT_STATE")
@Serializable
data class TraceState(val payload: StatePayload) : TraceMessage()

@SerialName("INITIALIZED")
@Serializable
data class Initialized(val msg: String = "", val maxHeap: Long = 0) : TraceMessage()
