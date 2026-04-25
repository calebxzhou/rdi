package calebxzhou.rdi.mc.common2.tpa;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TpaService {
    private static final long EXPIRE_MS = 60_000L;
    private static final ConcurrentHashMap<UUID, TpaRequest> REQUESTS_BY_TARGET = new ConcurrentHashMap<>();

    private TpaService() {
    }

    public static TpaResult request(TpaPlayer requester, String targetName, TpaPlayerLookup lookup) {
        var target = lookup.findByName(targetName);
        if (target == null) {
            return TpaResult.error("玩家" + targetName + "不在线");
        }
        if (requester.id().equals(target.id())) {
            return TpaResult.error("不能向自己发送传送请求");
        }
        REQUESTS_BY_TARGET.put(
                target.id(),
                new TpaRequest(requester.id(), requester.name(), target.name(), System.currentTimeMillis())
        );
        target.sendMessage(requester.name() + "请求传送到你身边，输入\\tpok接受，60秒内有效");
        return TpaResult.ok("已向" + target.name() + "发送传送请求，60秒内有效");
    }

    public static TpaResult accept(TpaPlayer target, TpaPlayerLookup lookup) {
        var request = REQUESTS_BY_TARGET.remove(target.id());
        if (request == null) {
            return TpaResult.error("没有待处理的传送请求");
        }
        if (request.isExpired()) {
            return TpaResult.error("传送请求已过期");
        }
        var requester = lookup.findById(request.requesterId());
        if (requester == null) {
            return TpaResult.error("请求玩家已离线");
        }
        lookup.teleportTo(requester, target);
        requester.sendMessage(request.targetName() + "已接受你的传送请求");
        return TpaResult.ok("已接受" + request.requesterName() + "的传送请求");
    }

    public static void removeRelated(UUID playerId) {
        REQUESTS_BY_TARGET.entrySet().removeIf(entry ->
                entry.getKey().equals(playerId) || entry.getValue().requesterId().equals(playerId)
        );
    }

    public static void clear() {
        REQUESTS_BY_TARGET.clear();
    }

    private record TpaRequest(UUID requesterId, String requesterName, String targetName, long createdAt) {
        private boolean isExpired() {
            return System.currentTimeMillis() - createdAt > EXPIRE_MS;
        }
    }
}
