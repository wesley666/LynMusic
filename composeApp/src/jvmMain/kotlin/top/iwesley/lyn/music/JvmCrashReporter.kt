package top.iwesley.lyn.music

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.PrintWriter
import java.io.PrintStream
import java.io.StringWriter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import javax.swing.border.EmptyBorder
import kotlin.system.exitProcess

internal fun installJvmUncaughtExceptionHandler() {
    val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
    if (currentHandler is JvmUncaughtExceptionHandler) return
    Thread.setDefaultUncaughtExceptionHandler(JvmUncaughtExceptionHandler())
}

internal fun logJvmStartupFailure(
    stage: String,
    error: Throwable,
    output: PrintStream = System.err,
) {
    output.println("LynMusic Desktop startup failure. stage=$stage")
    error.printStackTrace(output)
}

private class JvmUncaughtExceptionHandler : Thread.UncaughtExceptionHandler {
    private val isHandlingCrash = AtomicBoolean(false)

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        if (!isHandlingCrash.compareAndSet(false, true)) {
            System.err.println("Unhandled exception while crash reporter was already active.")
            throwable.printStackTrace(System.err)
            exitProcess(JVM_CRASH_EXIT_CODE)
        }

        val report = formatJvmCrashReport(threadName = thread.name, throwable = throwable)
        runCatching {
            if (GraphicsEnvironment.isHeadless()) {
                System.err.println(report)
            } else {
                showJvmCrashWindowAndWait(report)
            }
        }.onFailure { reportFailure ->
            System.err.println(report)
            System.err.println("Failed to show LynMusic crash report window.")
            reportFailure.printStackTrace(System.err)
        }
        exitProcess(JVM_CRASH_EXIT_CODE)
    }
}

internal fun formatJvmCrashReport(
    threadName: String,
    throwable: Throwable,
    maxChars: Int = MAX_JVM_CRASH_REPORT_CHARS,
): String {
    val writer = StringWriter()
    PrintWriter(writer).use { printer ->
        printer.println("LynMusic Desktop Crash")
        printer.println("Thread: $threadName")
        printer.println("Exception: ${throwable.javaClass.name}")
        throwable.message?.takeIf { it.isNotBlank() }?.let { message ->
            printer.println("Message: $message")
        }
        printer.println()
        throwable.printStackTrace(printer)
    }
    return truncateJvmCrashReport(writer.toString(), maxChars)
}

private fun truncateJvmCrashReport(report: String, maxChars: Int): String {
    if (report.length <= maxChars) return report
    val suffix = "\n\n[crash report truncated]\n"
    return if (maxChars <= suffix.length) {
        suffix.take(maxChars)
    } else {
        report.take(maxChars - suffix.length) + suffix
    }
}

private fun showJvmCrashWindowAndWait(report: String) {
    if (SwingUtilities.isEventDispatchThread()) {
        val secondaryLoop = Toolkit.getDefaultToolkit().systemEventQueue.createSecondaryLoop()
        val frame = buildJvmCrashFrame(report) {
            secondaryLoop.exit()
        }
        frame.isVisible = true
        secondaryLoop.enter()
        return
    }

    val windowClosed = CountDownLatch(1)
    SwingUtilities.invokeAndWait {
        val frame = buildJvmCrashFrame(report) {
            windowClosed.countDown()
        }
        frame.isVisible = true
    }
    windowClosed.await()
}

private fun buildJvmCrashFrame(report: String, onClosed: () -> Unit): JFrame {
    val closed = AtomicBoolean(false)
    val frame = JFrame()
    val textScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    fun bindText(description: kotlinx.coroutines.flow.Flow<UiText?>, update: (String) -> Unit) {
        textScope.launch {
            observeResolvedUiText(description) { value, isCurrent ->
                SwingUtilities.invokeLater {
                    if (!closed.get() && isCurrent()) update(value.orEmpty())
                }
            }
        }
    }
    bindText(flowOf(uiText(Res.string.crash_report_title))) { frame.title = it }

    fun closeWindow() {
        if (closed.compareAndSet(false, true)) {
            textScope.cancel()
            frame.dispose()
            onClosed()
        }
    }

    val reportText = JTextArea(report).apply {
        isEditable = false
        lineWrap = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        caretPosition = 0
    }
    val copyDescription = MutableStateFlow<UiText?>(uiText(Res.string.crash_copy_stack_trace))
    val copyButton = JButton().apply {
        addActionListener {
            Toolkit.getDefaultToolkit()
                .systemClipboard
                .setContents(StringSelection(report), null)
            copyDescription.value = uiText(Res.string.common_copied)
        }
    }
    val exitButton = JButton().apply {
        addActionListener {
            closeWindow()
        }
    }
    bindText(copyDescription) { copyButton.text = it }
    bindText(flowOf(uiText(Res.string.common_exit))) { exitButton.text = it }
    val instructions = JLabel()
    bindText(flowOf(uiText(Res.string.crash_desktop_report_instructions))) { instructions.text = it }
    val buttonPanel = JPanel().apply {
        add(copyButton)
        add(exitButton)
    }
    val contentPanel = JPanel(BorderLayout(0, 12)).apply {
        border = EmptyBorder(16, 16, 16, 16)
        add(
            instructions,
            BorderLayout.NORTH,
        )
        add(JScrollPane(reportText), BorderLayout.CENTER)
        add(buttonPanel, BorderLayout.SOUTH)
    }

    return frame.apply {
        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        contentPane = contentPanel
        minimumSize = Dimension(720, 480)
        size = Dimension(900, 640)
        setLocationRelativeTo(null)
        addWindowListener(
            object : WindowAdapter() {
                override fun windowClosing(event: WindowEvent) {
                    closeWindow()
                }
            },
        )
    }
}

private const val JVM_CRASH_EXIT_CODE = 10
private const val MAX_JVM_CRASH_REPORT_CHARS = 120_000
