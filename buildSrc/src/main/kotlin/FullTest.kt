import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Checks the test graph as well as the outcome, so a missing source set cannot silently pass. */
abstract class FullTest : DefaultTask() {
    @get:Input
    abstract val resultDirectories: MapProperty<String, String>

    @get:Input
    abstract val expectedMinimumCounts: MapProperty<String, Int>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val junitReports: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val reportDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val counts = mutableMapOf<String, Int>()
        val cases = mutableListOf("task\tclass\tmethod\tstatus")
        val summary = mutableListOf("task\ttests\tfailures\tskipped")
        val problems = mutableListOf<String>()
        var total = 0
        resultDirectories.get().toSortedMap().forEach { (taskPath, directory) ->
            var tests = 0
            var failures = 0
            var skipped = 0
            File(directory).listFiles { file -> file.name.startsWith("TEST-") && file.extension == "xml" }
                .orEmpty().sortedBy(File::getName).forEach { report ->
                    val nodes = factory.newDocumentBuilder().parse(report).getElementsByTagName("testcase")
                    repeat(nodes.length) { index ->
                        val node = nodes.item(index) as org.w3c.dom.Element
                        val failed = node.getElementsByTagName("failure").length > 0 ||
                            node.getElementsByTagName("error").length > 0
                        val ignored = node.getElementsByTagName("skipped").length > 0
                        tests++
                        if (failed) failures++
                        if (ignored) skipped++
                        val status = if (failed) "failed" else if (ignored) "skipped" else "passed"
                        val name = node.getAttribute("name").replace("\t", "\\t").replace("\n", "\\n")
                        cases += "$taskPath\t${node.getAttribute("classname")}\t$name\t$status"
                    }
                }
            counts[taskPath] = tests
            total += tests
            summary += "$taskPath\t$tests\t$failures\t$skipped"
            if (failures > 0 || skipped > 0) problems += "$taskPath: failures=$failures, skipped=$skipped"
        }
        expectedMinimumCounts.get().forEach { (taskPath, minimum) ->
            val actual = counts[taskPath] ?: 0
            if (actual < minimum) problems += "$taskPath: expected at least $minimum tests, found $actual"
        }
        val output = reportDirectory.get().asFile.apply { mkdirs() }
        File(output, "summary.tsv").writeText(summary.joinToString("\n", postfix = "\n"))
        File(output, "cases.tsv").writeText(cases.joinToString("\n", postfix = "\n"))
        check(problems.isEmpty()) { "Unit test coverage/outcome check failed:\n" + problems.joinToString("\n") }
        logger.lifecycle("FULL_TEST_RESULT tasks=${counts.size} tests=$total failures=0 skipped=0")
    }
}
