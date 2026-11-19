package com.luminate.keyboard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class SettingsActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("luminate_prefs", MODE_PRIVATE) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = prefs.getString("theme","DARK") != "LIGHT"
        window.statusBarColor = if(dark) Color.rgb(20,21,24) else Color.rgb(238,240,245)
        window.navigationBarColor = if(dark) Color.rgb(20,21,24) else Color.rgb(238,240,245)
        val bg=if(dark) Color.rgb(20,21,24) else Color.rgb(238,240,245)
        val fg=if(dark) Color.WHITE else Color.rgb(30,34,42)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(22.dp(),28.dp(),22.dp(),20.dp());setBackgroundColor(bg)}
        fun text(value:String,size:Float)=TextView(this).apply{text=value;textSize=size;setTextColor(fg);setPadding(0,10.dp(),0,10.dp())}
        fun button(value:String,action:()->Unit)=Button(this).apply{text=value;isAllCaps=false;setOnClickListener{action()}}
        root.addView(text("Luminate Keyboard",27f))
        root.addView(text("Appearance",18f))
        root.addView(text("Choose your keyboard style",14f))
        listOf("DARK" to "Dark theme","LIGHT" to "Light theme","LIQUID" to "Apple Liquid Glass").forEach{(value,label)->
            root.addView(button(label+(if(prefs.getString("theme","DARK")==value)"   ✓" else "")){prefs.edit().putString("theme",value).apply();recreate()})
        }
        root.addView(text("Liquid Glass",18f))
        root.addView(text("Apple-inspired glass look can be selected above. Choose Dark or Light to turn it off.",13f))
        root.addView(text("Keyboard setup",18f))
        root.addView(button("Enable Luminate Keyboard"){startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))})
        root.addView(button("Select Luminate Keyboard"){(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()})
        root.addView(text("Tip: switch the keyboard off and on again after changing its appearance.",12f))
        setContentView(root)
    }
    private fun Int.dp()=(this*resources.displayMetrics.density).toInt()
}
