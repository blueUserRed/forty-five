package com.fourinachamber.fortyfive.screen.general.customActor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Event
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


@Suppress("LeakingThis")
open class CustomInputField(
    screen: OnjScreen,
    defText: String,
    fieldStyle: InputFieldStyle,
    override val partOfHierarchy: Boolean = false
) : CustomLabel(screen, defText, fieldStyle, partOfHierarchy) {
    //TODO ctrl+A
    //TODO ctrl+Z
    //TODO fix if leaved prob
    protected val BACKSPACE = '\b'
    protected val CARRIAGE_RETURN = '\r'
    protected val NEWLINE = '\n'
    protected val TAB = '\t'
    protected val DELETE = '\u007f'
    protected val BULLET = '\u0095'
    private val tmp1 = Vector2() //exists, to theoretically get the next field
    private val tmp2 = Vector2()
    private val tmp3 = Vector2()
    var keyRepeatInitialTime = 0.4f
    var keyRepeatTime = 0.05f
    protected var cursor = 0
    protected var selectionStart = 0
    protected var hasSelection = false
    protected var writeEnters = false
    private val glyphPositions = com.badlogic.gdx.utils.FloatArray()
    private var displayText: CharSequence? = null
    var clipboard: Clipboard? = null
    val inputListener: InputListener
    var listener: CustomInputFieldListener? = null
    var filter: InputFieldFilter? = null
    var focusTraversal = false // TODO add if "true" is set
    var onlyFontChars = true //TODO add if "false" is set
    var undoText: String
    var lastChangeTime: Long = 0
    private var passwordMode = false //TODO add if "true" is set
    private var fontOffset = 0f
    private var textOffset = 0f
    var renderOffset = 0f
    private var visibleTextStart = 0
    private var visibleTextEnd = 0
    private var maxLength = 0
    var focused = false
    var cursorOn = false
    private var blinkTime = 0.32f
    private val blinkTask: Timer.Task
    private var programmaticChangeEvents = false
    private val selectionRect = CustomRectangle(0F, 0F, 1F, 1F, fieldStyle.selectionColor)
    private val cursorRect = CustomRectangle(0F, 0F, 1F, 1F, fieldStyle.cursorColor)
    private val keyRepeatTask: KeyRepeatTask


    init {
        maxLength = 15
        blinkTask = object : Timer.Task() {
            override fun run() {
                if (stage == null) {
                    cancel()
                } else {
                    cursorOn = !cursorOn
                    Gdx.graphics.requestRendering()
                }
            }
        }
        clipboard = Gdx.app.clipboard
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

    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (batch == null) return
        validate()
        val pos = localToStageCoordinates(Vector2())
        val yPosCursor = pos.y + glyphLayout.height * 0.33F
        if (hasSelection) {
            selectionRect.setPosition(pos.x + glyphPositions.get(max(selectionStart, 0)), yPosCursor)
            selectionRect.setSize(
                glyphPositions.get(cursor) - glyphPositions.get(max(selectionStart, 0)),
                glyphLayout.height
            )
            selectionRect.draw(batch, parentAlpha)
        }
        super.draw(batch, parentAlpha)
        val cursorWidth = 0.5F
        if ((focused || hasKeyboardFocus()) && !isDisabled) {
            countFrames = if (lastCursorPosition == cursor) {
                countFrames++
                countFrames and 127
            } else {
                0
            }
            if (((countFrames shr 6)) == 0) {
                cursorRect.setPosition(pos.x + glyphPositions.get(cursor) - cursorWidth / 2, yPosCursor)
                cursorRect.setSize(cursorWidth, glyphLayout.height)
                cursorRect.draw(batch, parentAlpha)
            }
        }
        lastCursorPosition = cursor
    }

    fun clearSelection() {
        hasSelection = false
    }

    open fun setSelection(selectionStartInit: Int, selectionEndInit: Int) {
        var selectionStart = selectionStartInit
        var selectionEnd = selectionEndInit
        require(selectionStart >= 0) { "selectionStart must be >= 0" }
        require(selectionEnd >= 0) { "selectionEnd must be >= 0" }
        selectionStart = Math.min(this.text!!.length, selectionStart)
        selectionEnd = Math.min(this.text!!.length, selectionEnd)
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
        return Character.isLetterOrDigit(c)
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
        if (selectionStart > newDisplayText.length) {
            selectionStart = textLength
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
            } else if (--cursor <= limit) {
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
            clipboard!!.contents = text.substring(
                Math.min(cursor, selectionStart), Math.max(
                    cursor,
                    selectionStart
                )
            )
        }
    }

    open fun cut() {
        this.cut(this.programmaticChangeEvents)
    }

    open fun cut(fireChangeEvent: Boolean) {
        if (hasSelection && !passwordMode) {
            copy()
            cursor = delete(fireChangeEvent)
            updateDisplayText()
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
            updateDisplayText()
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
            field.cursorOn = field.focused
            field.blinkTask.cancel()
            if (field.focused) {
                Timer.schedule(field.blinkTask, field.blinkTime, field.blinkTime)
            }
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
                field.cursorOn = field.focused
                field.blinkTask.cancel()
                if (field.focused) {
                    Timer.schedule(field.blinkTask, field.blinkTime, field.blinkTime)
                }
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
                                field.paste(field.clipboard?.contents, true)
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
                    field.BACKSPACE, field.TAB, field.NEWLINE, field.CARRIAGE_RETURN -> {}
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
                        val enter = character == field.CARRIAGE_RETURN || character == field.NEWLINE
                        val delete = character == field.DELETE
                        val backspace = character == field.BACKSPACE
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
                                ) return true;
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
                    field.listener?.keyTyped(event, character)
                    true
                }
            }
        }
    }

    override fun setText(newText: CharSequence?) {
        if (newText == null) {
            text.clear()
            invalidate()
            return
        }
        if (text.toString() == newText) return
        super.setText(newText)
        invalidate()
    }


    override fun layout() {
        super.layout()
        updateDisplayText()
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

    interface InputFieldFilter {
        fun acceptChar(textField: CustomInputField?, ch: Char): Boolean
        class DigitsOnlyFilter : InputFieldFilter {
            override fun acceptChar(textField: CustomInputField?, ch: Char): Boolean {
                return Character.isDigit(ch)
            }
        }
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
        fun keyTyped(e: Event, ch: Char)
    }


    class InputFieldStyle() : LabelStyle() {
        var selectionColor: Color = Color(0F, 0F, 1F, 0.7F)
        var cursorColor: Color = Color.valueOf("2D2424ff")

        constructor(font: BitmapFont?, fontColor: Color?, selectionColor: Color?, cursorColor: Color?) : this() {
            this.font = font
            this.fontColor = fontColor
            if (selectionColor != null) this.selectionColor = selectionColor
            if (cursorColor != null) this.cursorColor = cursorColor
        }

        constructor(style: InputFieldStyle) : this() {
            font = style.font
            if (style.fontColor != null) {
                fontColor = Color(style.fontColor)
            }
            background = style.background
            selectionColor = style.selectionColor
            cursorColor = style.cursorColor
        }
    }
}