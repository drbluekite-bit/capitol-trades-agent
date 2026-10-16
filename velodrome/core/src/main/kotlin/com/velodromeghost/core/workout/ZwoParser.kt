package com.velodromeghost.core.workout

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ZwoParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Parser for Zwift workout files (.zwo).
 *
 * Supported blocks: Warmup, Cooldown, Ramp, SteadyState, SolidState, IntervalsT, FreeRide,
 * MaxEffort, plus textevent messages (inside a block, or at workout level with an absolute offset).
 * Attribute names are matched case-insensitively because hand-written files vary.
 */
object ZwoParser {

    fun parse(xml: String, fallbackName: String = "Workout"): Workout =
        parse(xml.byteInputStream(), fallbackName)

    fun parse(input: InputStream, fallbackName: String = "Workout"): Workout {
        val factory = DocumentBuilderFactory.newInstance()
        // Harden against XXE; not every platform parser supports every feature.
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.isExpandEntityReferences = false }
        factory.isNamespaceAware = false

        val doc = try {
            factory.newDocumentBuilder().parse(input)
        } catch (e: Exception) {
            throw ZwoParseException("Not a valid .zwo (XML) file: ${e.message}", e)
        }
        val root = doc.documentElement ?: throw ZwoParseException("Empty file")
        if (!root.tagName.equals("workout_file", ignoreCase = true)) {
            throw ZwoParseException("Not a .zwo file (expected <workout_file>, found <${root.tagName}>)")
        }

        val name = root.childText("name")?.takeIf { it.isNotBlank() } ?: fallbackName
        val description = root.childText("description").orEmpty()
        val author = root.childText("author").orEmpty()
        val workoutEl = root.childElements().firstOrNull { it.tagName.equals("workout", true) }
            ?: throw ZwoParseException("No <workout> section found")

        val segments = mutableListOf<Segment>()
        val events = mutableListOf<TextEvent>()
        var t = 0.0

        for (el in workoutEl.childElements()) {
            val tag = el.tagName.lowercase()
            if (tag == "textevent") {
                el.textEvent(0.0)?.let(events::add)
                continue
            }
            val added = parseBlock(tag, el)
            if (added.isEmpty()) continue
            el.childElements()
                .filter { it.tagName.equals("textevent", true) }
                .forEach { child -> child.textEvent(t)?.let(events::add) }
            segments += added
            t += added.sumOf { it.durationSec }
        }

        if (segments.isEmpty()) throw ZwoParseException("Workout \"$name\" has no usable blocks")
        return Workout(name, segments, description, author, events)
    }

    private fun parseBlock(tag: String, el: Element): List<Segment> {
        val duration = el.num("Duration")
        val cadence = el.cadence()
        return when (tag) {
            "warmup", "ramp" -> {
                if (duration == null || duration <= 0) return emptyList()
                val low = el.power("PowerLow") ?: el.power("Power") ?: 0.5
                val high = el.power("PowerHigh") ?: low
                val kind = if (tag == "warmup") SegmentKind.WARMUP else SegmentKind.RAMP
                listOf(Segment(kind, duration, low, high, cadence))
            }
            "cooldown" -> {
                if (duration == null || duration <= 0) return emptyList()
                val a = el.power("PowerLow") ?: el.power("Power") ?: 0.5
                val b = el.power("PowerHigh") ?: a
                // Files disagree on which attribute is the start; a cooldown always goes down.
                listOf(Segment(SegmentKind.COOLDOWN, duration, max(a, b), min(a, b), cadence))
            }
            "steadystate", "solidstate" -> {
                if (duration == null || duration <= 0) return emptyList()
                val power = el.power("Power") ?: run {
                    val low = el.power("PowerLow")
                    val high = el.power("PowerHigh")
                    if (low != null && high != null) (low + high) / 2 else low ?: high
                } ?: 0.5
                listOf(Segment(SegmentKind.STEADY, duration, power, power, cadence))
            }
            "intervalst" -> {
                val repeat = (el.num("Repeat") ?: 1.0).roundToInt().coerceIn(1, 500)
                val onDur = el.num("OnDuration") ?: return emptyList()
                val offDur = el.num("OffDuration") ?: 0.0
                val onStart = el.power("OnPower") ?: el.power("PowerOnLow") ?: el.power("PowerOnHigh") ?: 1.0
                val onEnd = el.power("OnPower") ?: el.power("PowerOnHigh") ?: onStart
                val offStart = el.power("OffPower") ?: el.power("PowerOffLow") ?: el.power("PowerOffHigh") ?: 0.5
                val offEnd = el.power("OffPower") ?: el.power("PowerOffHigh") ?: offStart
                val restCadence = el.num("CadenceResting")?.roundToInt()
                buildList {
                    repeat(repeat) {
                        if (onDur > 0) add(Segment(SegmentKind.INTERVAL_ON, onDur, onStart, onEnd, cadence))
                        if (offDur > 0) add(Segment(SegmentKind.INTERVAL_OFF, offDur, offStart, offEnd, restCadence))
                    }
                }
            }
            "freeride" -> {
                if (duration == null || duration <= 0) return emptyList()
                listOf(Segment(SegmentKind.FREE_RIDE, duration, Workout.FREE_RIDE_POWER, Workout.FREE_RIDE_POWER, cadence))
            }
            "maxeffort" -> {
                if (duration == null || duration <= 0) return emptyList()
                listOf(Segment(SegmentKind.MAX_EFFORT, duration, Workout.MAX_EFFORT_POWER, Workout.MAX_EFFORT_POWER, cadence))
            }
            else -> emptyList()
        }
    }

    private fun Element.childElements(): List<Element> {
        val out = ArrayList<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n.nodeType == Node.ELEMENT_NODE) out += n as Element
        }
        return out
    }

    private fun Element.childText(tag: String): String? =
        childElements().firstOrNull { it.tagName.equals(tag, true) }?.textContent?.trim()

    private fun Element.attr(name: String): String? {
        val attrs = attributes
        for (i in 0 until attrs.length) {
            val a = attrs.item(i)
            if (a.nodeName.equals(name, ignoreCase = true)) return a.nodeValue
        }
        return null
    }

    private fun Element.num(name: String): Double? = attr(name)?.trim()?.toDoubleOrNull()

    /** FTP fraction; values over 5 are assumed to be percentages (e.g. 85 means 0.85). */
    private fun Element.power(name: String): Double? =
        num(name)?.let { if (it > 5.0) it / 100.0 else it }?.coerceAtLeast(0.0)

    private fun Element.cadence(): Int? {
        num("Cadence")?.let { return it.roundToInt() }
        val low = num("CadenceLow")
        val high = num("CadenceHigh")
        return when {
            low != null && high != null -> ((low + high) / 2).roundToInt()
            else -> (low ?: high)?.roundToInt()
        }
    }

    private fun Element.textEvent(offset: Double): TextEvent? {
        val message = attr("message")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val time = num("timeoffset") ?: 0.0
        return TextEvent(offset + time, message)
    }
}
