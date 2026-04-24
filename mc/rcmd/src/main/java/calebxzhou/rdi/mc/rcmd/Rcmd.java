package calebxzhou.rdi.mc.rcmd;

public final class Rcmd {
    public static final char PREFIX = '\\';

    private Rcmd() {
    }

    public static boolean isRcmd(String input) {
        return input != null && input.startsWith(String.valueOf(PREFIX));
    }

    public static String stripPrefix(String input) {
        if (!isRcmd(input)) {
            return input;
        }
        return input.substring(1);
    }
}
