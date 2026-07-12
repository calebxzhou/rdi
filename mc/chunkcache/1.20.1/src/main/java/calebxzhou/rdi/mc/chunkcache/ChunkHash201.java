package calebxzhou.rdi.mc.chunkcache;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;

public final class ChunkHash201 {
    private ChunkHash201() {
    }

    public record Result(long hash, int dataBytes) {
    }

    @SuppressWarnings("deprecation")
    public static Result compute(ClientboundLevelChunkPacketData chunkData, int chunkX, int chunkZ) {
        Hasher hasher = Hashing.murmur3_128().newHasher();

        FriendlyByteBuf sectionsBuffer = chunkData.getReadBuffer();
        int sectionsBytes = sectionsBuffer.readableBytes();
        byte[] sections = new byte[sectionsBytes];
        sectionsBuffer.getBytes(sectionsBuffer.readerIndex(), sections);
        sectionsBuffer.release();
        hasher.putBytes(sections);

        hashTag(hasher, chunkData.getHeightmaps());

        record BlockEntityEntry(BlockPos pos, BlockEntityType<?> type, CompoundTag tag) {
        }
        var blockEntities = new ArrayList<BlockEntityEntry>();
        chunkData.getBlockEntitiesTagsConsumer(chunkX, chunkZ).accept((pos, type, tag) ->
                blockEntities.add(new BlockEntityEntry(pos.immutable(), type, tag)));
        blockEntities.sort(Comparator.comparingInt((BlockEntityEntry entry) -> entry.pos().getX())
                .thenComparingInt(entry -> entry.pos().getY())
                .thenComparingInt(entry -> entry.pos().getZ()));

        hasher.putInt(blockEntities.size());
        for (BlockEntityEntry entry : blockEntities) {
            hasher.putInt(entry.pos().getX());
            hasher.putInt(entry.pos().getY());
            hasher.putInt(entry.pos().getZ());
            ResourceLocation typeId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entry.type());
            if (typeId != null) {
                hasher.putString(typeId.toString(), StandardCharsets.UTF_8);
            }
            hashTag(hasher, entry.tag());
        }

        return new Result(hasher.hash().asLong(), sectionsBytes);
    }

    private static void hashTag(Hasher hasher, Tag tag) {
        if (tag == null) {
            hasher.putByte((byte) 0);
            return;
        }

        hasher.putByte(tag.getId());
        if (tag instanceof CompoundTag compound) {
            var keys = new ArrayList<>(compound.getAllKeys());
            keys.sort(String::compareTo);
            hasher.putInt(keys.size());
            for (String key : keys) {
                hasher.putInt(key.length());
                hasher.putString(key, StandardCharsets.UTF_8);
                hashTag(hasher, compound.get(key));
            }
        } else if (tag instanceof ListTag list) {
            hasher.putInt(list.size());
            for (Tag entry : list) {
                hashTag(hasher, entry);
            }
        } else if (tag instanceof ByteTag value) {
            hasher.putByte(value.getAsByte());
        } else if (tag instanceof ShortTag value) {
            hasher.putShort(value.getAsShort());
        } else if (tag instanceof IntTag value) {
            hasher.putInt(value.getAsInt());
        } else if (tag instanceof LongTag value) {
            hasher.putLong(value.getAsLong());
        } else if (tag instanceof FloatTag value) {
            hasher.putFloat(value.getAsFloat());
        } else if (tag instanceof DoubleTag value) {
            hasher.putDouble(value.getAsDouble());
        } else if (tag instanceof StringTag value) {
            hasher.putInt(value.getAsString().length());
            hasher.putString(value.getAsString(), StandardCharsets.UTF_8);
        } else if (tag instanceof ByteArrayTag value) {
            byte[] entries = value.getAsByteArray();
            hasher.putInt(entries.length);
            hasher.putBytes(entries);
        } else if (tag instanceof IntArrayTag value) {
            int[] entries = value.getAsIntArray();
            hasher.putInt(entries.length);
            for (int entry : entries) {
                hasher.putInt(entry);
            }
        } else if (tag instanceof LongArrayTag value) {
            long[] entries = value.getAsLongArray();
            hasher.putInt(entries.length);
            for (long entry : entries) {
                hasher.putLong(entry);
            }
        }
    }
}
