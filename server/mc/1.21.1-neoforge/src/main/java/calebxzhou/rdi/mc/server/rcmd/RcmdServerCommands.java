package calebxzhou.rdi.mc.server.rcmd;

import calebxzhou.rdi.mc.rcmd.RcmdArgumentTypes;
import calebxzhou.rdi.mc.rcmd.RcmdCommandSpec;
import calebxzhou.rdi.mc.rcmd.RcmdContext;
import calebxzhou.rdi.mc.rcmd.RcmdDispatcher;
import calebxzhou.rdi.mc.rcmd.RcmdResult;
import calebxzhou.rdi.mc.rcmd.RcmdSource;
import calebxzhou.rdi.mc.common2.chat.ChatRange;
import calebxzhou.rdi.mc.common2.chat.PlayerChatRangeState;
import calebxzhou.rdi.mc.common2.home.HomeResult;
import calebxzhou.rdi.mc.common2.home.HomeService;
import calebxzhou.rdi.mc.common2.tpa.TpaResult;
import calebxzhou.rdi.mc.common2.tpa.TpaService;
import calebxzhou.rdi.mc.server.home.HomePlayer211;
import calebxzhou.rdi.mc.server.tpa.TpaPlayer211;
import calebxzhou.rdi.mc.server.tpa.TpaPlayerLookup211;
import net.minecraft.server.level.ServerPlayer;

public final class RcmdServerCommands {
    private static final RcmdDispatcher DISPATCHER = new RcmdDispatcher();

    static {
        DISPATCHER.register(
                RcmdCommandSpec.builder("ping")
                        .description("Test rcmd availability")
                        .command(context -> RcmdResult.ok("pong，rcmd正常"))
                        .build()
        );
        DISPATCHER.register(
                RcmdCommandSpec.builder("chat", "range")
                        .description("Set chat range")
                        .argument("range", RcmdArgumentTypes.enumOf("host", "global"))
                        .command(context -> {
                            var range = context.getString("range");
                            var source = context.source();
                            if (!source.isPlayer()) {
                                return RcmdResult.error("此rcmd命令只能由玩家执行");
                            }
                            var chatRange = ChatRange.fromRcmdValue(range);
                            PlayerChatRangeState.set(source.playerId(), chatRange);
                            return RcmdResult.ok("聊天范围已切换为" + chatRange.getDisplayName());
                        })
                        .build()
        );
        DISPATCHER.register(
                RcmdCommandSpec.builder("tpa")
                        .description("Request teleport to another player")
                        .argument("playerName", RcmdArgumentTypes.STRING)
                        .command(RcmdServerCommands::handleTpa)
                        .build()
        );
        DISPATCHER.register(
                RcmdCommandSpec.builder("tpok")
                        .description("Accept pending teleport request")
                        .command(RcmdServerCommands::handleTpok)
                        .build()
        );
        DISPATCHER.register(
                RcmdCommandSpec.builder("sethome")
                        .description("Save current player position as a home")
                        .argument("name", RcmdArgumentTypes.STRING)
                        .command(RcmdServerCommands::handleSetHome)
                        .build()
        );
        DISPATCHER.register(
                RcmdCommandSpec.builder("home")
                        .description("Teleport player to a saved home")
                        .argument("name", RcmdArgumentTypes.STRING)
                        .command(RcmdServerCommands::handleHome)
                        .build()
        );
        DISPATCHER.register(
                RcmdCommandSpec.builder("listhome")
                        .description("List saved player homes")
                        .command(RcmdServerCommands::handleListHome)
                        .build()
        );
        DISPATCHER.register(
                RcmdCommandSpec.builder("delhome")
                        .description("Delete a saved player home")
                        .argument("name", RcmdArgumentTypes.STRING)
                        .command(RcmdServerCommands::handleDeleteHome)
                        .build()
        );
    }

    private RcmdServerCommands() {
    }

    public static RcmdDispatcher dispatcher() {
        return DISPATCHER;
    }

    public static void reply(RcmdSource source, RcmdResult result) {
        if (result == null || result.message().isEmpty()) {
            return;
        }
        for (var message : result.message().split("\\R")) {
            replyLine(source, result.success(), message);
        }
    }

    private static void replyLine(RcmdSource source, boolean success, String message) {
        if (message.isEmpty()) {
            return;
        }
        switch (success ? ReplyKind.SUCCESS : ReplyKind.ERROR) {
            case SUCCESS -> source.sendFeedback(message);
            case ERROR -> source.sendError(message);
        }
    }

    public static String getChatRange(java.util.UUID playerId) {
        return PlayerChatRangeState.get(playerId).name().toLowerCase();
    }

    private static RcmdResult handleTpa(RcmdContext context) {
        ServerPlayer requester = playerOrNull(context.source());
        if (requester == null) {
            return RcmdResult.error("此rcmd命令只能由玩家执行");
        }
        var targetName = context.getString("playerName");
        var result = TpaService.request(new TpaPlayer211(requester), targetName, new TpaPlayerLookup211(requester.server));
        return toRcmdResult(result);
    }

    private static RcmdResult handleTpok(RcmdContext context) {
        ServerPlayer target = playerOrNull(context.source());
        if (target == null) {
            return RcmdResult.error("此rcmd命令只能由玩家执行");
        }
        var result = TpaService.accept(new TpaPlayer211(target), new TpaPlayerLookup211(target.server));
        return toRcmdResult(result);
    }

    private static RcmdResult handleSetHome(RcmdContext context) {
        ServerPlayer player = playerOrNull(context.source());
        if (player == null) {
            return RcmdResult.error("此rcmd命令只能由玩家执行");
        }
        var result = HomeService.setHome(new HomePlayer211(player), context.getString("name"));
        return toRcmdResult(result);
    }

    private static RcmdResult handleHome(RcmdContext context) {
        ServerPlayer player = playerOrNull(context.source());
        if (player == null) {
            return RcmdResult.error("此rcmd命令只能由玩家执行");
        }
        var result = HomeService.goHome(new HomePlayer211(player), context.getString("name"));
        return toRcmdResult(result);
    }

    private static RcmdResult handleListHome(RcmdContext context) {
        ServerPlayer player = playerOrNull(context.source());
        if (player == null) {
            return RcmdResult.error("此rcmd命令只能由玩家执行");
        }
        var result = HomeService.listHomes(new HomePlayer211(player));
        return toRcmdResult(result);
    }

    private static RcmdResult handleDeleteHome(RcmdContext context) {
        ServerPlayer player = playerOrNull(context.source());
        if (player == null) {
            return RcmdResult.error("此rcmd命令只能由玩家执行");
        }
        var result = HomeService.deleteHome(new HomePlayer211(player), context.getString("name"));
        return toRcmdResult(result);
    }

    private static RcmdResult toRcmdResult(TpaResult result) {
        return result.success() ? RcmdResult.ok(result.message()) : RcmdResult.error(result.message());
    }

    private static RcmdResult toRcmdResult(HomeResult result) {
        return result.success() ? RcmdResult.ok(result.message()) : RcmdResult.error(result.message());
    }

    private static ServerPlayer playerOrNull(RcmdSource source) {
        if (source instanceof RcmdServerSource211 playerSource) {
            return playerSource.getPlayer();
        }
        if (source instanceof RcmdCommandSourceStackSource commandSource) {
            return commandSource.getPlayer();
        }
        return null;
    }

    private enum ReplyKind {
        SUCCESS,
        ERROR
    }
}
