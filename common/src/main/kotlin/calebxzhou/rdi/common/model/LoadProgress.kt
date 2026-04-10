package calebxzhou.rdi.common.model

sealed interface LoadProgress {
    data class Phase(val text: String) : LoadProgress
    //fraction 0f~1f
    data class Percent(val text: String, val fraction: Float) : LoadProgress
    data class Warn(val text: String) : LoadProgress
}
typealias LoadProgressConsumer = (LoadProgress) -> Unit

fun LoadProgressConsumer.phase(text: String) = this(LoadProgress.Phase(text))
fun LoadProgressConsumer.perc(text: String,frac: Float) = this(LoadProgress.Percent(text,frac))
fun LoadProgressConsumer.warn(text: String) = this(LoadProgress.Warn(text))