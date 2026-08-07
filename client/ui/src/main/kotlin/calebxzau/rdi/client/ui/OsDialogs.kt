package calebxzau.rdi.client.ui

import javax.swing.JOptionPane
import kotlin.system.exitProcess

fun showFatalStartupErrorAndExit(message: String): Nothing {
    try {
        JOptionPane.showMessageDialog(
            null,
            message,
            "RDI启动失败",
            JOptionPane.ERROR_MESSAGE,
        )
    } finally {
        exitProcess(1)
    }
}
