package top.iwesley.lyn.music.tv

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
    private val crashReport: String
        get() = intent
            ?.getStringExtra(EXTRA_ANDROID_CRASH_REPORT)
            ?.takeIf { it.isNotBlank() }
            ?: "未收到崩溃堆栈。"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContentView(crashReport))
    }

    private fun buildContentView(report: String): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        root.addView(
            TextView(this).apply {
                text = "LynMusic 遇到未捕获异常"
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
                text = "应用主进程已停止。你可以复制下面的崩溃堆栈给开发者用于排查。"
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
            text = report
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
                        text = "复制堆栈"
                        setOnClickListener { copyCrashReport(report) }
                    },
                )
                addView(
                    Button(this@CrashReportActivity).apply {
                        text = "关闭"
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
        Toast.makeText(this, "已复制崩溃堆栈", Toast.LENGTH_SHORT).show()
    }

    private fun closeCrashReportProcess() {
        finishAndRemoveTask()
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
