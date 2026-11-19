package com.luminate.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator

class KeyboardView(context: Context, private val listener: Listener) : View(context), SharedPreferences.OnSharedPreferenceChangeListener {
    interface Listener {
        fun onText(text: String)
        fun onDelete()
        fun onEnter()
        fun onClipboard()
        fun onSettings()
        fun onEmoji()
        fun onShare()
        fun onTextEdit(action: Int)
        fun onTranslate()
        fun onVoice()
        fun onGif()
    }
    private enum class Shift { OFF, ON, CAPS }
    private data class Slot(val key: Key, val rect: RectF)
    private val prefs=context.getSharedPreferences("keyboard_prefs",Context.MODE_PRIVATE)
    private val d=resources.displayMetrics.density
    private val toolbarH=46*d
    private var rowH=54*d
    private val gap=4*d
    private val pad=4*d
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val text=Paint(Paint.ANTI_ALIAS_FLAG).apply{textAlign=Paint.Align.CENTER}
    private var theme=prefs.getString("theme_mode","DARK")?:"DARK"
    private var glass=prefs.getBoolean("liquid_glass",false)
    private var bg=Color.DKGRAY; private var keyColor=Color.GRAY; private var special=Color.GRAY
    private var pressedColor=Color.GRAY; private var accent=Color.BLUE; private var ink=Color.WHITE
    private var layer=Layer.LETTERS; private var shift=Shift.OFF
    private var toolbox=false; private var oneHanded=false; private var resized=false
    private var slots= emptyList<Slot>(); private var toolbar= emptyList<Slot>(); private var tools= emptyList<Slot>()
    private var pressed:Slot?=null; private var lastShift=0L
    private var popup:Slot?=null; private var popupLabel=""; private var popupScale=0f
    private var animator:ValueAnimator?=null
    private val handler=Handler(Looper.getMainLooper())
    private val repeat=object:Runnable{override fun run(){listener.onDelete();handler.postDelayed(this,45)}}
    init{prefs.registerOnSharedPreferenceChangeListener(this);updateColors()}
    override fun onSharedPreferenceChanged(p:SharedPreferences?,k:String?){theme=prefs.getString("theme_mode","DARK")?:"DARK";glass=prefs.getBoolean("liquid_glass",false);updateColors();invalidate()}
    private fun updateColors(){
        val light=theme=="LIGHT"
        bg=if(light)Color.rgb(238,240,245) else Color.rgb(29,30,32)
        keyColor=if(light)Color.WHITE else Color.rgb(55,56,59)
        special=if(light)Color.rgb(218,222,231) else Color.rgb(66,67,70)
        pressedColor=if(light)Color.rgb(220,230,246) else Color.rgb(85,88,95)
        accent=if(glass)Color.rgb(0,122,255) else if(light)Color.rgb(173,202,250) else Color.rgb(169,199,255)
        ink=if(light)Color.rgb(30,34,42) else Color.rgb(238,239,243)
    }
    override fun onMeasure(w:Int,h:Int){val body=if(toolbox)3*rowH*1.5f else 4*rowH;setMeasuredDimension(MeasureSpec.getSize(w),(toolbarH+body+2*pad).toInt())}
    override fun onSizeChanged(w:Int,h:Int,ow:Int,oh:Int){buildSlots()}
    private fun buildSlots(){
        val usable=(width-2*pad)*(if (oneHanded) 0.78f else 1f)
        val start=if(oneHanded)width-pad-usable else pad
        toolbar=listOf(KeyType.GRID_4,KeyType.STICKER,KeyType.CLIPBOARD,KeyType.TRANSLATE,KeyType.SETTINGS,KeyType.THEME,KeyType.VOICE).mapIndexed{i,t->
            val u=width/7f;Slot(Key("",t),RectF(i*u+gap/2,gap/2,(i+1)*u-gap/2,toolbarH-gap/2))
        }
        val out=ArrayList<Slot>();val top=toolbarH+pad
        Layouts.rows(layer).forEachIndexed{r,row->
            val total=row.sumOf{it.weight.toDouble()}.toFloat();val unit=usable/total;var x=start
            row.forEach{k->val kw=unit*k.weight;if(k.type!=KeyType.SPACER)out.add(Slot(k,RectF(x+gap/2,top+r*rowH+gap/2,x+kw-gap/2,top+(r+1)*rowH-gap/2)));x+=kw}
        };slots=out
        val names=listOf("GIF","Emoji","One-handed","Text editing","Share","Resize")
        val types=listOf(KeyType.TOOL_GIF,KeyType.TOOL_EMOJI,KeyType.TOOL_ONE_HANDED,KeyType.TOOL_TEXT_EDIT,KeyType.TOOL_SHARE,KeyType.TOOL_RESIZE)
        val cw=usable/2;val rh=rowH*1.5f
        tools=types.mapIndexed{i,t->val c=i%2;val r=i/2;Slot(Key(names[i],t),RectF(start+c*cw+gap,top+r*rh+gap,start+(c+1)*cw-gap,top+(r+1)*rh-gap))}
    }
    fun reset(){toolbox=false;layer=Layer.LETTERS;shift=Shift.OFF;buildSlots();requestLayout();invalidate()}
    fun setAutoShift(on:Boolean){if(shift==Shift.CAPS)return;shift=if(on)Shift.ON else Shift.OFF;invalidate()}
    private fun label(k:Key):String?=when(k.type){
        KeyType.CHAR->if(layer==Layer.LETTERS&&shift!=Shift.OFF)k.label.uppercase() else k.label
        KeyType.SHIFT->"⇧";KeyType.DEL->"⌫";KeyType.ENTER->"➜";KeyType.MODE_SYM->"?123";KeyType.MODE_ABC->"ABC";else->null
    }
    private fun rounded(c:Canvas,r:RectF,color:Int,rad:Float){
        paint.color=color
        if(glass){paint.setShadowLayer(7*d,0f,2*d,Color.argb(55,0,0,0));c.drawRoundRect(r,rad,rad,paint);paint.clearShadowLayer();paint.style=Paint.Style.STROKE;paint.strokeWidth=d;paint.color=Color.argb(95,255,255,255);c.drawRoundRect(r,rad,rad,paint);paint.style=Paint.Style.FILL}
        else c.drawRoundRect(r,rad,rad,paint)
    }
    private fun drawLabel(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int=ink){
        text.color=color;text.textSize=size*d
        c.drawText(s,x,y-(text.ascent()+text.descent())/2,text)
    }
    override fun onDraw(c:Canvas){
        c.drawColor(bg);toolbar.forEachIndexed{i,s->
            if(i==0&&toolbox)rounded(c,s.rect,accent,20*d)
            val glyph=listOf("▦","☻","▤","文","⚙","◉","●")[i]
            drawLabel(c,glyph,s.rect.centerX(),s.rect.centerY(),if(i==0)25f else 23f,if(i==0&&toolbox)Color.WHITE else ink)
        }
        if(toolbox){tools.forEach{s->rounded(c,s.rect,special,s.rect.height()/2);drawLabel(c,s.key.label,s.rect.centerX(),s.rect.centerY(),15f)}}
        else slots.forEach{s->
            val k=s.key;val color=when{pressed===s->pressedColor;k.type==KeyType.ENTER->accent;k.type.special->special;else->keyColor}
            rounded(c,s.rect,color,if(k.type==KeyType.ENTER||k.type==KeyType.MODE_SYM)22*d else 8*d)
            label(k)?.let{drawLabel(c,it,s.rect.centerX(),s.rect.centerY(),if(k.type==KeyType.MODE_SYM)14f else 23f,if(k.type==KeyType.ENTER)Color.WHITE else ink)}
            if(layer==Layer.LETTERS&&s.key.type==KeyType.CHAR&&s.rect.top<toolbarH+rowH){val n="qwertyuiop".indexOf(k.label).let{if(it<0)"" else if(it==9)"0" else (it+1).toString()};text.textSize=10*d;text.color=ink;text.textAlign=Paint.Align.RIGHT;c.drawText(n,s.rect.right-5*d,s.rect.top+13*d,text);text.textAlign=Paint.Align.CENTER}
        }
        drawPopup(c)
    }
    private fun drawPopup(c:Canvas){
        val s=popup?:return;if(popupScale<=0f)return
        val w=s.rect.width()*1.4f;val h=s.rect.height()*1.5f;val x=s.rect.centerX();val y=s.rect.top-h/2+6*d
        c.save();c.scale(popupScale,popupScale,x,s.rect.bottom)
        val r=RectF(x-w/2,y-h/2,x+w/2,y+h/2);rounded(c,r,if(theme=="LIGHT")Color.WHITE else Color.rgb(78,80,84),10*d)
        drawLabel(c,popupLabel,x,y,28f);c.restore()
    }
    private fun showPopup(s:Slot){popup=s;popupLabel=label(s.key)?:s.key.label;animator?.cancel();animator=ValueAnimator.ofFloat(0f,1f).apply{duration=145;interpolator=OvershootInterpolator(1.1f);addUpdateListener{popupScale=it.animatedValue as Float;invalidate()};start()}}
    private fun hidePopup(){animator?.cancel();animator=ValueAnimator.ofFloat(popupScale,0f).apply{duration=95;addUpdateListener{popupScale=it.animatedValue as Float;if(popupScale<=0f)popup=null;invalidate()};start()}}
    private fun hit(x:Float,y:Float):Slot?=toolbar.firstOrNull{it.rect.contains(x,y)}?:if(toolbox)tools.firstOrNull{it.rect.contains(x,y)} else slots.firstOrNull{x>=it.rect.left-gap/2&&x<=it.rect.right+gap/2&&y>=it.rect.top-gap/2&&y<=it.rect.bottom+gap/2}
    override fun onTouchEvent(e:MotionEvent):Boolean{
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{pressed=hit(e.x,e.y);pressed?.let{performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);if(it.key.type==KeyType.CHAR)showPopup(it);if(it.key.type==KeyType.DEL){listener.onDelete();handler.postDelayed(repeat,400)}};invalidate()}
            MotionEvent.ACTION_MOVE->{val next=hit(e.x,e.y);if(next!==pressed){handler.removeCallbacks(repeat);pressed=next;if(next?.key?.type==KeyType.CHAR)showPopup(next)else hidePopup();invalidate()}}
            MotionEvent.ACTION_UP->{handler.removeCallbacks(repeat);hidePopup();val item=pressed;pressed=null;invalidate();if(item!=null&&item.key.type!=KeyType.DEL)fire(item.key)}
            MotionEvent.ACTION_CANCEL->{handler.removeCallbacks(repeat);hidePopup();pressed=null;invalidate()}
        };return true
    }
    private fun fire(k:Key){
        when(k.type){
            KeyType.GRID_4->{toolbox=!toolbox;requestLayout();invalidate()}
            KeyType.STICKER,KeyType.TOOL_EMOJI->listener.onEmoji()
            KeyType.CLIPBOARD->listener.onClipboard()
            KeyType.TRANSLATE->listener.onTranslate()
            KeyType.SETTINGS,KeyType.THEME->listener.onSettings()
            KeyType.VOICE->listener.onVoice()
            KeyType.TOOL_GIF->listener.onGif()
            KeyType.TOOL_ONE_HANDED->{oneHanded=!oneHanded;buildSlots();invalidate()}
            KeyType.TOOL_TEXT_EDIT->listener.onTextEdit(android.R.id.selectAll)
            KeyType.TOOL_SHARE->listener.onShare()
            KeyType.TOOL_RESIZE->{resized=!resized;rowH=if(resized)62*d else 54*d;buildSlots();requestLayout();invalidate()}
            KeyType.CHAR->{val value=if(layer==Layer.LETTERS&&shift!=Shift.OFF)k.label.uppercase() else k.label;if(shift==Shift.ON)shift=Shift.OFF;listener.onText(value);invalidate()}
            KeyType.SPACE->listener.onText(" ")
            KeyType.ENTER->listener.onEnter()
            KeyType.SHIFT->{val now=SystemClock.uptimeMillis();shift=when{shift==Shift.CAPS->Shift.OFF;shift==Shift.ON&&now-lastShift<350->Shift.CAPS;shift==Shift.ON->Shift.OFF;else->Shift.ON};lastShift=now;invalidate()}
            KeyType.MODE_SYM->{layer=Layer.SYMBOLS;buildSlots();invalidate()}
            KeyType.MODE_ABC->{layer=Layer.LETTERS;buildSlots();invalidate()}
            else->Unit
        }
    }
    override fun onDetachedFromWindow(){prefs.unregisterOnSharedPreferenceChangeListener(this);handler.removeCallbacks(repeat);animator?.cancel();super.onDetachedFromWindow()}
}
