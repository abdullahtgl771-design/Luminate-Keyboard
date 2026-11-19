package com.luminate.keyboard

enum class Layer { LETTERS, SYMBOLS }

enum class KeyType(val special: Boolean) {
    CHAR(false), SPACE(false), SHIFT(true), DEL(true), ENTER(true),
    MODE_SYM(true), MODE_ABC(true), EMOJI(true), SPACER(false)
}

data class Key(val label: String, val type: KeyType = KeyType.CHAR, val weight: Float = 1f)

object Layouts {
    private fun chars(value: String) = value.map { Key(it.toString()) }
    private fun spacer(weight: Float) = Key("", KeyType.SPACER, weight)

    private val letterBottom = listOf(
        Key("?123", KeyType.MODE_SYM, 1.45f),
        Key(",", KeyType.CHAR, 0.85f),
        Key("", KeyType.EMOJI, 0.9f),
        Key("", KeyType.SPACE, 4.4f),
        Key(".", KeyType.CHAR, 0.85f),
        Key("", KeyType.ENTER, 1.55f)
    )

    private val symbolBottom = listOf(
        Key("ABC", KeyType.MODE_ABC, 1.45f),
        Key(",", KeyType.CHAR, 0.85f),
        Key("", KeyType.EMOJI, 0.9f),
        Key("", KeyType.SPACE, 4.4f),
        Key(".", KeyType.CHAR, 0.85f),
        Key("", KeyType.ENTER, 1.55f)
    )

    private val letters = listOf(
        chars("qwertyuiop"),
        listOf(spacer(0.48f)) + chars("asdfghjkl") + spacer(0.48f),
        listOf(Key("", KeyType.SHIFT, 1.45f)) + chars("zxcvbnm") + Key("", KeyType.DEL, 1.45f),
        letterBottom
    )

    private val symbols = listOf(
        chars("1234567890"),
        chars("@#\$%&-+()/"),
        listOf(Key("=", KeyType.CHAR), Key("*", KeyType.CHAR), Key("\"", KeyType.CHAR),
            Key("'", KeyType.CHAR), Key(":", KeyType.CHAR), Key(";", KeyType.CHAR),
            Key("!", KeyType.CHAR), Key("?", KeyType.CHAR), Key("", KeyType.DEL, 1.45f)),
        symbolBottom
    )

    fun rows(layer: Layer): List<List<Key>> =
        if (layer == Layer.LETTERS) letters else symbols
}
