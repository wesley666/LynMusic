plugins {
    base
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

abstract class VerifyUiModuleBoundaries : DefaultTask() {
    @get:Input
    abstract val dependencyEdges: ListProperty<String>

    @TaskAction
    fun verify() {
        val pages = setOf(":ui:mobile", ":ui:desktop", ":ui:automotive", ":tvApp")
        val graph = dependencyEdges.get().map { it.split(" -> ") }
            .groupBy({ it[0] }, { it[1] })
        val failures = mutableSetOf<String>()
        for (origin in graph.keys) {
            val visited = mutableSetOf(origin)
            fun visit(node: String, path: List<String>) {
                for (target in graph[node].orEmpty()) {
                    val route = path + target
                    val forbidden = when {
                        target == ":player:app" -> true
                        origin in pages -> target in pages && target != origin
                        origin == ":ui:common" -> target !in setOf(":shared:core", ":shared:data", ":shared:resources")
                        origin == ":app:runtime" -> target in pages || target.startsWith(":ui:")
                        origin.startsWith(":shared:") || origin == ":player:core" ||
                            origin.startsWith(":cast:") || origin == ":android:runtime" ->
                            target in pages || target.startsWith(":ui:") || target == ":app:runtime"
                        else -> false
                    }
                    if (forbidden) failures += route.joinToString(" -> ")
                    if (visited.add(target)) visit(target, route)
                }
            }
            visit(origin, listOf(origin))
        }
        check(failures.isEmpty()) { "UI module dependency boundaries violated:\n" + failures.joinToString("\n") }
        logger.lifecycle("UI module dependency boundaries verified (including transitive dependencies).")
    }
}

val verifyUiModuleBoundaries = tasks.register<VerifyUiModuleBoundaries>("verifyUiModuleBoundaries") {
    group = "verification"
    description = "Reject cross-platform page dependencies and reverse dependencies from shared modules."
}

val fullTest = tasks.register<FullTest>("fullTest") {
    group = "verification"
    description = "Run every JVM, Android-KMP host, and Android Debug/Release unit test and verify coverage."
    reportDirectory.set(layout.buildDirectory.dir("reports/fullTest"))
    expectedMinimumCounts.putAll(mapOf(
        ":app:runtime:jvmTest" to 17,
        ":cast:api:jvmTest" to 9,
        ":composeApp:jvmTest" to 380,
        ":player:core:jvmTest" to 407,
        ":shared:core:jvmTest" to 161,
        ":shared:data:jvmTest" to 520,
        ":shared:features:jvmTest" to 271,
        ":shared:resources:jvmTest" to 4,
        ":ui:automotive:jvmTest" to 301,
        ":ui:common:jvmTest" to 57,
        ":ui:desktop:jvmTest" to 283,
        ":ui:mobile:jvmTest" to 285,
        ":cast:api:testAndroidHostTest" to 9,
        ":composeApp:testAndroidHostTest" to 67,
        ":player:core:testAndroidHostTest" to 265,
        ":shared:core:testAndroidHostTest" to 95,
        ":shared:data:testAndroidHostTest" to 100,
        ":shared:features:testAndroidHostTest" to 271,
        ":ui:mobile:testAndroidHostTest" to 16,
        ":ui:automotive:testAndroidHostTest" to 16,
        ":android:runtime:testDebugUnitTest" to 67,
        ":android:runtime:testReleaseUnitTest" to 67,
        ":cast:upnp:android:testDebugUnitTest" to 15,
        ":cast:upnp:android:testReleaseUnitTest" to 15,
        ":tvApp:testDebugUnitTest" to 37,
        ":tvApp:testReleaseUnitTest" to 37,
    ))
    val unitTests = subprojects.flatMap { it.tasks.withType<Test>().toList() }
    dependsOn(unitTests)
    unitTests.forEach { test ->
        // Dependencies are explicit above; snapshot report paths rather than inferring
        // task ownership from Gradle's nested Report.outputLocation property.
        val directory = test.reports.junitXml.outputLocation.get().asFile
        resultDirectories.put(test.path, directory.absolutePath)
        junitReports.from(fileTree(directory) { include("TEST-*.xml") })
    }
}

// Capture plain strings after all source sets have been configured; the task remains configuration-cache safe.
gradle.projectsEvaluated {
    val edges = allprojects.flatMap { source ->
        source.configurations.flatMap { configuration ->
            configuration.dependencies.withType<ProjectDependency>().filter { it.path != source.path }.map { dependency ->
                "${source.path} -> ${dependency.path}"
            }
        }
    }.distinct().sorted()
    verifyUiModuleBoundaries.configure { dependencyEdges.set(edges) }
}

tasks.named("check") { dependsOn(verifyUiModuleBoundaries) }
subprojects {
    tasks.configureEach {
        if (name == "check" || name == "preBuild" || (name.startsWith("compile") && name.contains("Kotlin"))) {
            dependsOn(verifyUiModuleBoundaries)
        }
    }
}
