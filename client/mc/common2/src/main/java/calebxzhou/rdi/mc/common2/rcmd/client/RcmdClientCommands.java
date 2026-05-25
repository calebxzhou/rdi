package calebxzhou.rdi.mc.common2.rcmd.client;

import calebxzhou.rdi.mc.rcmd.RcmdCommandSpec;
import calebxzhou.rdi.mc.rcmd.RcmdDispatchResult;
import calebxzhou.rdi.mc.rcmd.RcmdDispatcher;
import calebxzhou.rdi.mc.rcmd.RcmdResult;

public final class RcmdClientCommands {
    private static final RcmdDispatcher DISPATCHER = new RcmdDispatcher();

    static {
        DISPATCHER.register(
                RcmdCommandSpec.builder("firmsection", "display", "set")
                        .description("切换已设置固定子区块边框")
                        .command(context -> toggleSetFirmSections((RcmdClientBridge) context.source()))
                        .build()
        );
        DISPATCHER.register(
                RcmdCommandSpec.builder("firmsection", "display", "now")
                        .description("切换当前子区块边框")
                        .command(context -> toggleNowFirmSection((RcmdClientBridge) context.source()))
                        .build()
        );
    }

    private RcmdClientCommands() {
    }

    public static RcmdDispatchResult dispatch(RcmdClientBridge bridge, String message) {
        return DISPATCHER.dispatch(bridge, message);
    }

    public static void reply(RcmdClientBridge bridge, RcmdResult result) {
        if (result == null || result.message().isEmpty()) {
            return;
        }
        for (var message : result.message().split("\\R")) {
            if (message.isEmpty()) {
                continue;
            }
            if (result.success()) {
                bridge.sendFeedback(message);
            } else {
                bridge.sendError(message);
            }
        }
    }

    private static RcmdResult toggleSetFirmSections(RcmdClientBridge bridge) {
        var visible = bridge.toggleSetFirmSectionsVisible();
        return RcmdResult.ok("固定子区块边框：" + (visible ? "显示" : "隐藏"));
    }

    private static RcmdResult toggleNowFirmSection(RcmdClientBridge bridge) {
        var visible = bridge.toggleNowFirmSectionVisible();
        return RcmdResult.ok("当前子区块边框：" + (visible ? "显示" : "隐藏"));
    }
}
