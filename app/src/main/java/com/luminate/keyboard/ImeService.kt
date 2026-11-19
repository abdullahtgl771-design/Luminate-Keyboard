package com.luminate.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager

class ImeService : InputMethodService(), KeyboardView.Listener {
    private var keyboard: KeyboardView? = null

    override fun onCreateInputView(): View {
        return KeyboardView(this, this).also { keyboard = it }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboard?.reset()
        refreshShift()
    }

    override fun onText(text: String) {
        currentInputConnection?.commitText(text, 1)
        refreshShift()
    }

    override fun onDelete() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        refreshShift()
    }

    override fun onEnter() {
        val info = currentInputEditorInfo
        val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noAction = info != null && (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (noAction || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        } else {
            currentInputConnection?.performEditorAction(action)
        }
        refreshShift()
    }

    override fun onClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip()) {
            val clip: ClipData? = clipboard.primaryClip
            val text = clip?.getItemAt(0)?.coerceToText(this)
            if (!text.isNullOrEmpty()) currentInputConnection?.commitText(text, 1)
        }
    }

    override fun onSettings() {
        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onPicker() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    private fun refreshShift() {
        val info = currentInputEditorInfo ?: return
        val caps = currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0
        keyboard?.setAutoShift(caps != 0)
    }
}
