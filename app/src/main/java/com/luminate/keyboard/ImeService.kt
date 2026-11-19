package com.luminate.keyboard

import android.content.ClipboardManager
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast

class ImeService : InputMethodService(), KeyboardView.Listener {
    private var keyboard:KeyboardView?=null
    override fun onCreateInputView():View=KeyboardView(this,this).also{keyboard=it}
    override fun onStartInputView(info:EditorInfo?,restarting:Boolean){super.onStartInputView(info,restarting);keyboard?.reset();refreshShift()}
    override fun onText(text:String){currentInputConnection?.commitText(text,1);refreshShift()}
    override fun onDelete(){sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);refreshShift()}
    override fun onEnter(){
        val info=currentInputEditorInfo
        val action=(info?.imeOptions?:0) and EditorInfo.IME_MASK_ACTION
        val noAction=info!=null&&(info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION)!=0
        if(noAction||action==EditorInfo.IME_ACTION_NONE||action==EditorInfo.IME_ACTION_UNSPECIFIED)sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        else currentInputConnection?.performEditorAction(action)
        refreshShift()
    }
    override fun onClipboard(){
        val clip=(getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        if(clip!=null&&clip.itemCount>0){val value=clip.getItemAt(0).coerceToText(this);if(!value.isNullOrEmpty())currentInputConnection?.commitText(value,1)}
    }
    override fun onSettings(){startActivity(Intent(this,SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
    override fun onEmoji(){onText("😊")}
    override fun onShare(){
        val send=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,"I'm using Luminate Keyboard!");addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)}
        startActivity(Intent.createChooser(send,"Share Luminate Keyboard").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    override fun onTextEdit(action:Int){currentInputConnection?.performContextMenuAction(action)}
    override fun onTranslate(){Toast.makeText(this,"Translation is not configured yet",Toast.LENGTH_SHORT).show()}
    override fun onVoice(){Toast.makeText(this,"Use voice input from your device keyboard picker",Toast.LENGTH_SHORT).show()}
    override fun onGif(){Toast.makeText(this,"GIF search is not configured yet",Toast.LENGTH_SHORT).show()}
    private fun refreshShift(){val info=currentInputEditorInfo?:return;val caps=currentInputConnection?.getCursorCapsMode(info.inputType)?:0;keyboard?.setAutoShift(caps!=0)}
}
