package com.luminate.keyboard

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo

class ImeService : InputMethodService(), KeyboardView.Listener {
    private var kbd: KeyboardView? = null

    override fun onCreateInputView(): View {
        val v = KeyboardView(this, this)
        kbd = v
        return v
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        kbd?.reset()
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
        val ei = currentInputEditorInfo
        val action = (ei?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noEnterAction = ei != null && (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (noEnterAction || action == EditorInfo.IME_ACTION_NONE ||
            action == EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        } else {
            currentInputConnection?.performEditorAction(action)
        }
        refreshShift()
    }

    private fun refreshShift() {
        val ei = currentInputEditorInfo ?: return
        val caps = currentInputConnection?.getCursorCapsMode(ei.inputType) ?: 0
        kbd?.setAutoShift(caps != 0)
    }
}
