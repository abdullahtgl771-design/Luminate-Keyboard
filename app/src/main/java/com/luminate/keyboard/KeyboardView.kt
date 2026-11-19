package com.luminate.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

enum class KeyboardTheme { DARK, LIGHT, LIQUID }

class KeyboardView(context: Context, private val listener: Listener) : View(context) {
    interface Listener {
        fun onText(text: String)
        fun onDelete()
        fun onEnter()
        fun onClipboard()
        fun onSettings()
        fun onPicker()
        fun onShare()
        fun onSelectAll()
        fun onCut()
        fun onCopy()
        fun onPaste()
    }

    enum class Shift { OFF, ON, CAPS }
    private data class Slot(val key: Key, val rect: RectF, val row: Int)
    private val d = resources.displayMetrics.density
    private val toolbarH = 48f*d
    private var rowH = 52f*d
    private val gap = 4f*d
    private val pad = 4f*d
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign=Paint.Align.CENTER }
    private var theme = KeyboardTheme.DARK
    private var layer = Layer.LETTERS
    private var shift = Shift.OFF
    private var slots: List<Slot> = emptyList()
    private var pressed: Slot? = null
    private var toolbarPressed = -1
    private var toolPressed = -1
    private var toolbox = false
    private var editingTools = false
    private var oneHanded = false
    private var compact = false
    private var popupKey: String? = null
    private var popupRect = RectF()
    private var popupProgress = 0f
    private var popupAnimator: ValueAnimator? = null
    private var lastShiftTap = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val repeat = object: Runnable { override fun run(){ listener.onDelete(); handler.postDelayed(this,48) } }

    private data class Palette(val bg:Int,val key:Int,val special:Int,val action:Int,val ink:Int,val muted:Int,val stroke:Int)
    private fun palette(): Palette = when(theme) {
        KeyboardTheme.LIGHT -> Palette(Color.rgb(238,240,245),Color.WHITE,Color.rgb(218,222,231),Color.rgb(173,202,250),Color.rgb(30,34,42),Color.rgb(95,103,116),Color.rgb(255,255,255))
        KeyboardTheme.DARK -> Palette(Color.rgb(29,30,32),Color.rgb(55,56,59),Color.rgb(66,67,70),Color.rgb(169,199,255),Color.rgb(238,239,243),Color.rgb(174,177,184),Color.rgb(91,93,98))
        KeyboardTheme.LIQUID -> Palette(Color.rgb(24,27,35),Color.argb(145,105,119,145),Color.argb(165,129,145,173),Color.rgb(169,203,255),Color.rgb(249,250,255),Color.rgb(207,216,232),Color.argb(175,230,239,255))
    }

    init { isHapticFeedbackEnabled=true; setLayerType(View.LAYER_TYPE_SOFTWARE,null) }
    fun applyPreferences() {
        val prefs=context.getSharedPreferences("luminate_prefs",Context.MODE_PRIVATE)
        theme=runCatching { KeyboardTheme.valueOf(prefs.getString("theme","DARK") ?: "DARK") }.getOrDefault(KeyboardTheme.DARK)
        invalidate()
    }
    override fun onMeasure(w:Int,h:Int) {
        val rows=if(toolbox) 3 else Layouts.rows(layer).size
        val wanted=toolbarH+rows*rowH+pad*2
        setMeasuredDimension(MeasureSpec.getSize(w),resolveSize(wanted.toInt(),h))
    }
    override fun onSizeChanged(w:Int,h:Int,ow:Int,oh:Int){ buildSlots() }
    private fun buildSlots(){
        val out=ArrayList<Slot>()
        val rows=Layouts.rows(layer)
        val usable=((width-2*pad)*(if(oneHanded)0.78f else 1f)).coerceAtLeast(1f)
        val startX=if(oneHanded) width-usable-pad else pad
        rows.forEachIndexed { ri,row ->
            val total=row.sumOf{it.weight.toDouble()}.toFloat()
            val widthUnit=usable/total
            val top=toolbarH+pad+ri*rowH
            var x=startX
            row.forEach { key ->
                val kw=widthUnit*key.weight
                if(key.type!=KeyType.SPACER) out.add(Slot(key,RectF(x+gap/2,top+gap/2,x+kw-gap/2,top+rowH-gap/2),ri))
                x+=kw
            }
        }
        slots=out
        invalidate()
    }
    fun reset(){ layer=Layer.LETTERS;shift=Shift.OFF;toolbox=false;editingTools=false;buildSlots();requestLayout();applyPreferences() }
    fun setAutoShift(on:Boolean){ if(shift==Shift.CAPS)return; shift=if(on)Shift.ON else Shift.OFF;invalidate() }
    private fun label(k:Key)=when(k.type){
        KeyType.CHAR->if(layer==Layer.LETTERS&&shift!=Shift.OFF)k.label.uppercase() else k.label
        KeyType.SHIFT->if(shift==Shift.CAPS)"⬆" else "⇧"
        KeyType.DEL->"⌫";KeyType.ENTER->"➜";KeyType.MODE_SYM->"?123";KeyType.MODE_ABC->"ABC";KeyType.EMOJI->"☻";else->""
    }
    private fun rounded(c:Canvas,r:RectF,color:Int,rad:Float,stroke:Int?=null){
        p.color=color;p.style=Paint.Style.FILL
        if(theme==KeyboardTheme.LIQUID){p.shader=LinearGradient(r.left,r.top,r.right,r.bottom,Color.argb(190,255,255,255),color,Shader.TileMode.CLAMP)}
        c.drawRoundRect(r,rad,rad,p);p.shader=null
        if(theme==KeyboardTheme.LIQUID){p.color=stroke?:palette().stroke;p.style=Paint.Style.STROKE;p.strokeWidth=1f*d;c.drawRoundRect(r,rad,rad,p);p.style=Paint.Style.FILL
            p.color=Color.argb(48,255,255,255);c.drawRoundRect(RectF(r.left+2*d,r.top+1*d,r.right-2*d,r.top+min(9*d,r.height()/3)),rad,rad,p)}
    }
    private fun drawText(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int){
        tp.color=color;tp.textSize=size*d;tp.typeface=Typeface.create("sans-serif",Typeface.NORMAL)
        c.drawText(s,x,y-(tp.ascent()+tp.descent())/2,tp)
    }
    override fun onDraw(c:Canvas){
        val pal=palette();c.drawColor(pal.bg)
        if(theme==KeyboardTheme.LIQUID){p.shader=LinearGradient(0f,0f,width.toFloat(),height.toFloat(),Color.rgb(48,57,78),pal.bg,Shader.TileMode.CLAMP);c.drawRect(0f,0f,width.toFloat(),height.toFloat(),p);p.shader=null}
        drawToolbar(c)
        if(toolbox) drawToolbox(c) else {
            slots.forEach { s ->
                val k=s.key;val isDown=s===pressed
                val color=when{isDown->if(theme==KeyboardTheme.DARK)Color.rgb(85,88,95) else Color.rgb(220,230,246);k.type==KeyType.ENTER->pal.action;k.type.special->pal.special;else->pal.key}
                rounded(c,s.rect,color,if(k.type==KeyType.MODE_SYM||k.type==KeyType.ENTER)22*d else 8*d)
                val text=label(k)
                if(text.isNotEmpty()) drawText(c,text,s.rect.centerX(),s.rect.centerY(),when(k.type){KeyType.MODE_SYM,KeyType.MODE_ABC->14f;KeyType.SHIFT,KeyType.DEL,KeyType.ENTER->23f;else->23f}*1f,pal.ink)
                if(layer==Layer.LETTERS&&s.row==0&&k.type==KeyType.CHAR){
                    val n="qwertyuiop".indexOf(k.label.lowercase()).let{if(it<0)"" else if(it==9)"0" else (it+1).toString()}
                    tp.textAlign=Paint.Align.RIGHT;tp.textSize=10*d;tp.color=pal.muted;c.drawText(n,s.rect.right-5*d,s.rect.top+13*d,tp);tp.textAlign=Paint.Align.CENTER
                }
                if(k.type==KeyType.SPACE)drawText(c,"English",s.rect.centerX(),s.rect.centerY(),15f,pal.muted)
            }
        }
        if(popupKey!=null) drawPopup(c,pal)
    }
    private fun drawToolbar(c:Canvas){
        val pal=palette()
        val icons=listOf("▦","☻","▤","文","⚙","◉","●")
        val cell=width/7f
        icons.forEachIndexed{i,s->
            val cx=cell*(i+.5f)
            if(i==toolbarPressed)rounded(c,RectF(cx-20*d,5*d,cx+20*d,toolbarH-4*d),pal.special,22*d)
            drawText(c,s,cx,toolbarH/2,if(i==0)27f else 25f,if(i==6)pal.action else pal.ink)
        }
    }
    private fun drawToolbox(c:Canvas){
        val pal=palette();val names=if(editingTools) listOf("Select all","Cut","Copy","Paste","◀ Left","Right ▶") else listOf("GIF","Emoji","One-handed","Text editing","Share Luminate","Resize")
        val icons=if(editingTools) listOf("▣","✂","▢","▤","◀","▶") else listOf("GIF","☺","▣","↔","↗","⤢")
        val left=8*d;val colGap=8*d;val cellW=(width-2*left-colGap)/2f;val cellH=55*d
        names.forEachIndexed{i,name->
            val col=i%2;val row=i/2;val x=left+col*(cellW+colGap);val y=toolbarH+18*d+row*(cellH+10*d)
            val r=RectF(x,y,x+cellW,y+cellH)
            rounded(c,r,if(i==toolPressed)pal.action else pal.special,cellH/2)
            drawText(c,icons[i],x+31*d,y+cellH/2,if(icons[i].length>2)14f else 23f,pal.ink)
            tp.textAlign=Paint.Align.LEFT;tp.textSize=14*d;tp.color=pal.ink
            c.drawText(name,x+58*d,y+cellH/2-(tp.ascent()+tp.descent())/2,tp);tp.textAlign=Paint.Align.CENTER
        }
        if(editingTools)drawText(c,"‹ Back",width/2f,height-10*d,12f,pal.muted)
        else {drawText(c,"●   ●",width/2f,height-10*d,10f,pal.muted)}
    }
    private fun drawPopup(c:Canvas,pal:Palette){
        val progress=popupProgress
        val alpha=(if(progress<.22f)progress/.22f else (1f-progress)/.78f).coerceIn(0f,1f)
        val scale=.76f+.24f*(1f-kotlin.math.abs(.28f-progress))
        val r=RectF(popupRect);val cx=r.centerX();val bottom=r.top-3*d
        val pw=maxOf(42*d,r.width()*.76f);val ph=54*d
        val pop=RectF(cx-pw/2,bottom-ph,cx+pw/2,bottom)
        c.save();c.scale(scale,scale,cx,pop.centerY())
        p.alpha=(alpha*255).toInt();rounded(c,pop,if(theme==KeyboardTheme.DARK)Color.rgb(83,85,90) else Color.WHITE,12*d)
        drawText(c,popupKey?: "",cx,pop.centerY(),30f,pal.ink);p.alpha=255;c.restore()
    }
    private fun hit(x:Float,y:Float)=slots.firstOrNull{x>=it.rect.left-gap&&x<=it.rect.right+gap&&y>=it.rect.top-gap&&y<=it.rect.bottom+gap}
    private fun animatePopup(s:Slot){
        popupAnimator?.cancel();popupKey=label(s.key);popupRect=RectF(s.rect);popupProgress=0f
        popupAnimator=ValueAnimator.ofFloat(0f,1f).apply{duration=430;interpolator=DecelerateInterpolator();addUpdateListener{popupProgress=it.animatedValue as Float;invalidate()};start()}
    }
    private fun fire(k:Key){
        when(k.type){
            KeyType.CHAR->{val v=if(layer==Layer.LETTERS&&shift!=Shift.OFF)k.label.uppercase() else k.label;if(layer==Layer.LETTERS&&shift==Shift.ON)shift=Shift.OFF;listener.onText(v)}
            KeyType.SPACE->listener.onText(" ");KeyType.EMOJI->listener.onText("😊");KeyType.ENTER->listener.onEnter()
            KeyType.SHIFT->{val now=SystemClock.uptimeMillis();shift=when{shift==Shift.CAPS->Shift.OFF;shift==Shift.ON&&now-lastShiftTap<360->Shift.CAPS;shift==Shift.ON->Shift.OFF;else->Shift.ON};lastShiftTap=now}
            KeyType.MODE_SYM->{layer=Layer.SYMBOLS;buildSlots()};KeyType.MODE_ABC->{layer=Layer.LETTERS;buildSlots()};else->Unit
        };invalidate()
    }
    private fun toolAt(x:Float,y:Float):Int{
        val left=8*d;val gapX=8*d;val cw=(width-2*left-gapX)/2f;val ch=55*d
        val col=((x-left)/(cw+gapX)).toInt();val row=((y-toolbarH-18*d)/(ch+10*d)).toInt()
        return if(col in 0..1&&row in 0..2&&x>=left&&y>=toolbarH)row*2+col else -1
    }
    override fun onTouchEvent(e:MotionEvent):Boolean{
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{
                if(e.y<toolbarH){toolbarPressed=(e.x/(width/7f)).toInt().coerceIn(0,6);pressed=null}
                else if(toolbox){toolPressed=toolAt(e.x,e.y);pressed=null}
                else{pressed=hit(e.x,e.y);if(pressed!=null){performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);if(pressed!!.key.type==KeyType.DEL){listener.onDelete();handler.postDelayed(repeat,420)}else animatePopup(pressed!!)}}
                invalidate();return true
            }
            MotionEvent.ACTION_MOVE->{if(!toolbox&&toolbarPressed<0){val next=hit(e.x,e.y);if(next!==pressed){handler.removeCallbacks(repeat);pressed=next;if(next!=null)animatePopup(next);invalidate()}}else if(toolbox){toolPressed=toolAt(e.x,e.y);invalidate()};return true}
            MotionEvent.ACTION_UP->{
                handler.removeCallbacks(repeat);val t=toolbarPressed;val tool=toolPressed;val key=pressed?.key
                toolbarPressed=-1;toolPressed=-1;pressed=null;invalidate()
                if(t>=0&&e.y<toolbarH){when(t){0->{toolbox=!toolbox;editingTools=false;requestLayout()};1->listener.onText("😊");2->listener.onClipboard();3->listener.onText("বাংলা");4->listener.onSettings();5->listener.onSettings();6->listener.onPicker()}}
                else if(toolbox&&tool>=0)runTool(tool)
                else if(key!=null&&key.type!=KeyType.DEL)fire(key)
                return true
            }
            MotionEvent.ACTION_CANCEL->{handler.removeCallbacks(repeat);pressed=null;toolbarPressed=-1;toolPressed=-1;invalidate();return true}
        };return true
    }
    private fun runTool(i:Int){
        if(editingTools){when(i){0->listener.onSelectAll();1->listener.onCut();2->listener.onCopy();3->listener.onPaste();4->listener.onText("←");5->listener.onText("→")};return}
        when(i){0->listener.onText("GIF ");1->listener.onText("😊");2->{oneHanded=!oneHanded;compact=oneHanded;invalidate()};3->{editingTools=true;invalidate()};4->listener.onShare();5->{compact=!compact;rowH=if(compact)60f*d else 52f*d;buildSlots();requestLayout()}}
    }
    override fun onDetachedFromWindow(){handler.removeCallbacks(repeat);popupAnimator?.cancel();super.onDetachedFromWindow()}
}
