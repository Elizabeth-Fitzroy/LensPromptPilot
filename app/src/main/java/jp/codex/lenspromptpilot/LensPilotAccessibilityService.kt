package jp.codex.lenspromptpilot

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class LensPilotAccessibilityService : AccessibilityService() {
    private var active = false
    private var promptSubmitted = false
    private var fieldOpened = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() { instance = this }

    fun startPilot() {
        active = true
        promptSubmitted = false
        fieldOpened = false
        inspectScreen()
    }

    fun stopPilot() {
        active = false
        promptSubmitted = false
        fieldOpened = false
        handler.removeCallbacksAndMessages(null)
        Toast.makeText(this, "自動操作を停止しました", Toast.LENGTH_SHORT).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (active) inspectScreen()
    }

    private fun inspectScreen() {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != LENS_PACKAGE) return
        // The reference screenshot shows this label on the Lens results prompt field.
        // Do not target arbitrary editable fields or the unlabeled camera shutter.
        if (promptSubmitted) return
        val searchField = findNode(root) { node ->
            val label = "${node.text ?: ""} ${node.contentDescription ?: ""}"
            label.contains("この画像について質問する") ||
                label.contains("Ask about this image", ignoreCase = true)
        }
        if (searchField != null) {
            if (!fieldOpened) {
                fieldOpened = searchField.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                searchField.recycle()
                if (fieldOpened) handler.postDelayed({ if (active) inspectScreen() }, 500)
                return
            }
            searchField.recycle()
            if (fillFocusedEditable(root)) {
                promptSubmitted = true
                active = false
            }
        }
    }

    private fun fillFocusedEditable(node: AccessibilityNodeInfo): Boolean {
        if (node.isEditable && node.isEnabled && node.isFocused) {
            val prompt = getSharedPreferences("pilot", MODE_PRIVATE)
                .getString(MainActivity.KEY_PROMPT, "")?.trim().orEmpty()
            if (prompt.isEmpty()) return false
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, prompt)
            }
            val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (ok) {
                Toast.makeText(this, "定型プロンプトを入力しました。内容を確認して送信してください", Toast.LENGTH_LONG).show()
                active = false
            }
            return ok
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = fillFocusedEditable(child)
            child.recycle()
            if (found) return true
        }
        return false
    }

    private fun findNode(node: AccessibilityNodeInfo, matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (matches(node)) return AccessibilityNodeInfo.obtain(node)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = findNode(child, matches)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    override fun onInterrupt() { stopPilot() }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val LENS_PACKAGE = "com.google.android.googlequicksearchbox"
        @Volatile var instance: LensPilotAccessibilityService? = null
    }
}
