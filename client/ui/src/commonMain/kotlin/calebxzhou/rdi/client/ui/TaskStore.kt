package calebxzhou.rdi.client.ui

import calebxzhou.rdi.common.model.Task

object TaskStore {
    var current: Task? = null
    var autoClose: Boolean = false
    var onDone: (() -> Unit)? = null
    var onBack: (() -> Unit)? = null
}
