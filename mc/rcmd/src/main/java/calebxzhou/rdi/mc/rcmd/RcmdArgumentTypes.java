package calebxzhou.rdi.mc.rcmd;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RcmdArgumentTypes {
    public static final RcmdArgumentType<Boolean> BOOL = new RcmdArgumentType<Boolean>() {
        @Override
        public String getName() {
            return "bool";
        }

        @Override
        public RcmdArgumentParseResult<Boolean> parse(List<String> tokens, int tokenIndex) throws RcmdParseException {
            String token = requireToken(tokens, tokenIndex, getName());
            String normalized = token.toLowerCase(Locale.ROOT);
            if ("true".equals(normalized) || "on".equals(normalized) || "yes".equals(normalized) || "1".equals(normalized)) {
                return new RcmdArgumentParseResult<Boolean>(Boolean.TRUE, tokenIndex + 1);
            }
            if ("false".equals(normalized) || "off".equals(normalized) || "no".equals(normalized) || "0".equals(normalized)) {
                return new RcmdArgumentParseResult<Boolean>(Boolean.FALSE, tokenIndex + 1);
            }
            throw new RcmdParseException("需要bool参数，收到：" + token);
        }
    };

    public static final RcmdArgumentType<Integer> INT = new RcmdArgumentType<Integer>() {
        @Override
        public String getName() {
            return "int";
        }

        @Override
        public RcmdArgumentParseResult<Integer> parse(List<String> tokens, int tokenIndex) throws RcmdParseException {
            String token = requireToken(tokens, tokenIndex, getName());
            try {
                return new RcmdArgumentParseResult<Integer>(Integer.valueOf(token), tokenIndex + 1);
            } catch (NumberFormatException e) {
                throw new RcmdParseException("需要int参数，收到：" + token);
            }
        }
    };

    public static final RcmdArgumentType<Long> LONG = new RcmdArgumentType<Long>() {
        @Override
        public String getName() {
            return "long";
        }

        @Override
        public RcmdArgumentParseResult<Long> parse(List<String> tokens, int tokenIndex) throws RcmdParseException {
            String token = requireToken(tokens, tokenIndex, getName());
            try {
                return new RcmdArgumentParseResult<Long>(Long.valueOf(token), tokenIndex + 1);
            } catch (NumberFormatException e) {
                throw new RcmdParseException("需要long参数，收到：" + token);
            }
        }
    };

    public static final RcmdArgumentType<Double> DOUBLE = new RcmdArgumentType<Double>() {
        @Override
        public String getName() {
            return "double";
        }

        @Override
        public RcmdArgumentParseResult<Double> parse(List<String> tokens, int tokenIndex) throws RcmdParseException {
            String token = requireToken(tokens, tokenIndex, getName());
            try {
                return new RcmdArgumentParseResult<Double>(Double.valueOf(token), tokenIndex + 1);
            } catch (NumberFormatException e) {
                throw new RcmdParseException("需要double参数，收到：" + token);
            }
        }
    };

    public static final RcmdArgumentType<String> STRING = new RcmdArgumentType<String>() {
        @Override
        public String getName() {
            return "string";
        }

        @Override
        public RcmdArgumentParseResult<String> parse(List<String> tokens, int tokenIndex) throws RcmdParseException {
            return new RcmdArgumentParseResult<String>(requireToken(tokens, tokenIndex, getName()), tokenIndex + 1);
        }
    };

    public static final RcmdArgumentType<String> MESSAGE = new RcmdArgumentType<String>() {
        @Override
        public String getName() {
            return "message";
        }

        @Override
        public RcmdArgumentParseResult<String> parse(List<String> tokens, int tokenIndex) throws RcmdParseException {
            if (tokenIndex >= tokens.size()) {
                throw new RcmdParseException("需要message参数");
            }
            StringBuilder message = new StringBuilder();
            for (int i = tokenIndex; i < tokens.size(); i++) {
                if (message.length() > 0) {
                    message.append(' ');
                }
                message.append(tokens.get(i));
            }
            return new RcmdArgumentParseResult<String>(message.toString(), tokens.size());
        }
    };

    private RcmdArgumentTypes() {
    }

    public static RcmdArgumentType<String> enumOf(String... values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("enum选项不能为空");
        }
        return new EnumArgumentType(values);
    }

    private static String requireToken(List<String> tokens, int tokenIndex, String typeName) throws RcmdParseException {
        if (tokenIndex >= tokens.size()) {
            throw new RcmdParseException("缺少" + typeName + "参数");
        }
        return tokens.get(tokenIndex);
    }

    private static final class EnumArgumentType implements RcmdArgumentType<String> {
        private final Set<String> values;

        private EnumArgumentType(String[] values) {
            LinkedHashSet<String> normalized = new LinkedHashSet<String>();
            for (String value : values) {
                if (value == null || value.trim().isEmpty()) {
                    throw new IllegalArgumentException("enum选项不能包含空值");
                }
                normalized.add(value.toLowerCase(Locale.ROOT));
            }
            this.values = Collections.unmodifiableSet(normalized);
        }

        @Override
        public String getName() {
            return "enum" + Arrays.toString(values.toArray(new String[0]));
        }

        @Override
        public RcmdArgumentParseResult<String> parse(List<String> tokens, int tokenIndex) throws RcmdParseException {
            String token = requireToken(tokens, tokenIndex, "enum");
            String normalized = token.toLowerCase(Locale.ROOT);
            if (!values.contains(normalized)) {
                throw new RcmdParseException("需要以下选项之一：" + values + "，收到：" + token);
            }
            return new RcmdArgumentParseResult<String>(normalized, tokenIndex + 1);
        }
    }
}
