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

import com.epam.drill.logger.api.Logger
import java.io.File
import java.util.jar.JarFile

internal fun initNativeLibraries(logger: Logger) {
    val currentJar = File(Plugin::class.java.protectionDomain.codeSource.location.file)
    val jarFile = JarFile(currentJar)
    jarFile.use { jf ->
        val entries = jf.entries()
        while (entries.hasMoreElements()) {
            val it = entries.nextElement()
            if (it.name.startsWith("lib/") && !it.isDirectory) {
                val file = File(currentJar.parentFile, it.name.replace("lib/", ""))
                logger.debug { "native lib '${file.name} was extracted into $file" }
                file.createNewFile()
                file.writeBytes(jf.getInputStream(it).readBytes())

            }
        }
    }
    System.setProperty("org.hyperic.sigar.path", currentJar.parent)
}