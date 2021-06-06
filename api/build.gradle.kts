plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    `maven-publish`
}

kotlin {
    sourceSets.commonMain {
        dependencies {
            //provided by drill runtime or clients
            compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-core")
        }
    }

    jvm {
        val main by compilations
        main.defaultSourceSet {
            dependencies {
                //provided by drill runtime or clients
                compileOnly("io.ktor:ktor-locations") { isTransitive = false }
            }
        }
    }
}

val kt2dts by configurations.creating

dependencies {
    kt2dts("com.epam.drill.ts:kt2dts-cli:0.3.0")
}

val genTsd by tasks.registering(JavaExec::class) {
    group = "ts"
    classpath = kt2dts
    val main by kotlin.jvm().compilations
    dependsOn(main.compileAllTaskName)
    CommandLineArgumentProvider {
        val genPaths = main.output.classesDirs + main.runtimeDependencyFiles.files + main.compileDependencyFiles.files
        val drillPluginId: String by project.extra
        val tsDir = buildDir.resolve("ts").apply { mkdirs() }
        val tsdFile = tsDir.resolve("$drillPluginId.d.ts")
        mutableListOf(
            "--module=@drill4j/$drillPluginId-types",
            "--output=${tsdFile.path}",
            "--cp=${genPaths.joinToString(separator = ",")}"
        )
    }.let { argumentProviders += it }
}

tasks.compileKotlinMetadata {
    dependsOn(genTsd)
}
