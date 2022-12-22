package fi.hel.haitaton.hanke

import java.math.BigDecimal
import java.math.RoundingMode
import javax.validation.ConstraintViolationException

fun Any?.toJsonString(): String = OBJECT_MAPPER.writeValueAsString(this)

fun Any?.toJsonPrettyString(): String =
    OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(this)

/**
 * Serializes only the main data fields; no audit fields or other irrelevant fields.
 *
 * The fields to serialize are marked with as [com.fasterxml.jackson.annotation.JsonView] with
 * [ChangeLogView] view.
 */
fun Any?.toChangeLogJsonString(): String =
    OBJECT_MAPPER.writerWithView(ChangeLogView::class.java).writeValueAsString(this)

// These marker classes are used to get a limited set of info for logging.
open class ChangeLogView

open class NotInChangeLogView

/** Rounds a Float to one decimal. */
fun Float.roundToOneDecimal(): Float {
    return BigDecimal(this.toDouble()).setScale(1, RoundingMode.HALF_UP).toFloat()
}

internal fun ConstraintViolationException.toHankeError(default: HankeError): HankeError {
    val violation =
        constraintViolations.firstOrNull { constraintViolation ->
            constraintViolation.message.matches(HankeError.CODE_PATTERN)
        }
    return if (violation != null) {
        HankeError.valueOf(violation)
    } else {
        default
    }
}
