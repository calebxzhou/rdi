package calebxzhou.rdi.mc.rcmd;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RcmdArgumentTypes {
    public static final RcmdArgumentType<Boolean> BOOL = new BoolType();
    public static final RcmdArgumentType<Integer> INT = new IntType();
    public static final RcmdArgumentType<Long> LONG = new LongType();
    public static final RcmdArgumentType<Double> DOUBLE = new DoubleType();
    public static final RcmdArgumentType<String> STRING = new StringType();
    public static final RcmdArgumentType<String> MESSAGE = new MessageType();

    private RcmdArgumentTypes() {
    }

    public static RcmdArgumentType<String> enumOf(String... values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("enum选项不能为空");
        }
        return new EnumType(values);
    }

    private static String requireToken(List<String> tokens, int tokenIndex, String typeName) throws RcmdParseException {
        if (tokenIndex >= tokens.size()) {
            throw new RcmdParseException("缺少" + typeName + "参数");
        }
        return tokens.get(tokenIndex);
    }

    public static final class BoolType implements RcmdArgumentType<Boolean> {
        @Override
        public String name() {
            return "bool";
        }

        @Override
        public RcmdArgumentParseResult<Boolean> parse(List<String> tokens, int tokenIndex) throws RcmdParseException {
            String token = requireToken(tokens, tokenIndex, name());
            Boolean value = switch (token.toLowerCase(Locale.ROOT)) {
                case "true", "on", "yes", "1" -> Boolean.TRUE;
                case "false", "off", "no", "0" -> Boolean.FALSE;
                default -> throw new RcmdParseException("需要bool参数，收到：" + token);
            };
            return new RcmdArgumentParseResult<>(value, tokenIndex + 1);
        }
    }

    public static final class IntType implements RcmdArgumentType<Integer> {
        @Override
        public String name() {
            return "int";
        }

        @Override
        public RcmdArgumentParseResult<Integer> parse(List<String> tokens, int tokenIndex) throws RcmdParseException {
            String token = requireToken(tokens, tokenIndex, name());
            try {
                return new RcmdArgumentParseResult<>(Integer.valueOf(token), tokenIndex + 1);
            } catch (NumberFormatException e) {
                throw new RcmdParseException("需要int参数，收到：" + token);
            }
        }
    }

    public static final class LongType implements RcmdArgumentType<Long> {
        @Override
        public String name() {
            return "long";
        }

        @Override
        public RcmdArgumentParseResult<Long> parse(List<String> tokens, int tokenIndex) throws RcmdParseException {
            String token = requireToken(tokens, tokenIndex, name());
            try {
                return new RcmdArgumentParseResult<>(Long.valueOf(token), tokenIndex + 1);
            } catch (NumberFormatException e) {
                throw new RcmdParseException("需要long参数，收到：" + token);
            }
        }
    }

    public static final class DoubleType implements RcmdArgumentType<Double> {
        @Override
        public String name() {
            return "double";
        }

        @Override
        public RcmdArgumentParseResult<Double> parse(List<String> tokens, int tokenIndex) throws RcmdParseException {
            String token = requireToken(tokens, tokenIndex, name());
            try {
                return new RcmdArgumentParseResult<>(Double.valueOf(token), tokenIndex + 1);
            } catch (NumberFormatException e) {
                throw new RcmdParseException("需要double参数，收到：" + token);
            }
        }
    }

    public static final class StringType implements RcmdArgumentType<String> {
        @Override
        public String name() {
            return "string";
        }

        @Override
        public RcmdArgumentParseResult<String> parse(List<String> tokens, int tokenIndex) throws RcmdParseException {
            return new RcmdArgumentParseResult<>(requireToken(tokens, tokenIndex, name()), tokenIndex + 1);
        }
    }

    public static final class MessageType implements RcmdArgumentType<String> {
        @Override
        public String name() {
            return "message";
        }

        @Override
        public RcmdArgumentParseResult<String> parse(List<String> tokens, int tokenIndex) throws RcmdParseException {
            if (tokenIndex >= tokens.size()) {
                throw new RcmdParseException("需要message参数");
            }
            return new RcmdArgumentParseResult<>(String.join(" ", tokens.subList(tokenIndex, tokens.size())), tokens.size());
        }
    }

    public static final class EnumType implements RcmdArgumentType<String> {
        private final Set<String> values;

        private EnumType(String[] values) {
            var normalized = new LinkedHashSet<String>();
            for (String value : values) {
                if (value == null || value.isBlank()) {
                    throw new IllegalArgumentException("enum选项不能包含空值");
                }
                normalized.add(value.toLowerCase(Locale.ROOT));
            }
            this.values = Collections.unmodifiableSet(normalized);
        }

        @Override
        public String name() {
            return "enum" + Arrays.toString(values.toArray(new String[0]));
        }

        @Override
        public RcmdArgumentParseResult<String> parse(List<String> tokens, int tokenIndex) throws RcmdParseException {
            String token = requireToken(tokens, tokenIndex, "enum");
            String normalized = token.toLowerCase(Locale.ROOT);
            if (!values.contains(normalized)) {
                throw new RcmdParseException("需要以下选项之一：" + values + "，收到：" + token);
            }
            return new RcmdArgumentParseResult<>(normalized, tokenIndex + 1);
        }
    }
}
