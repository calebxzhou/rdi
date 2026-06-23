package calebxzhou.rdi.mc.client.hook;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MCLibNetworkBlockerLog {
    private static final Logger LOGGER = LogManager.getLogger("rdi");

    private MCLibNetworkBlockerLog() {
    }

    public static void blocked(String source) {
        LOGGER.info("Blocked MCLib network entry: {}", source);
    }
}
