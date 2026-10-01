package top.iwesley.lyn.music.automotive

import top.iwesley.lyn.music.platform.AndroidCrashReportText
import top.iwesley.lyn.music.core.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Process
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt
import kotlin.system.exitProcess

class CrashReportActivity : Activity() {
    private val textScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val crashReport: String?
        get() = intent
            ?.getStringExtra(EXTRA_ANDROID_CRASH_REPORT)
            ?.takeIf { it.isNotBlank() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContentView(crashReport))
    }

    private fun buildContentView(report: String?): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        root.addView(
            TextView(this).apply {
                bindText(this, AndroidCrashReportText.Title)
                setTextColor(Color.rgb(23, 23, 23))
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        root.addView(
            TextView(this).apply {
                bindText(this, AndroidCrashReportText.Instructions)
                setTextColor(Color.rgb(68, 68, 68))
                textSize = 14f
                setPadding(0, dp(8), 0, dp(16))
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val reportText = TextView(this).apply {
            if (report != null) {
                text = report
            } else {
                bindText(this, AndroidCrashReportText.MissingStackTrace)
            }
            setTextColor(Color.rgb(32, 32, 32))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        root.addView(
            ScrollView(this).apply {
                setBackgroundColor(Color.rgb(245, 245, 245))
                addView(
                    reportText,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        root.addView(
            LinearLayout(this).apply {
                gravity = Gravity.END
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(16), 0, 0)
                addView(
                    Button(this@CrashReportActivity).apply {
                        bindText(this, AndroidCrashReportText.CopyStackTrace)
                        isEnabled = report != null
                        setOnClickListener { if (report != null) copyCrashReport(report) }
                    },
                )
                addView(
                    Button(this@CrashReportActivity).apply {
                        bindText(this, AndroidCrashReportText.Close)
                        setOnClickListener { closeCrashReportProcess() }
                    },
                )
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        return root
    }

    private fun copyCrashReport(report: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("LynMusic crash report", report))
        textScope.launch {
            Toast.makeText(this@CrashReportActivity, AndroidCrashReportText.StackTraceCopied.resolve(), Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindText(view: TextView, label: AndroidCrashReportText) {
        view.text = label.fallbackText()
        textScope.launch {
            observeResolvedUiText(
                descriptions = flowOf(uiText(label.resource)),
                resolve = { _, language -> label.resolve(language) },
            ) { value, _ -> view.text = value.orEmpty() }
        }
    }

    override fun onDestroy() {
        textScope.cancel()
        super.onDestroy()
    }

    private fun closeCrashReportProcess() {
        finishAndRemoveTask()
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
