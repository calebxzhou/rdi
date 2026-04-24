package calebxzhou.rdi.mc.rcmd;

import java.util.ArrayList;
import java.util.List;

public final class RcmdParser {
    private RcmdParser() {
    }

    public static List<String> tokenize(String input) throws RcmdParseException {
        List<String> tokens = new ArrayList<String>();
        if (input == null) {
            return tokens;
        }
        StringBuilder token = new StringBuilder();
        boolean quoted = false;
        char quoteChar = 0;
        boolean escaping = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (escaping) {
                token.append(c);
                escaping = false;
                continue;
            }
            if (c == '\\') {
                escaping = true;
                continue;
            }
            if (quoted) {
                if (c == quoteChar) {
                    quoted = false;
                } else {
                    token.append(c);
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quoted = true;
                quoteChar = c;
                continue;
            }
            if (Character.isWhitespace(c)) {
                addToken(tokens, token);
                continue;
            }
            token.append(c);
        }
        if (escaping) {
            token.append('\\');
        }
        if (quoted) {
            throw new RcmdParseException("引号未闭合");
        }
        addToken(tokens, token);
        return tokens;
    }

    private static void addToken(List<String> tokens, StringBuilder token) {
        if (token.length() == 0) {
            return;
        }
        tokens.add(token.toString());
        token.setLength(0);
    }
}
