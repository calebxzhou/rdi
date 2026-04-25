package calebxzhou.rdi.mc.common2.tpa;

public record TpaResult(boolean success, String message) {
    public static TpaResult ok(String message) {
        return new TpaResult(true, message);
    }

    public static TpaResult error(String message) {
        return new TpaResult(false, message);
    }
}
