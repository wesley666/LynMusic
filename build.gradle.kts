plugins {
    base
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlinAndroid) apply false
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
