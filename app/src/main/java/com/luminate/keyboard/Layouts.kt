package com.luminate.keyboard

enum class Layer { LETTERS, SYMBOLS }
enum class KeyType(val special:Boolean) {
    CHAR(false), SPACE(false), SHIFT(true), DEL(true), ENTER(true), MODE_SYM(true), MODE_ABC(true), SPACER(false),
    GRID_4(true), STICKER(true), CLIPBOARD(true), TRANSLATE(true), SETTINGS(true), THEME(true), VOICE(true),
    TOOL_GIF(false), TOOL_EMOJI(false), TOOL_ONE_HANDED(false), TOOL_TEXT_EDIT(false), TOOL_SHARE(false), TOOL_RESIZE(false)
}
data class Key(val label:String,val type:KeyType=KeyType.CHAR,val weight:Float=1f)
object Layouts {
    private fun chars(s:String)=s.map{Key(it.toString())}
    private fun spacer(w:Float)=Key("",KeyType.SPACER,w)
    private val bottom=listOf(Key("",KeyType.MODE_SYM,1.5f),Key(","),Key(" ",KeyType.SPACE,5f),Key("."),Key("",KeyType.ENTER,1.5f))
    private val bottomSymbols=listOf(Key("",KeyType.MODE_ABC,1.5f),Key(","),Key(" ",KeyType.SPACE,5f),Key("."),Key("",KeyType.ENTER,1.5f))
    private val letters=listOf(chars("qwertyuiop"),listOf(spacer(.5f))+chars("asdfghjkl")+spacer(.5f),listOf(Key("",KeyType.SHIFT,1.5f))+chars("zxcvbnm")+Key("",KeyType.DEL,1.5f),bottom)
    private val symbols=listOf(chars("1234567890"),chars("@#\$%&-+()/"),listOf(spacer(1.5f))+chars("*\"':;!?")+Key("",KeyType.DEL,1.5f),bottomSymbols)
    fun rows(layer:Layer):List<List<Key>> = if(layer==Layer.LETTERS)letters else symbols
}
