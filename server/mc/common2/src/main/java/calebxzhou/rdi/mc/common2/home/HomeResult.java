package calebxzhou.rdi.mc.common2.home;

public record HomeResult(boolean success, String message) {
    public static HomeResult ok(String message) {
        return new HomeResult(true, message);
    }

    public static HomeResult error(String message) {
        return new HomeResult(false, message);
    }
}
