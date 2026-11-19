package com.luminate.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView

class SettingsActivity:Activity(){
    private lateinit var prefs:SharedPreferences
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState);prefs=getSharedPreferences("keyboard_prefs",Context.MODE_PRIVATE)
        val d=resources.displayMetrics.density;val pad=(20*d).toInt()
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(pad,pad*2,pad,pad);setBackgroundColor(Color.rgb(18,18,18))}
        fun subtitle(value:String)=TextView(this).apply{text=value;textSize=16f;setTextColor(Color.LTGRAY);setPadding(0,(12*d).toInt(),0,(6*d).toInt())}
        fun button(value:String,action:()->Unit)=Button(this).apply{text=value;isAllCaps=false;setTextColor(Color.WHITE);background=GradientDrawable().apply{setColor(Color.rgb(44,44,46));cornerRadius=12*d};setOnClickListener{action()}}
        root.addView(TextView(this).apply{text="Luminate Keyboard";textSize=25f;setTextColor(Color.WHITE);setPadding(0,0,0,(14*d).toInt())})
        root.addView(subtitle("Keyboard theme"))
        val spinner=Spinner(this).apply{
            adapter=ArrayAdapter(this@SettingsActivity,android.R.layout.simple_spinner_dropdown_item,arrayOf("Dark theme","Light theme"))
            setSelection(if(prefs.getString("theme_mode","DARK")=="LIGHT")1 else 0)
            onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
                override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long){prefs.edit().putString("theme_mode",if(position==1)"LIGHT" else "DARK").apply()}
                override fun onNothingSelected(parent:AdapterView<*>?){}
            }
        };root.addView(spinner)
        root.addView(subtitle("Apple Liquid Glass"))
        root.addView(Switch(this).apply{text="Enable Liquid Glass effect";setTextColor(Color.WHITE);isChecked=prefs.getBoolean("liquid_glass",false);setOnCheckedChangeListener{_,checked->prefs.edit().putBoolean("liquid_glass",checked).apply()}})
        root.addView(subtitle("Keyboard activation"))
        root.addView(button("Enable Luminate Keyboard"){startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))})
        root.addView(button("Select Luminate Keyboard"){(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()})
        root.addView(subtitle("Test keyboard"))
        root.addView(EditText(this).apply{hint="Type here to test…";setHintTextColor(Color.GRAY);setTextColor(Color.WHITE);background=GradientDrawable().apply{setColor(Color.rgb(28,28,30));cornerRadius=8*d};setPadding((12*d).toInt(),(12*d).toInt(),(12*d).toInt(),(12*d).toInt())})
        setContentView(root)
    }
}
