package com.fourinachamber.fortyfive.screen.general.customActor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.UIUtils
import com.badlogic.gdx.utils.Clipboard
import com.badlogic.gdx.utils.Pools
import com.badlogic.gdx.utils.Timer
import com.fourinachamber.fortyfive.screen.general.CustomLabel
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.styles.WidthStyleProperty
import com.fourinachamber.fortyfive.screen.general.styles.addBackgroundStyles
import com.fourinachamber.fortyfive.screen.general.styles.addDisableStyles
import com.fourinachamber.fortyfive.screen.general.styles.addTextInputStyles
import com.fourinachamber.fortyfive.utils.MainThreadOnly
import com.fourinachamber.fortyfive.utils.substringTillEnd
import io.github.orioncraftmc.meditate.YogaValue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


@Suppress("LeakingThis")
/**
 * Represents a ONE LINE input from a user (not the best, but it's okay)
 */
open class CustomInputField(
    screen: OnjScreen,
    defText: String,
    val labelStyle: LabelStyle,
    override val partOfHierarchy: Boolean = false
) : CustomLabel(screen, defText, labelStyle, partOfHierarchy) {
    //TODO ctrl+A
    //TODO ctrl+Z
    //TODO fix if leaved / unfocused
    //TODO add on

    //    private val tmp1 = Vector2() //exists, to theoretically get the next field
//    private val tmp2 = Vector2()
//    private val tmp3 = Vector2()
    var keyRepeatInitialTime = 0.4f
    var keyRepeatTime = 0.05f
    protected var cursor = 0
    protected var selectionStart = 0
    protected var hasSelection = false
    protected var writeEnters = false
    private val glyphPositions = com.badlogic.gdx.utils.FloatArray()
    private var displayText: CharSequence? = null
    private val clipboard: Clipboard = Gdx.app.clipboard
    private val inputListener: InputListener
    var keyslistener: CustomInputFieldListener? = null
    var limitListener: CustomMaxReachedListener? = null
    var filter: InputFieldFilter? = null
    var focusTraversal = false // TODO add if "true" is set
    var onlyFontChars = true //TODO add if "false" is set
    private var undoText: String
    var lastChangeTime: Long = 0
    private var passwordMode = false //TODO add if "true" is set
    private var fontOffset = 0f
    private var textOffset = 0f
    var renderOffset = 0f
    private var visibleTextStart = 0
    private var visibleTextEnd = 0
    var maxLength = -1
        set(value) {
            field = value
            setText(text.toString().substringTillEnd(0, value))
        }
    private var focused = false
    val selectionRect = CustomRectangle(Color(0F, 0F, 1F, 0.7F))
    val cursorRect = CustomRectangle(Color.valueOf("2A2424"))
    private val keyRepeatTask: KeyRepeatTask
    private var lastCorrectText: String = text.toString()
    private var lastCorrectCursor: Int = cursor


    init {
        setText(text)
        setSize(prefWidth, prefHeight)
        inputListener = InputFieldClickListener(this)
        addListener(inputListener)
        keyRepeatTask = KeyRepeatTask(this)
        updateDisplayText()
        undoText = text.toString()
    }

    private var countFrames: Int = 0
    private var lastCursorPosition: Int = 0


    fun clearSelection() {
        hasSelection = false
    }

    open fun setSelection(selectionStartInit: Int, selectionEndInit: Int) {
        var selectionStart = selectionStartInit
        var selectionEnd = selectionEndInit
        require(selectionStart >= 0) { "selectionStart must be >= 0" }
        require(selectionEnd >= 0) { "selectionEnd must be >= 0" }
        selectionStart = this.text!!.length.coerceAtMost(selectionStart)
        selectionEnd = this.text!!.length.coerceAtMost(selectionEnd)
        if (selectionEnd == selectionStart) {
            clearSelection()
        } else {
            if (selectionEnd < selectionStart) {
                val temp = selectionEnd
                selectionEnd = selectionStart
                selectionStart = temp
            }
            hasSelection = true
            this.selectionStart = selectionStart
            cursor = selectionEnd
        }
    }

    protected open fun letterUnderCursor(xPos: Float): Int {
        var x = xPos
        x -= textOffset + fontOffset - this.style!!.font.data.cursorX - glyphPositions[visibleTextStart]
        val n = glyphPositions.size
        for (i in 1 until n) {
            if (glyphPositions[i] > x) {
                return if (glyphPositions[i] - x <= x - glyphPositions[i - 1]) {
                    i
                } else i - 1
            }
        }
        return n - 1
    }

    protected open fun isWordCharacter(c: Char): Boolean {
        return !Character.isWhitespace(c)
    }

    open fun selectAll() {
        setSelection(0, this.text!!.length)
    }

    open fun withinMaxLength(size: Int): Boolean {
        return maxLength <= 0 || size < maxLength
    }

    protected open fun wordUnderCursor(at: Int): IntArray {
        val text = this.text
        var right = text!!.length
        var left = 0
        var index = at
        if (at >= text.length) {
            left = text.length
            right = 0
            return intArrayOf(left, right)
        } else {
            while (true) {
                if (index < right) {
                    if (this.isWordCharacter(text[index])) {
                        ++index
                        continue
                    }
                    right = index
                }
                index = at - 1
                while (index > -1) {
                    if (!this.isWordCharacter(text[index])) {
                        left = index + 1
                        return intArrayOf(left, right)
                    }
                    --index
                }
                return intArrayOf(left, right)
            }
        }
    }

    private fun updateDisplayText() {
        val font = style.font
        val data = font.data
        val text: String = text.toString()
        val textLength = text.length
        val buffer = java.lang.StringBuilder()
        var i = 0
        while (i < textLength) {
            val ch = text[i].code
            buffer.append((if (data.hasGlyph(ch.toChar())) ch else ' '))
            i++
        }
        val newDisplayText = buffer.toString()
        displayText = newDisplayText
        glyphPositions.clear()
        var x = 0.0f
        if (this.glyphLayout.runs.size > 0) {
            val run = this.glyphLayout.runs.first() as GlyphRun
            val xAdvances: com.badlogic.gdx.utils.FloatArray = run.xAdvances
            fontOffset = xAdvances.first()
            i = 1
            val n = xAdvances.size
            while (i < n) {
                glyphPositions.add(x)
                x += xAdvances[i]
                ++i
            }
        } else {
            fontOffset = 0.0f
        }
        glyphPositions.add(x)
        visibleTextStart = visibleTextStart.coerceAtMost(glyphPositions.size - 1)
        visibleTextEnd = MathUtils.clamp(visibleTextEnd, visibleTextStart, glyphPositions.size - 1)
        if (selectionStart > newDisplayText.length) selectionStart = textLength
        if (text.length >= glyphPositions.size && !glyphLayout.runs.isEmpty) {
            setText(lastCorrectText)
            cursor = lastCorrectCursor
            limitListener?.maxReached(this, text)
        } else {
            lastCorrectText = text
            lastCorrectCursor = cursor
        }
    }

    open fun changeText(oldText: String, newText: String): Boolean {
        return if (newText == oldText) {
            false
        } else {
            setText(newText)
            val changeEvent = Pools.obtain(
                ChangeListener.ChangeEvent::class.java
            ) as ChangeListener.ChangeEvent
            val cancelled = fire(changeEvent)
            if (cancelled) {
                setText(oldText)
            }
            Pools.free(changeEvent)
            !cancelled
        }
    }

    protected open fun moveCursor(forward: Boolean, jump: Boolean) {
        val limit = if (forward) text.length else 0
        val charOffset = if (forward) 0 else -1
        do {
            if (forward) {
                if (++cursor >= limit) {
                    break
                }
            } else if (--cursor <= 0) {
                break
            }
        } while (jump && this.continueCursor(cursor, charOffset))
    }

    protected open fun continueCursor(index: Int, offset: Int): Boolean {
        val c = text[index + offset]
        return isWordCharacter(c)
    }

    open fun insert(position: Int, text: CharSequence, to: String): String? {
        return if (to.isEmpty()) text.toString() else to.substring(0, position) + text + to.substring(
            position,
            to.length
        )
    }


    open fun copy() {
        if (hasSelection && !passwordMode) {
            clipboard.contents = text.substring(
                cursor.coerceAtMost(selectionStart), cursor.coerceAtLeast(selectionStart)
            )
        }
    }

    open fun cut(fireChangeEvent: Boolean) {
        if (hasSelection && !passwordMode) {
            copy()
            cursor = delete(fireChangeEvent)
            invalidate()
//            updateDisplayText()
        }
    }

    open fun paste(cont: String?, fireChangeEvent: Boolean) {
        var content = cont
        if (content != null) {
            val buffer = StringBuilder()
            var textLength = text.length
            if (hasSelection) {
                textLength -= abs(cursor - selectionStart)
            }
            val data = style.font.data
            var i = 0
            val n = content.length
            while (i < n && withinMaxLength(textLength + buffer.length)) {
                val c = content[i]
                if (writeEnters && (c == '\n' || c == '\r') || c != '\r' && c != '\n' && (!onlyFontChars || data.hasGlyph(
                        c
                    )) && (filter == null || filter!!.acceptChar(this, c))
                ) {
                    buffer.append(c)
                }
                ++i
            }
            content = buffer.toString()
            if (hasSelection) {
                cursor = delete(fireChangeEvent)
            }
            if (fireChangeEvent) {
                changeText(text.toString(), insert(cursor, content, text.toString())!!)
            } else {
                setText(insert(cursor, content, text.toString()))
            }
            invalidate()
            cursor += content.length
        }
    }

    /*open fun next(up: Boolean) { //TODO focustraversable if needed
        val stage = stage
        if (stage != null) {
            var current: CustomInputField = this
            val currentCoords = parent.localToStageCoordinates(TextField.tmp2.set(x, y))
            val bestCoords = TextField.tmp1
            while (true) {
                var textField =
                    current.findNextTextField(stage.actors, null as TextField?, bestCoords, currentCoords, up)
                if (textField == null) {
                    if (up) {
                        currentCoords[-3.4028235E38f] = -3.4028235E38f
                    } else {
                        currentCoords[Float.MAX_VALUE] = Float.MAX_VALUE
                    }
                    textField =
                        current.findNextTextField(stage.actors, null as TextField?, bestCoords, currentCoords, up)
                }
                if (textField == null) {
                    Gdx.input.setOnscreenKeyboardVisible(false)
                    break
                }
                if (stage.setKeyboardFocus(textField)) {
                    textField.selectAll()
                    break
                }
                current = textField
                currentCoords.set(bestCoords)
            }
        }
    }*/


    open class InputFieldClickListener(protected val field: CustomInputField) : ClickListener() {
        override fun clicked(event: InputEvent, x: Float, y: Float) {
            if (field.isDisabled) {
                field.clearSelection()
                return
            }
            val count = tapCount % 4
            if (count == 0) {
                field.clearSelection()
            }
            if (count == 2) {
                val array: IntArray = field.wordUnderCursor(field.letterUnderCursor(x))
                field.setSelection(array[0], array[1])
            }
            if (count == 3) {
                field.selectAll()
            }
        }

        override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean {
            return if (!super.touchDown(event, x, y, pointer, button)) {
                false
            } else if (pointer == 0 && button != 0) {
                false
            } else if (field.isDisabled) {
                true
            } else {
                setCursorPosition(x, y)
                field.selectionStart = field.cursor
                val stage: Stage = field.stage
                stage.keyboardFocus = field
                field.hasSelection = true
                true
            }
        }

        override fun touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int) {
            super.touchDragged(event, x, y, pointer)
            setCursorPosition(x, y)
        }

        override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int) {
            if (field.selectionStart == field.cursor) {
                field.hasSelection = false
            }
            super.touchUp(event, x, y, pointer, button)
        }

        protected open fun setCursorPosition(x: Float, y: Float) {
            field.cursor = field.letterUnderCursor(x)
//            field.cursorOn = field.focused
//            field.blinkTask.cancel()
//            if (field.focused) {
//                Timer.schedule(field.blinkTask, field.blinkTime, field.blinkTime)
//            }
        }

        protected open fun goHome(jump: Boolean) {
            field.cursor = 0
        }

        protected open fun goEnd(jump: Boolean) {
            field.cursor = field.text.length
        }

        override fun keyDown(event: InputEvent?, keycode: Int): Boolean {
            return if (field.isDisabled) {
                false
            } else {
//                field.cursorOn = field.focused
//                field.blinkTask.cancel()
//                if (field.focused) {
//                    Timer.schedule(field.blinkTask, field.blinkTime, field.blinkTime)
//                }
                if (!field.hasKeyboardFocus()) {
                    false
                } else {
                    var repeat = false
                    val ctrl = UIUtils.ctrl()
                    val jump = ctrl && !field.passwordMode
                    var handled = true
                    if (ctrl) {
                        when (keycode) {
                            29 -> {
                                field.selectAll()
                                return true
                            }

                            31, 124 -> {
                                field.copy()
                                return true
                            }

                            50 -> {
                                field.paste(field.clipboard.contents, true)
                                repeat = true
                            }

                            52 -> {
                                field.cut(true)
                                return true
                            }

                            54 -> {
                                val oldText: String = field.text.toString()
                                field.setText(field.undoText)
                                field.undoText = oldText
                                return true
                            }

                            else -> handled = false
                        }
                    }
                    if (UIUtils.shift()) {
                        run {
//                            when (keycode) { //idk what the usecase should be
//                                112 -> field.cut(true)
//                                124 -> field.paste(field.clipboard.getContents(), true)
//                            }
                            val temp: Int = field.cursor
                            when (keycode) {
                                3 -> {
                                    goHome(jump)
                                    handled = true
                                }

                                21 -> {
                                    field.moveCursor(false, jump)
                                    repeat = true
                                    handled = true
                                }

                                22 -> {
                                    field.moveCursor(true, jump)
                                    repeat = true
                                    handled = true
                                }

                                123 -> {
                                    goEnd(jump)
                                    handled = true
                                }

                                else -> return@run
                            }
                            if (!field.hasSelection) {
                                field.selectionStart = temp
                                field.hasSelection = true
                            }
                        }
                    } else {
                        when (keycode) {
                            3 -> {
                                goHome(jump)
                                field.clearSelection()
                                handled = true
                            }

                            21 -> {
                                if (field.hasSelection) field.cursor = min(field.cursor, field.selectionStart)
                                else field.moveCursor(false, jump)
                                field.clearSelection()
                                repeat = true
                                handled = true
                            }

                            22 -> {
                                if (field.hasSelection) field.cursor = max(field.cursor, field.selectionStart)
                                else field.moveCursor(true, jump)
                                field.clearSelection()
                                repeat = true
                                handled = true
                            }

                            123 -> {
                                goEnd(jump)
                                field.clearSelection()
                                handled = true
                            }
                        }
                    }
                    field.cursor = MathUtils.clamp(field.cursor, 0, field.text.length)
                    if (repeat) {
                        scheduleKeyRepeatTask(keycode)
                    }
                    handled
                }
            }
        }

        private fun scheduleKeyRepeatTask(keycode: Int) {
            if (!field.keyRepeatTask.isScheduled || field.keyRepeatTask.keycode != keycode) {
                field.keyRepeatTask.keycode = keycode
                field.keyRepeatTask.cancel()
                Timer.schedule(
                    field.keyRepeatTask,
                    field.keyRepeatInitialTime,
                    if (field.hasSelection) field.keyRepeatTime / 3 else field.keyRepeatTime
                )
            }
        }

        override fun keyUp(event: InputEvent, keycode: Int): Boolean {
            return if (field.isDisabled) {
                false
            } else {
                field.keyRepeatTask.cancel()
                true
            }
        }

        protected open fun checkFocusTraversal(character: Char): Boolean {
            return field.focusTraversal && (character == '\t' || (character == '\r' || character == '\n') && (UIUtils.isAndroid || UIUtils.isIos))
        }

        override fun keyTyped(event: InputEvent, character: Char): Boolean {
            return if (field.isDisabled) {
                false
            } else {
                when (character) {
                    BACKSPACE, TAB, NEWLINE, CARRIAGE_RETURN -> {}
                    '\u000b', '\u000C' -> return false

                    else -> if (character < ' ') {
                        return false
                    }
                }
                if (!field.hasKeyboardFocus()) {
                    false
                } else if (UIUtils.isMac && Gdx.input.isKeyPressed(63)) {
                    true
                } else {
                    if (checkFocusTraversal(character)) {
//                        field.next(UIUtils.shift())
                        println("NOW TRAVERSE (NOT IMPLEMENTED)")
                    } else {
                        val enter = character == CARRIAGE_RETURN || character == NEWLINE
                        val delete = character == DELETE
                        val backspace = character == BACKSPACE
                        val add =
                            if (enter) field.writeEnters else !field.onlyFontChars || field.style.font.data
                                .hasGlyph(character)
                        val remove = backspace || delete
                        if (add || remove) {
                            val oldText: String = field.text.toString()
                            val oldCursor: Int = field.cursor
                            if (remove) {
                                if (field.hasSelection) {
                                    field.cursor = field.delete(false)
                                } else {
                                    if (backspace && field.cursor > 0) {

                                        field.setText(
                                            field.text.substring(
                                                0,
                                                field.cursor - 1
                                            ) + field.text.substring(field.cursor--)
                                        )
                                        field.renderOffset = 0.0f
                                    }
                                    if (delete && field.cursor < field.text.length) {
                                        field.setText(
                                            field.text.substring(
                                                0,
                                                field.cursor
                                            ) + field.text.substring(field.cursor + 1)
                                        )
                                    }
                                }
                            }
                            val insertion: String
                            if (add && !remove) {
                                if (!enter && field.filter != null && !field.filter!!.acceptChar(
                                        field,
                                        character
                                    )
                                ) return true
                                if (!field.withinMaxLength(
                                        field.text.length - if (field.hasSelection) abs(
                                            field.cursor - field.selectionStart
                                        ) else 0
                                    )
                                ) {
                                    return true
                                }
                                if (field.hasSelection) field.cursor = field.delete(false)
                                insertion = if (enter) "\n" else character.toString()
                                val newText = field.insert(field.cursor++, insertion, field.text.toString())
                                field.setText(newText)
                            }
                            if (field.changeText(oldText, field.text.toString())) {
                                val time = System.currentTimeMillis()
                                if (time - 750L > field.lastChangeTime) {
                                    field.undoText = oldText
                                }
                                field.lastChangeTime = time
                            } else {
                                field.cursor = oldCursor
                            }
                        }
                    }
                    field.keyslistener?.keyTyped(event, character)
                    true
                }
            }
        }
    }

    override fun setText(newText: CharSequence?) {
        if (newText == null) {
            text.clear()
            super.setText(null)
            invalidateHierarchy()
            return
        }
        if (text.toString() == newText) return
        super.setText(newText)
        if (cursor > newText.length) cursor = newText.length
    }


    override fun layout() {
        if (styleManager!!.styleProperties.find { it is WidthStyleProperty }!!
                .get(styleManager!!.node) != YogaValue.parse("auto")
        ) {
            wrap = true
        }
        super.layout()
        updateDisplayText()
        if (glyphLayout.runs.size > 1) { //has multiple lines /wrap //kinda trash solution, but IDK any better, I tried for a bit but nothing worked
            super.layout()
            updateDisplayText()
        }
        if (prefHeight < height) {
            val dif = height - prefHeight.toInt()
            height = prefHeight.toInt().toFloat()
            y += dif / 2
        }
    }

    @MainThreadOnly
    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (batch == null) return
        validate()
        drawBackground(batch)
        val pos = localToStageCoordinates(Vector2())
        val ySize = glyphLayout.height * 1.2F
        val yPosCursor = pos.y + (height - ySize) / 2
        if (hasSelection) {
            selectionRect.setPosition(pos.x + glyphPositions.get(max(selectionStart, 0)), yPosCursor)
            selectionRect.setSize(
                glyphPositions.get(cursor) - glyphPositions.get(max(selectionStart, 0)),
                ySize
            )
            selectionRect.draw(batch, parentAlpha)
        }
        //selection-rect has to be drawn after background, that's why its like that
        val background = backgroundHandle
        backgroundHandle = null
        super.draw(batch, parentAlpha)
        backgroundHandle = background
        val cursorWidth = 3F * fontScaleX
        if ((focused || hasKeyboardFocus()) && !isDisabled) {
            countFrames = if (lastCursorPosition == cursor) {
                countFrames++
                countFrames and 127
            } else {
                0
            }
            if (((countFrames shr 6)) == 0) {
                cursorRect.setPosition(pos.x + glyphPositions.get(cursor) - cursorWidth / 2, yPosCursor)
                cursorRect.setSize(cursorWidth, ySize)
                cursorRect.draw(batch, parentAlpha)
            }
        }
        lastCursorPosition = cursor
    }

    open fun delete(fireChangeEvent: Boolean): Int {
        val from = selectionStart
        val to = cursor
        val minIndex = from.coerceAtMost(to)
        val maxIndex = from.coerceAtLeast(to)
        val newText =
            (if (minIndex > 0) text.substring(0, minIndex) else "") + if (maxIndex < text.length) text.substring(
                maxIndex,
                text.length
            ) else ""
        if (fireChangeEvent) {
            this.changeText(text.toString(), newText)
        } else {
            setText(newText)
        }
        clearSelection()
        return minIndex
    }

    interface InputFieldFilter {//isn't really well working
        fun acceptChar(textField: CustomInputField?, ch: Char): Boolean
//        class DigitsOnlyFilter : InputFieldFilter {
//            override fun acceptChar(textField: CustomInputField?, ch: Char): Boolean {
//                return Character.isDigit(ch)
//            }
//        }
    }

    class KeyRepeatTask(val field: CustomInputField) : Timer.Task() {
        var keycode = 0
        override fun run() {
            if (field.stage == null) {
                cancel()
            } else {
                field.inputListener.keyDown(null as InputEvent?, keycode)
            }
        }
    }

    interface CustomInputFieldListener {
        fun keyTyped(e: InputEvent, ch: Char)
    }

    interface CustomMaxReachedListener {
        fun maxReached(field: CustomInputField, wrong: String)
    }

    override fun initStyles(screen: OnjScreen) {
        addTextInputStyles(screen)
        addBackgroundStyles(screen)
        addDisableStyles(screen)
    }


    companion object {
        const val BACKSPACE = '\b'
        const val CARRIAGE_RETURN = '\r'
        const val NEWLINE = '\n'
        const val TAB = '\t'
        const val DELETE = '\u007f'
//        const val BULLET = '\u0095' //Maybe needed later or so
    }
}