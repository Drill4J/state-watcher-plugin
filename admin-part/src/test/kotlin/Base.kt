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

import com.epam.drill.plugins.tracer.api.*
import com.epam.drill.plugins.tracer.storage.*
import com.epam.drill.plugins.tracer.util.*
import kotlin.test.*

class Base {

    @Test
    fun `merge instance date`() {

        val instanceData = InstanceData("id", listOf(Metric(1, Memory(1)), Metric(2, Memory(2))))
        val instanceData2 = InstanceData("id", listOf(Metric(3, Memory(3)), Metric(4, Memory(4))))

        val first: Set<InstanceData> = mutableSetOf(instanceData)
        val second: Set<InstanceData> = mutableSetOf(instanceData2)

        assert((first + second).first().metrics.size == 4)

    }
}
