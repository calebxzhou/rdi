package calebxzhou.rdi.mc.server.rcmd;

import calebxzhou.rdi.mc.common2.chat.ChatRange;
import calebxzhou.rdi.mc.common2.chat.PlayerChatRangeState;
import calebxzhou.rdi.mc.common2.home.HomeResult;
import calebxzhou.rdi.mc.common2.home.HomeService;
import calebxzhou.rdi.mc.common2.tpa.TpaResult;
import calebxzhou.rdi.mc.common2.tpa.TpaService;
import calebxzhou.rdi.mc.rcmd.RcmdArgumentTypes;
import calebxzhou.rdi.mc.rcmd.RcmdCommandSpec;
import calebxzhou.rdi.mc.rcmd.RcmdContext;
import calebxzhou.rdi.mc.rcmd.RcmdDispatcher;
import calebxzhou.rdi.mc.rcmd.RcmdResult;
import calebxzhou.rdi.mc.rcmd.RcmdSource;
import calebxzhou.rdi.mc.server.home.HomePlayer201;
import calebxzhou.rdi.mc.server.tpa.TpaPlayer201;
import calebxzhou.rdi.mc.server.tpa.TpaPlayerLookup201;
import net.minecraft.server.level.ServerPlayer;

public final class RcmdServerCommands {
    private static final RcmdDispatcher DISPATCHER = new RcmdDispatcher();

    static {
        DISPATCHER.register(
                RcmdCommandSpec.builder("ping")
                        .description("测试rcmd是否可用")
                        .command(context -> RcmdResult.ok("pong，rcmd正常"))
                        .build()
        );
        DISPATCHER.register(
                RcmdCommandSpec.builder("chat", "range")
                        .description("设置聊天范围")
                        .argument("range", RcmdArgumentTypes.enumOf("host", "global"))
                        .command(context -> {
                            var source = context.source();
                            if (!source.isPlayer()) {
                                return RcmdResult.error("此rcmd命令只能由玩家执行");
                            }
                            var chatRange = ChatRange.fromRcmdValue(context.getString("range"));
                            PlayerChatRangeState.set(source.playerId(), chatRange);
                            return RcmdResult.ok("聊天范围已切换为" + chatRange.getDisplayName());
                        })
                        .build()
        );
        DISPATCHER.register(
                RcmdCommandSpec.builder("tpa")
                        .description("请求传送到另一名玩家身边")
                        .argument("playerName", RcmdArgumentTypes.STRING)
                        .command(RcmdServerCommands::handleTpa)
                        .build()
        );
        DISPATCHER.register(
                RcmdCommandSpec.builder("tpok")
                        .description("接受待处理的传送请求")
                        .command(RcmdServerCommands::handleTpok)
                        .build()
        );
        DISPATCHER.register(
                RcmdCommandSpec.builder("sethome")
                        .description("把当前位置保存为家")
                        .argument("name", RcmdArgumentTypes.STRING)
                        .command(RcmdServerCommands::handleSetHome)
                        .build()
        );
        DISPATCHER.register(
                RcmdCommandSpec.builder("home")
                        .description("传送到已保存的家")
                        .argument("name", RcmdArgumentTypes.STRING)
                        .command(RcmdServerCommands::handleHome)
                        .build()
        );
        DISPATCHER.register(
                RcmdCommandSpec.builder("listhome")
                        .description("列出已保存的家")
                        .command(RcmdServerCommands::handleListHome)
                        .build()
        );
        DISPATCHER.register(
                RcmdCommandSpec.builder("delhome")
                        .description("删除已保存的家")
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
            if (!message.isEmpty()) {
                replyLine(source, result.success(), message);
            }
        }
    }

    public static String getChatRange(java.util.UUID playerId) {
        return PlayerChatRangeState.get(playerId).name().toLowerCase();
    }

    private static void replyLine(RcmdSource source, boolean success, String message) {
        switch (success ? ReplyKind.SUCCESS : ReplyKind.ERROR) {
            case SUCCESS -> source.sendFeedback(message);
            case ERROR -> source.sendError(message);
        }
    }

    private static RcmdResult handleTpa(RcmdContext context) {
        ServerPlayer requester = playerOrNull(context.source());
        if (requester == null) {
            return RcmdResult.error("此rcmd命令只能由玩家执行");
        }
        var result = TpaService.request(
                new TpaPlayer201(requester),
                context.getString("playerName"),
                new TpaPlayerLookup201(requester.server)
        );
        return toRcmdResult(result);
    }

    private static RcmdResult handleTpok(RcmdContext context) {
        ServerPlayer target = playerOrNull(context.source());
        if (target == null) {
            return RcmdResult.error("此rcmd命令只能由玩家执行");
        }
        var result = TpaService.accept(new TpaPlayer201(target), new TpaPlayerLookup201(target.server));
        return toRcmdResult(result);
    }

    private static RcmdResult handleSetHome(RcmdContext context) {
        ServerPlayer player = playerOrNull(context.source());
        if (player == null) {
            return RcmdResult.error("此rcmd命令只能由玩家执行");
        }
        return toRcmdResult(HomeService.setHome(new HomePlayer201(player), context.getString("name")));
    }

    private static RcmdResult handleHome(RcmdContext context) {
        ServerPlayer player = playerOrNull(context.source());
        if (player == null) {
            return RcmdResult.error("此rcmd命令只能由玩家执行");
        }
        return toRcmdResult(HomeService.goHome(new HomePlayer201(player), context.getString("name")));
    }

    private static RcmdResult handleListHome(RcmdContext context) {
        ServerPlayer player = playerOrNull(context.source());
        if (player == null) {
            return RcmdResult.error("此rcmd命令只能由玩家执行");
        }
        return toRcmdResult(HomeService.listHomes(new HomePlayer201(player)));
    }

    private static RcmdResult handleDeleteHome(RcmdContext context) {
        ServerPlayer player = playerOrNull(context.source());
        if (player == null) {
            return RcmdResult.error("此rcmd命令只能由玩家执行");
        }
        return toRcmdResult(HomeService.deleteHome(new HomePlayer201(player), context.getString("name")));
    }

    private static RcmdResult toRcmdResult(TpaResult result) {
        return result.success() ? RcmdResult.ok(result.message()) : RcmdResult.error(result.message());
    }

    private static RcmdResult toRcmdResult(HomeResult result) {
        return result.success() ? RcmdResult.ok(result.message()) : RcmdResult.error(result.message());
    }

    private static ServerPlayer playerOrNull(RcmdSource source) {
        if (source instanceof RcmdServerSource201 playerSource) {
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
