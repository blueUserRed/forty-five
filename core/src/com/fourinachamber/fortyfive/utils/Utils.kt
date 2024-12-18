package com.fourinachamber.fortyfive.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.graphics.Cursor.SystemCursor
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.badlogic.gdx.utils.ObjectMap
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.game.GameAnimation
import com.fourinachamber.fortyfive.game.controller.GameController
import com.fourinachamber.fortyfive.onjNamespaces.OnjYogaValue
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.CenteredDragSource
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import io.github.orioncraftmc.meditate.YogaValue
import io.github.orioncraftmc.meditate.enums.YogaUnit
import onj.value.OnjArray
import onj.value.OnjString
import java.util.concurrent.atomic.AtomicInteger
import kotlin.experimental.ExperimentalTypeInference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * represents a value that can be of type [T] or of type [U]. Check which type it is using `is Either.Left` or
 * `is Either.Right`. Can be created using the constructor of the subclasses or using the [eitherLeft] or [eitherRight]
 * extension functions.
 */
sealed class Either<out T, out U> {

    class Left<out T>(val value: T) : Either<T, Nothing>()
    class Right<out U>(val value: U) : Either<Nothing, U>()

}

/**
 * redirects to [Payload.object] because `object` is a keyword in kotlin
 */
var Payload.obj: Any?
    get() = this.`object`
    set(value) {
        this.`object` = value
    }

/**
 * @see Either
 */
fun <T> T.eitherLeft(): Either<T, Nothing> = Either.Left(this)

/**
 * @see Either
 */
fun <T> T.eitherRight(): Either<Nothing, T> = Either.Right(this)

@OptIn(ExperimentalTypeInference::class)
fun <T> repeatingSequenceOf(@BuilderInference value: () -> T): Sequence<T> = sequence { while (true) yield(value()) }

/**
 * returns the x and y components of a vector3 as a Vector2
 */
val Vector3.xy: Vector2
    get() = Vector2(x, y)

val Vector2.unit: Vector2
    get() {
        val len = this.len()
        if (len == 0f) return Vector2(0f, 0f)
        return this / len
    }

val Vector2.normal: Vector2
    get() {
        return this.cpy().rotate90(0)
    }

operator fun Vector2.minus(other: Vector2) = Vector2(x - other.x, y - other.y)
operator fun Vector2.plus(other: Vector2) = Vector2(x + other.x, y + other.y)
operator fun Vector2.times(other: Float): Vector2 = Vector2(this.x * other, this.y * other)
operator fun Vector2.div(other: Float): Vector2 = Vector2(this.x / other, this.y / other)
infix fun Vector2.dot(other: Vector2) = this.dot(other)
infix fun Vector2.cross(other: Vector2): Float = this.crs(other)
operator fun Vector2.unaryMinus(): Vector2 = Vector2(-this.x, -this.y)
infix fun Vector2.multIndividual(other: Vector2) = Vector2(x * other.x, y * other.y)
fun Vector2.withMag(mag: Float): Vector2 = this.unit * mag
fun Vector2.compare(other: Vector2, epsilon: Float = 0.01f): Boolean =
    other.x in (this.x - epsilon)..(this.x + epsilon) &&
            other.y in (this.y - epsilon)..(this.y + epsilon)

fun Vector2.clampIndividual(minX: Float, maxX: Float, minY: Float, maxY: Float): Vector2 = Vector2(
    this.x.coerceIn(minX, maxX),
    this.y.coerceIn(minY, maxY),
)

infix fun Vector2.midPoint(other: Vector2): Vector2 {
    val off = this - other
    return other + off * 0.5f
}

operator fun Vector2.component1(): Float = this.x
operator fun Vector2.component2(): Float = this.y

/**
 * makes sure that [this] is between [min] and [max] (inclusive)
 */
fun Float.between(min: Float, max: Float): Float {
    if (this < min) return min
    if (this > max) return max
    return this
}

/**
 * makes sure that [this] is between [min] and [max] (inclusive)
 */
fun Int.between(min: Int, max: Int): Int {
    if (this < min) return min
    if (this > max) return max
    return this
}

fun <T, U> MutableMap<T, U>.defaultValue(default: U): MutableMap<T, U> = object : HashMap<T, U>() {

    init { putAll(this@defaultValue) }

    override fun get(key: T): U? {
        val result = super.get(key)
        if (result == null) {
            this@defaultValue[key] = default
            return default
        }
        return result
    }
}

/**
 * makes sure that [this] is between [min] and [max] (inclusive)
 */
fun Double.between(min: Double, max: Double): Double {
    if (this < min) return min
    if (this > max) return max
    return this
}

fun Vector2(x: Number, y: Number): Vector2 {
    return Vector2(x.toFloat(), y.toFloat())
}

/**
 * compares two floats using an epsilon to make sure rounding errors don't break anything
 */
fun Float.epsilonEquals(other: Float, epsilon: Float = 0.0005f): Boolean {
    return this in (other - epsilon)..(other + epsilon)
}

val Float.radians: Float
    get() = Math.toRadians(this.toDouble()).toFloat()

val Float.degrees: Float
    get() = Math.toDegrees(this.toDouble()).toFloat()

fun Float.percent(x: Number): Float = (this * x / 100)

private operator fun Float.times(x: Number): Float {
    return when (x) {
        is Byte -> this * x
        is Float -> this * x
        is Int -> this * x
        is Long -> this * x
        is Short -> this * x
        else -> (this * x.toDouble()).toFloat()
    }
}


fun ClosedFloatingPointRange<Float>.random(random: Random = Random): Float {
    return random.nextFloat() * (endInclusive - start) + start
}

fun <T, U> Collection<T>.slot(keyMapper: (T) -> U): Map<U, List<T>> {
    val map = mutableMapOf<U, MutableList<T>>()
    forEach {
        val key = keyMapper(it)
        if (map[key] == null) map[key] = mutableListOf()
        map[key]!!.add(it)
    }
    return map
}

fun <T> Collection<T>.splitInTwo(predicate: (T) -> Boolean): Pair<List<T>, List<T>> {
    val ifTrue = mutableListOf<T>()
    val ifFalse = mutableListOf<T>()
    forEach {
        (if (predicate(it)) ifTrue else ifFalse).add(it)
    }
    return ifTrue to ifFalse
}

infix fun <T> ClosedFloatingPointRange<T>.intersection(
    other: ClosedFloatingPointRange<T>
): Boolean where T : Comparable<T> = this.start in other || other.start in this

infix fun IntRange.intersection(other: IntRange): Boolean = this.start in other || other.start in this

inline fun <reified T> ClosedFloatingPointRange<T>.asArray(
): Array<T> where T : Comparable<T> = arrayOf(this.start, this.endInclusive)

public fun <E> List<E>.subListTillMax(toIndex: Int): List<E> {
    return subList(0, min(size, toIndex))
}

fun Vector2.clone(): Vector2 {
    return Vector2(x, y)
}

operator fun AtomicInteger.inc(): AtomicInteger {
    this.incrementAndGet()
    return this
}


operator fun Float.plus(fl: Float?): Float {
    if (fl != null) return this + fl.toFloat()
    return this
}


val AtomicInteger.get: Int
    get() = this.get()

fun OnjArray.toIntRange(): IntRange {
    val first = this.get<Long>(0).toInt()
    val second = this.get<Long>(1).toInt()
    if (second < first) throw RuntimeException("second value must be higher than first when creating an IntRange")
    return first..second
}

fun OnjArray.toFloatRange(): ClosedFloatingPointRange<Float> {
    val first = this.get<Double>(0).toFloat()
    val second = this.get<Double>(1).toFloat()
    if (second <= first) throw RuntimeException("second value must be higher than first when creating a FloatRange")
    return first..second
}

fun Vector2.toArray(): Array<Float> = arrayOf(x, y)

fun OnjArray.toVector2(): Vector2 = Vector2(get<Double>(0).toFloat(), get<Double>(1).toFloat())

fun <T> Collection<Pair<Int, T>>.weightedRandom(random: Random = Random): T {
    val total = this.sumOf { abs(it.first) }
    val choice = (0..total).random(random)
    var acc = 0
    this.forEach { (weight, value) ->
        acc += abs(weight)
        if (choice <= acc) return value
    }
    throw NoSuchElementException("weightedRandom called on an empty collection")
}

fun Collection<Timeline>.collectTimeline(): Timeline {
    val actions = mutableListOf<Timeline.TimelineAction>()
    forEach {
        if (it.hasBeenStarted) throw RuntimeException("cannot collect a timeline that has been started already")
        actions.addAll(it.actions)
    }
    return Timeline(actions)
}

fun Collection<Timeline>.collectParallelTimeline(): Timeline {
    val actions = mutableListOf<Timeline.TimelineAction>()
    forEach {
        if (it.hasBeenStarted) throw RuntimeException("cannot collect a timeline that has been started already")
        actions.add(it.asAction())
    }
    return ParallelTimelineAction(actions).wrap()
}

fun <T> Collection<T>.randomIndex(): Int = (0..this.size).random()

fun String.lowerCaseFirstChar(): String = this.replaceFirstChar { it.lowercaseChar() }

fun String.onjString(): OnjString = OnjString(this)

fun Timeline.TimelineBuilderDSL.awaitConfirmationInput(screen: OnjScreen, maxTime: Long? = null) {
    includeAction(screen.confirmationClickTimelineAction(maxTime))
}

inline fun <T> Iterable<T>.splitAt(predicate: (T) -> Boolean): List<List<T>> {
    val chunks = mutableListOf<MutableList<T>>(mutableListOf())
    forEach { element ->
        if (predicate(element)) chunks.add(mutableListOf())
        chunks.last().add(element)
    }
    return chunks.filter { it.isNotEmpty() }
}

inline fun <T, U> Iterable<T>.zip(creator: (T) -> U): List<Pair<T, U>> = map { it to creator(it) }
inline fun <T, U> Iterable<T>.zipToFirst(creator: (T) -> U): List<Pair<U, T>> = map { creator(it) to it }

inline fun <T, U, V> Iterable<Pair<T, U>>.mapFirst(mapper: (T) -> V): List<Pair<V, U>> = map { (first, second) -> mapper(first) to second }
inline fun <T, U, V> Iterable<Pair<T, U>>.mapSecond(mapper: (U) -> V): List<Pair<T, V>> = map { (first, second) -> first to mapper(second) }

fun IntRange.midPoint(): Int = first + ((last - first) * 0.5).toInt()

fun IntRange.scale(factor: Double): IntRange = IntRange(
    (this.first * factor).roundToInt(),
    (this.last * factor).roundToInt()
)

fun Float.toOnjYoga(unit: YogaUnit = YogaUnit.POINT): OnjYogaValue {
    return OnjYogaValue(YogaValue(this, unit))
}

fun String.substringTillEnd(start: Int = 0, end: Int = length - 1): String {
    return substring(max(start, 0), min(max(end, 0), length - 1))
}

@Suppress("UNCHECKED_CAST")
fun DragAndDrop.removeAllListenersWithActor(actor: Actor) { //This feels highly illegal
    val fieldSource = DragAndDrop::class.java.getDeclaredField("sourceListeners")
    fieldSource.isAccessible = true
    val sources = (fieldSource.get(this) as ObjectMap<DragAndDrop.Source, DragListener>).map { it.key }
    sources.filter { it.actor == actor }.forEach {
        removeSource(it)
        if (it is CenteredDragSource) actor.removeListener(it.centerOnClick)
    }
    val fieldTarget = DragAndDrop::class.java.getDeclaredField("targets")
    fieldTarget.isAccessible = true
    val targets = (fieldTarget.get(this) as com.badlogic.gdx.utils.Array<DragAndDrop.Target>)
    targets.filter { it.actor == actor }.forEach { removeTarget(it) }
}

fun GameAnimation.asTimeline(controller: GameController): Timeline = Timeline.timeline {
    action {
        controller.playGameAnimation(this@asTimeline)
    }
    delayUntil { this@asTimeline.isFinished() }
}

fun Float.minMagnitude(min: Float): Float = when {
    this > 0 && this < min -> min
    this < 0 && this > -min -> -min
    else -> this
}

infix fun Int.pluralS(word: String): String = if (this == 1) "$this $word" else "$this ${word}s"

fun Actor.setPosition(pos: Vector2) = setPosition(pos.x, pos.y)

inline fun <T> MutableIterable<T>.iterateRemoving(block: (value: T, remover: () -> Unit) -> Unit) {
    val iterator = iterator()
    while (iterator.hasNext()) {
        val next = iterator.next()
        block(next, { iterator.remove() })
    }
}

inline fun <T, U> MutableMap<T, U>.iterateRemoving(block: (value: MutableMap.MutableEntry<T, U>, remover: () -> Unit) -> Unit) {
    val iterator = iterator()
    while (iterator.hasNext()) {
        val next = iterator.next()
        block(next, { iterator.remove() })
    }
}

fun Color.interpolate(other: Color): Color {
    return Color(
        (this.r + other.r) / 2,
        (this.g + other.g) / 2,
        (this.b + other.b) / 2,
        (this.a + other.a) / 2
    )
}

object Utils {

    fun coinFlip(probability: Float): Boolean = (0f..1f).random() < probability

    /**
     * sets the currently active cursor
     */
    @MainThreadOnly
    fun setCursor(cursor: Either<Cursor, SystemCursor>) = when (cursor) {
        is Either.Left -> Gdx.graphics.setCursor(cursor.value)
        is Either.Right -> Gdx.graphics.setSystemCursor(cursor.value)
    }

    /**
     * gets the current cursor pos and unprojects it using [viewport]
     */
    @AllThreadsAllowed
    fun getCursorPos(viewport: Viewport): Vector2 {
        return viewport.camera.unproject(Vector3(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), 0f)).xy
    }

    fun worldSpaceToScreenSpaceDimensions(width: Float, height: Float, viewport: Viewport): Pair<Float, Float> {
        val screenSpaceWidth = (viewport.screenWidth / viewport.worldWidth) * width
        val screenSpaceHeight = (viewport.screenHeight / viewport.worldHeight) * height
        return screenSpaceHeight to screenSpaceWidth
    }

    /**
     * convert slot from external representation (1 comes after 5)
     * to internal representation (4 comes after 5) and back
     */
    fun convertSlotRepresentation(slot: Int): Int = if (slot == 5) 5 else 5 - slot

    /**
     * loads either a custom cursor or a system cursor
     * @throws RuntimeException when [cursorName] is not known
     */
    @MainThreadOnly
    fun loadCursor(
        useSystemCursor: Boolean,
        cursorName: String,
        onjScreen: OnjScreen
    ): Promise<Either<Cursor, SystemCursor>> {

        if (useSystemCursor) {

            return when (cursorName) {

                "hand" -> SystemCursor.Hand
                "arrow" -> SystemCursor.Arrow
                "ibeam" -> SystemCursor.Ibeam
                "crosshair" -> SystemCursor.Crosshair
                "horizontal resize" -> SystemCursor.HorizontalResize
                "vertical resize" -> SystemCursor.VerticalResize
                "nw se resize" -> SystemCursor.NWSEResize
                "ne sw resize" -> SystemCursor.NESWResize
                "all resize" -> SystemCursor.AllResize
                "not allowed" -> SystemCursor.NotAllowed
                "none" -> SystemCursor.None
                else -> throw RuntimeException("unknown system cursor: $cursorName")

            }.eitherRight().asPromise()

        } else {
            return ResourceManager.request<Cursor>(onjScreen, onjScreen, cursorName).map { it.eitherLeft() }
        }
    }

    @AllThreadsAllowed
    fun interpolationOrError(name: String): Interpolation = when (name) {

        "linear" -> Interpolation.linear
        "swing" -> Interpolation.swing
        "swing in" -> Interpolation.swingIn
        "swing out" -> Interpolation.swingOut
        "bounce" -> Interpolation.bounce
        "bounce in" -> Interpolation.bounceIn
        "bounce out" -> Interpolation.bounceOut
        "elastic" -> Interpolation.elastic
        "elastic in" -> Interpolation.elasticIn
        "elastic out" -> Interpolation.elasticOut
        "circle" -> Interpolation.circle
        "circle in" -> Interpolation.circleIn
        "circle out" -> Interpolation.circleOut
        "smooth" -> Interpolation.smooth

        else -> throw RuntimeException("Unknown interpolation: $name")
    }

}
