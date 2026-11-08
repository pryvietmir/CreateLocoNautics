package com.lycoris.loconautics.content.schedule.map;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class MapColorRegionFile implements AutoCloseable {
    public static final int REGION_SIZE = 32;
    public static final int CHUNK_SLOTS = 1024;
    public static final int HEADER_BYTES = 4096;
    public static final int PAYLOAD_BYTES = 256;
    private final FileChannel channel;
    private final int[] offsets = new int[1024];

    public MapColorRegionFile(Path path) throws IOException {
        boolean exists = Files.exists(path);
        this.channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        if (exists) {
            this.readHeader();
        } else {
            this.writeEmptyHeader();
        }
    }

    public void write(int localX, int localZ, byte[] colors) throws IOException {
        if (colors != null && colors.length == 256) {
            int slot = slot(localX, localZ);
            int offset = this.offsets[slot];
            if (offset == 0) {
                offset = (int)this.channel.size();
                this.offsets[slot] = offset;
                this.writeOffsetEntry(slot, offset);
            }

            ByteBuffer buf = ByteBuffer.wrap(colors);
            int written = 0;

            while (written < 256) {
                written += this.channel.write(buf, offset + written);
            }
        } else {
            throw new IllegalArgumentException("colors must be exactly 256 bytes");
        }
    }

    public byte[] read(int localX, int localZ) throws IOException {
        int slot = slot(localX, localZ);
        int offset = this.offsets[slot];
        if (offset == 0) {
            return null;
        }

        byte[] colors = new byte[256];
        ByteBuffer buf = ByteBuffer.wrap(colors);
        int read = 0;

        while (read < 256) {
            int n = this.channel.read(buf, offset + read);
            if (n < 0) {
                return null;
            }

            read += n;
        }

        return colors;
    }

    public boolean hasChunk(int localX, int localZ) {
        return this.offsets[slot(localX, localZ)] != 0;
    }

    @Override
    public void close() throws IOException {
        if (this.channel != null && this.channel.isOpen()) {
            this.channel.force(true);
            this.channel.close();
        }
    }

    private void readHeader() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(4096);
        this.channel.read(buf, 0L);
        buf.flip();

        for (int i = 0; i < 1024; i++) {
            this.offsets[i] = buf.getInt();
        }
    }

    private void writeEmptyHeader() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(4096);
        this.channel.write(buf, 0L);
    }

    private void writeOffsetEntry(int slot, int offset) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.putInt(offset);
        buf.flip();
        this.channel.write(buf, slot * 4L);
    }

    private static int slot(int localX, int localZ) {
        return localX + localZ * 32;
    }
}
