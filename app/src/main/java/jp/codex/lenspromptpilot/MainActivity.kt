package jp.codex.lenspromptpilot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("pilot", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val prompt = EditText(this).apply {
            hint = "例：この画像について詳しく説明して"
            minLines = 3
            gravity = Gravity.TOP
            setText(prefs.getString(KEY_PROMPT, "この画像について詳しく説明して"))
        }
        val status = TextView(this).apply {
            text = "1. 定型プロンプトを保存\n2. アクセシビリティ設定でこのアプリを有効化\n3. 「入力を開始」を押してGoogle レンズへ移動\n4. 画面下部中央の虫眼鏡ボタンを押す\n5. 結果画面の「この画像について質問する」欄に入った文を確認して検索"
            textSize = 16f
        }
        val save = Button(this).apply {
            text = "プロンプトを保存"
            setOnClickListener {
                prefs.edit().putString(KEY_PROMPT, prompt.text.toString()).apply()
                Toast.makeText(this@MainActivity, "保存しました", Toast.LENGTH_SHORT).show()
            }
        }
        val settings = Button(this).apply {
            text = "アクセシビリティ設定を開く"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val start = Button(this).apply {
            text = "入力待機を開始"
            setOnClickListener {
                val service = LensPilotAccessibilityService.instance
                if (service == null) {
                    Toast.makeText(this@MainActivity, "先にアクセシビリティサービスを有効にしてください", Toast.LENGTH_LONG).show()
                } else {
                    service.startPilot()
                    val launched = packageManager.getLaunchIntentForPackage("com.google.android.googlequicksearchbox")
                    if (launched == null) {
                        service.stopPilot()
                        Toast.makeText(this@MainActivity, "Google アプリを起動できません。Lens を手動で開いてください", Toast.LENGTH_LONG).show()
                    } else {
                        startActivity(launched)
                        Toast.makeText(this@MainActivity, "Lens の結果画面を待っています。撮影後に入力欄へ文を入れます", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        val stop = Button(this).apply {
            text = "停止"
            setOnClickListener { LensPilotAccessibilityService.instance?.stopPilot() }
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(TextView(this@MainActivity).apply { text = "Lens Prompt Pilot"; textSize = 24f })
            addView(status, spaced())
            addView(prompt, spaced())
            addView(save, spaced())
            addView(settings, spaced())
            addView(start, spaced())
            addView(stop, spaced())
        }
        setContentView(column)
    }

    private fun spaced() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = (12 * resources.displayMetrics.density).toInt()
    }

    companion object { const val KEY_PROMPT = "prompt" }
}
