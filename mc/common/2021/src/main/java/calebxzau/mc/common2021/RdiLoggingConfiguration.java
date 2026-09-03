package calebxzau.mc.common2021;

import java.io.File;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;

public final class RdiLoggingConfiguration {
    private RdiLoggingConfiguration() {
    }

    public static void reapplyConfiguredLog4j2() {
        String path = System.getProperty("log4j.configurationFile");
        if (path == null || path.isBlank()) {
            return;
        }
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        context.setConfigLocation(new File(path).toURI());
        LogManager.getLogger("rdi").info("Reapplied RDI logging configuration");
    }
}
