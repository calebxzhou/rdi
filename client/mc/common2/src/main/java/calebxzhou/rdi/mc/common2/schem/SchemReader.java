package calebxzhou.rdi.mc.common2.schem;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * calebxzhou @ 2026-05-08 14:35
 */
public final class SchemReader {
    private static final int TAG_END = 0;
    private static final int TAG_BYTE = 1;
    private static final int TAG_SHORT = 2;
    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_FLOAT = 5;
    private static final int TAG_DOUBLE = 6;
    private static final int TAG_BYTE_ARRAY = 7;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_INT_ARRAY = 11;
    private static final int TAG_LONG_ARRAY = 12;

    private SchemReader() {
    }

    public static Schematic read(File file) throws IOException {
        return read(file.toPath());
    }

    public static Schematic read(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            return read(input);
        }
    }

    public static Schematic read(InputStream inputStream) throws IOException {
        var input = new DataInputStream(new GZIPInputStream(inputStream));
        int rootType = input.readUnsignedByte();
        if (rootType != TAG_COMPOUND) {
            throw new IOException("Schematic root must be an NBT compound tag");
        }
        readString(input);
        var root = readCompoundPayload(input);
        return Schematic.from(root);
    }

    public record Schematic(
            int width,
            int height,
            int length,
            String materials,
            byte[] rawBlocks,
            byte[] rawData,
            byte[] addBlocks,
            List<NbtCompound> entities,
            List<NbtCompound> tileEntities,
            NbtCompound root
    ) {
        public Schematic {
            if (width <= 0 || height <= 0 || length <= 0) {
                throw new IllegalArgumentException("Schematic dimensions must be positive");
            }
            int expectedBlocks = Math.multiplyExact(Math.multiplyExact(width, height), length);
            if (rawBlocks.length != expectedBlocks) {
                throw new IllegalArgumentException("Blocks length " + rawBlocks.length + " does not match dimensions " + expectedBlocks);
            }
            if (rawData.length != expectedBlocks) {
                throw new IllegalArgumentException("Data length " + rawData.length + " does not match dimensions " + expectedBlocks);
            }
            if (addBlocks != null && addBlocks.length < (expectedBlocks + 1) / 2) {
                throw new IllegalArgumentException("AddBlocks length " + addBlocks.length + " is too small for " + expectedBlocks + " blocks");
            }
            materials = materials == null ? "" : materials;
            rawBlocks = Arrays.copyOf(rawBlocks, rawBlocks.length);
            rawData = Arrays.copyOf(rawData, rawData.length);
            addBlocks = addBlocks == null ? null : Arrays.copyOf(addBlocks, addBlocks.length);
            entities = List.copyOf(entities);
            tileEntities = List.copyOf(tileEntities);
            root = root == null ? new NbtCompound(Map.of()) : root;
        }

        private static Schematic from(NbtCompound root) {
            int width = requireNumber(root, "Width").intValue();
            int height = requireNumber(root, "Height").intValue();
            int length = requireNumber(root, "Length").intValue();
            var blocks = requireByteArray(root, "Blocks");
            var data = requireByteArray(root, "Data");
            var addBlocks = optionalByteArray(root, "AddBlocks");
            var materials = root.string("Materials").orElse("");
            var entities = compoundList(root.get("Entities"));
            var tileEntities = compoundList(root.get("TileEntities"));
            return new Schematic(width, height, length, materials, blocks, data, addBlocks, entities, tileEntities, root);
        }

        public int index(int x, int y, int z) {
            checkPos(x, y, z);
            return (y * length + z) * width + x;
        }

        public BlockEntry blockAt(int x, int y, int z) {
            int index = index(x, y, z);
            return new BlockEntry(x, y, z, blockIdAtIndex(index), blockDataAtIndex(index));
        }

        public int blockIdAt(int x, int y, int z) {
            return blockIdAtIndex(index(x, y, z));
        }

        public int blockDataAt(int x, int y, int z) {
            return blockDataAtIndex(index(x, y, z));
        }

        public List<BlockEntry> blocks() {
            var entries = new ArrayList<BlockEntry>(rawBlocks.length);
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        entries.add(blockAt(x, y, z));
                    }
                }
            }
            return entries;
        }

        public WorldEditPlacement worldEditPlacement() {
            return new WorldEditPlacement(
                    root.intValue("WEOriginX").orElse(null),
                    root.intValue("WEOriginY").orElse(null),
                    root.intValue("WEOriginZ").orElse(null),
                    root.intValue("WEOffsetX").orElse(null),
                    root.intValue("WEOffsetY").orElse(null),
                    root.intValue("WEOffsetZ").orElse(null)
            );
        }

        public byte[] blockBytes() {
            return Arrays.copyOf(rawBlocks, rawBlocks.length);
        }

        public byte[] dataBytes() {
            return Arrays.copyOf(rawData, rawData.length);
        }

        @Override
        public byte[] rawBlocks() {
            return blockBytes();
        }

        @Override
        public byte[] rawData() {
            return dataBytes();
        }

        @Override
        public byte[] addBlocks() {
            return addBlocks == null ? null : Arrays.copyOf(addBlocks, addBlocks.length);
        }

        private int blockIdAtIndex(int index) {
            int id = rawBlocks[index] & 0xFF;
            if (addBlocks == null) {
                return id;
            }
            int packed = addBlocks[index / 2] & 0xFF;
            int highBits = (index % 2 == 0) ? (packed >> 4) & 0x0F : packed & 0x0F;
            return id | (highBits << 8);
        }

        private int blockDataAtIndex(int index) {
            return rawData[index] & 0x0F;
        }

        private void checkPos(int x, int y, int z) {
            if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= length) {
                throw new IndexOutOfBoundsException("Block position out of schematic bounds: x=" + x + ", y=" + y + ", z=" + z);
            }
        }
    }

    public record BlockEntry(int x, int y, int z, int blockId, int data) {
    }

    public record WorldEditPlacement(
            Integer originX,
            Integer originY,
            Integer originZ,
            Integer offsetX,
            Integer offsetY,
            Integer offsetZ
    ) {
    }

    public record NbtCompound(Map<String, Object> values) {
        public NbtCompound {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }

        public Object get(String key) {
            return values.get(key);
        }

        public boolean contains(String key) {
            return values.containsKey(key);
        }

        public java.util.Optional<String> string(String key) {
            var value = values.get(key);
            return value instanceof String string ? java.util.Optional.of(string) : java.util.Optional.empty();
        }

        public java.util.Optional<Integer> intValue(String key) {
            var value = values.get(key);
            return value instanceof Number number ? java.util.Optional.of(number.intValue()) : java.util.Optional.empty();
        }

        public java.util.Optional<byte[]> byteArray(String key) {
            var value = values.get(key);
            return value instanceof byte[] bytes ? java.util.Optional.of(Arrays.copyOf(bytes, bytes.length)) : java.util.Optional.empty();
        }
    }

    private static NbtCompound readCompoundPayload(DataInputStream input) throws IOException {
        var values = new LinkedHashMap<String, Object>();
        while (true) {
            int type = input.readUnsignedByte();
            if (type == TAG_END) {
                return new NbtCompound(values);
            }
            String name = readString(input);
            values.put(name, readPayload(input, type));
        }
    }

    private static Object readPayload(DataInputStream input, int type) throws IOException {
        return switch (type) {
            case TAG_BYTE -> input.readByte();
            case TAG_SHORT -> input.readShort();
            case TAG_INT -> input.readInt();
            case TAG_LONG -> input.readLong();
            case TAG_FLOAT -> input.readFloat();
            case TAG_DOUBLE -> input.readDouble();
            case TAG_BYTE_ARRAY -> readByteArray(input);
            case TAG_STRING -> readString(input);
            case TAG_LIST -> readList(input);
            case TAG_COMPOUND -> readCompoundPayload(input);
            case TAG_INT_ARRAY -> readIntArray(input);
            case TAG_LONG_ARRAY -> readLongArray(input);
            default -> throw new IOException("Unsupported NBT tag type: " + type);
        };
    }

    private static byte[] readByteArray(DataInputStream input) throws IOException {
        int length = readLength(input, "byte array");
        var value = new byte[length];
        input.readFully(value);
        return value;
    }

    private static List<Object> readList(DataInputStream input) throws IOException {
        int elementType = input.readUnsignedByte();
        int length = readLength(input, "list");
        var value = new ArrayList<Object>(length);
        for (int i = 0; i < length; i++) {
            value.add(readPayload(input, elementType));
        }
        return value;
    }

    private static int[] readIntArray(DataInputStream input) throws IOException {
        int length = readLength(input, "int array");
        var value = new int[length];
        for (int i = 0; i < length; i++) {
            value[i] = input.readInt();
        }
        return value;
    }

    private static long[] readLongArray(DataInputStream input) throws IOException {
        int length = readLength(input, "long array");
        var value = new long[length];
        for (int i = 0; i < length; i++) {
            value[i] = input.readLong();
        }
        return value;
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort();
        var bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("Unexpected end of NBT string");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int readLength(DataInputStream input, String type) throws IOException {
        int length = input.readInt();
        if (length < 0) {
            throw new IOException("Negative NBT " + type + " length: " + length);
        }
        return length;
    }

    private static Number requireNumber(NbtCompound compound, String key) {
        var value = compound.get(key);
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalArgumentException("Schematic missing numeric tag: " + key);
    }

    private static byte[] requireByteArray(NbtCompound compound, String key) {
        var bytes = optionalByteArray(compound, key);
        if (bytes != null) {
            return bytes;
        }
        throw new IllegalArgumentException("Schematic missing byte array tag: " + key);
    }

    private static byte[] optionalByteArray(NbtCompound compound, String key) {
        var value = compound.get(key);
        return value instanceof byte[] bytes ? Arrays.copyOf(bytes, bytes.length) : null;
    }

    private static List<NbtCompound> compoundList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        var compounds = new ArrayList<NbtCompound>(list.size());
        for (var item : list) {
            if (item instanceof NbtCompound compound) {
                compounds.add(compound);
            }
        }
        return compounds;
    }
}
