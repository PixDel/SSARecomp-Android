package de.pixdel.ssarecomp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts an Xbox 360 disc image (XDVDFS, "XISO") into a folder, as extract-xiso does for the PC
 * installer. Plain Java on purpose: the setup screen runs it before the game's native libraries are
 * loaded.
 *
 * The game partition starts at a fixed offset that depends on the disc format (an image straight
 * off a dumper keeps the video partition before it); its volume descriptor is sector 32 of the
 * partition. A directory is a binary tree of entries: left/right child offsets (in 4-byte units,
 * 0 = none), start sector, size, attributes, name. Entries are 4-byte aligned and never cross a
 * sector; 0xFF fills the rest of a sector.
 */
public final class XisoExtractor {
    public interface Progress {
        /** done/total in bytes; returns false to cancel. */
        boolean onProgress(long done, long total, String path);
    }

    public static final class CancelledException extends IOException {
        CancelledException() { super("cancelled"); }
    }

    private static final int SECTOR = 2048;
    private static final byte[] MAGIC = "MICROSOFT*XBOX*MEDIA".getBytes(StandardCharsets.US_ASCII);
    /** Plain XISO, XGD1, XGD2, XGD3 (extract-xiso's list). */
    private static final long[] PARTITION_OFFSETS = { 0L, 0x18300000L, 0xFD90000L, 0x2080000L };
    private static final int ATTR_DIRECTORY = 0x10;

    private static final class Entry {
        String path;
        boolean directory;
        long sector;
        long size;
    }

    private final FileChannel in;
    private final long partition;
    private final long rootSector;
    private final long rootSize;

    public XisoExtractor(FileChannel in) throws IOException {
        this.in = in;
        ByteBuffer vd = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN);
        for (long offset : PARTITION_OFFSETS) {
            vd.clear();
            if (readFully(vd, offset + 32L * SECTOR) < 28) continue;
            boolean match = true;
            for (int i = 0; i < MAGIC.length; i++) {
                if (vd.get(i) != MAGIC[i]) { match = false; break; }
            }
            if (!match) continue;
            partition = offset;
            rootSector = vd.getInt(20) & 0xFFFFFFFFL;
            rootSize = vd.getInt(24) & 0xFFFFFFFFL;
            return;
        }
        throw new IOException("not an Xbox 360 disc image");
    }

    /** Every file and folder on the disc, folders before their contents. */
    public List<String> list() throws IOException {
        List<Entry> entries = new ArrayList<>();
        walk(rootSector, rootSize, "", entries);
        List<String> out = new ArrayList<>();
        for (Entry e : entries) out.add(e.directory ? e.path + "/" : e.path);
        return out;
    }

    /**
     * Extracts the disc into dest. skipTopLevel: names of top-level folders to leave out
     * ("$SystemUpdate", as extract-xiso -s).
     */
    public void extract(File dest, String[] skipTopLevel, Progress progress) throws IOException {
        List<Entry> entries = new ArrayList<>();
        walk(rootSector, rootSize, "", entries);
        List<Entry> wanted = new ArrayList<>();
        long total = 0;
        for (Entry e : entries) {
            String top = e.path.contains("/") ? e.path.substring(0, e.path.indexOf('/')) : e.path;
            boolean skip = false;
            if (skipTopLevel != null) {
                for (String s : skipTopLevel) if (s.equalsIgnoreCase(top)) skip = true;
            }
            if (skip) continue;
            wanted.add(e);
            if (!e.directory) total += e.size;
        }

        if (!dest.isDirectory() && !dest.mkdirs()) throw new IOException("cannot create " + dest);
        ByteBuffer buffer = ByteBuffer.allocateDirect(4 << 20);
        long done = 0;
        for (Entry e : wanted) {
            File target = new File(dest, e.path);
            if (e.directory) {
                if (!target.isDirectory() && !target.mkdirs()) throw new IOException("cannot create " + target);
                continue;
            }
            File parent = target.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("cannot create " + parent);
            }
            long position = partition + e.sector * SECTOR;
            long left = e.size;
            try (FileOutputStream os = new FileOutputStream(target)) {
                FileChannel out = os.getChannel();
                while (left > 0) {
                    buffer.clear();
                    if (left < buffer.capacity()) buffer.limit((int) left);
                    int n = in.read(buffer, position);
                    if (n <= 0) throw new IOException("disc image ends inside " + e.path);
                    buffer.flip();
                    while (buffer.hasRemaining()) out.write(buffer);
                    position += n;
                    left -= n;
                    done += n;
                    if (progress != null && !progress.onProgress(done, total, e.path)) {
                        throw new CancelledException();
                    }
                }
            }
        }
        if (progress != null) progress.onProgress(total, total, "");
    }

    private void walk(long sector, long size, String prefix, List<Entry> out) throws IOException {
        if (size == 0) return;
        if (size > (64L << 20)) throw new IOException("directory too large: " + prefix);
        ByteBuffer dir = ByteBuffer.allocate((int) size).order(ByteOrder.LITTLE_ENDIAN);
        if (readFully(dir, partition + sector * SECTOR) < size) {
            throw new IOException("disc image ends inside a directory: " + prefix);
        }
        walkTree(dir, 0, prefix, out, 0);
    }

    private void walkTree(ByteBuffer dir, int offset, String prefix, List<Entry> out, int depth)
            throws IOException {
        if (depth > 256) throw new IOException("directory tree too deep: " + prefix);
        if (offset + 14 > dir.capacity()) return;
        int left = dir.getShort(offset) & 0xFFFF;
        int right = dir.getShort(offset + 2) & 0xFFFF;
        if (left == 0xFFFF) return; // padding: an empty directory
        long start = dir.getInt(offset + 4) & 0xFFFFFFFFL;
        long length = dir.getInt(offset + 8) & 0xFFFFFFFFL;
        int attributes = dir.get(offset + 12) & 0xFF;
        int nameLength = dir.get(offset + 13) & 0xFF;
        if (offset + 14 + nameLength > dir.capacity()) throw new IOException("broken directory: " + prefix);
        byte[] nameBytes = new byte[nameLength];
        for (int i = 0; i < nameLength; i++) nameBytes[i] = dir.get(offset + 14 + i);
        String name = new String(nameBytes, StandardCharsets.ISO_8859_1);
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/")
                || name.contains("\\")) {
            throw new IOException("bad file name on disc: " + prefix + name);
        }

        if (left != 0) walkTree(dir, left * 4, prefix, out, depth + 1);
        Entry e = new Entry();
        e.path = prefix + name;
        e.directory = (attributes & ATTR_DIRECTORY) != 0;
        e.sector = start;
        e.size = length;
        out.add(e);
        if (e.directory) walk(start, length, e.path + "/", out);
        if (right != 0) walkTree(dir, right * 4, prefix, out, depth + 1);
    }

    private int readFully(ByteBuffer buffer, long position) throws IOException {
        int total = 0;
        while (buffer.hasRemaining()) {
            int n = in.read(buffer, position + total);
            if (n <= 0) break;
            total += n;
        }
        return total;
    }
}
