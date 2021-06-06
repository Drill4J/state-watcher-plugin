plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("kotlinx-atomicfu")
    id("com.github.johnrengelman.shadow")
}


repositories {
    maven ( url = "https://repo.typesafe.com/typesafe/maven-releases/" )
}
val jarDeps by configurations.creating {
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
}
configurations.implementation {
    extendsFrom(jarDeps)
}

dependencies {
    jarDeps(project(":agent-api"))
    jarDeps("org.jacoco:org.jacoco.core")
    jarDeps("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm") { isTransitive = false }
    jarDeps("io.github.microutils:kotlin-logging:1.7.10")
    jarDeps("org.hyperic:sigar:1.6.4")
    implementation(kotlin("stdlib"))

    //provided by drill runtime
    implementation("com.epam.drill:drill-agent-part")
    implementation("com.epam.drill:common")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    compileOnly("org.jetbrains.kotlinx:atomicfu")

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.jetbrains.kotlinx:atomicfu")
}

tasks {
    test {
        useJUnitPlatform()
        systemProperty("plugin.feature.drealtime", false)
    }

    fun com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.commonConfig() {
        isZip64 = true
        archiveFileName.set("agent-part.jar")
        configurations = listOf(jarDeps)
        dependencies {
            exclude(
                "/META-INF/**",
                "/*.class",
                "/*.html"
            )
        }
        from(file("nativeLib"))
        listOf(
            "mu",
            "org.slf4j",
            "org.objectweb.asm",
            "org.jacoco.core",
            "kotlinx.collections.immutable"
        ).forEach { relocate(it, "${rootProject.group}.tracer.shadow.$it") }
    }

    shadowJar {
        commonConfig()
        relocate("kotlin", "kruntime")
        relocate("kotlinx", "kruntimex")
    }
    //TODO remove after fixes in test framework
    val shadowJarTest by registering(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class)
    shadowJarTest {
        group = "shadow"
        from(jar)
        commonConfig()
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.freeCompilerArgs = listOf(
            "-Xuse-experimental=kotlin.ExperimentalStdlibApi",
            "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xuse-experimental=kotlinx.coroutines.FlowPreview",
            "-Xuse-experimental=kotlinx.coroutines.InternalCoroutinesApi",
            "-Xuse-experimental=kotlinx.coroutines.ObsoleteCoroutinesApi"
        )
    }
}
