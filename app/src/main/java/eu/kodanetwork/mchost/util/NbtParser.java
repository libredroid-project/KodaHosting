package eu.kodanetwork.mchost.util;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class NbtParser {

    public static class NbtTag {
        public byte type;
        public String name;
        public Object value;

        public NbtTag(byte type, String name, Object value) {
            this.type = type;
            this.name = name;
            this.value = value;
        }
    }

    public static Map<String, Object> parsePlayerDat(File file) {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new FileInputStream(file)))) {
            byte rootType = in.readByte();
            if (rootType == 0) return null;
            String rootName = readString(in);
            return (Map<String, Object>) readTagValue(in, rootType);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Object readTagValue(DataInputStream in, byte type) throws IOException {
        switch (type) {
            case 1: return in.readByte(); // Byte
            case 2: return in.readShort(); // Short
            case 3: return in.readInt(); // Int
            case 4: return in.readLong(); // Long
            case 5: return in.readFloat(); // Float
            case 6: return in.readDouble(); // Double
            case 7: { // ByteArray
                int len = in.readInt();
                byte[] bytes = new byte[len];
                in.readFully(bytes);
                return bytes;
            }
            case 8: return readString(in); // String
            case 9: { // List
                byte listType = in.readByte();
                int len = in.readInt();
                List<Object> list = new ArrayList<>(len);
                for (int i = 0; i < len; i++) {
                    list.add(readTagValue(in, listType));
                }
                return list; // Note: We lose the listType, but we usually know what to expect.
            }
            case 10: { // Compound
                Map<String, Object> map = new HashMap<>();
                while (true) {
                    byte childType = in.readByte();
                    if (childType == 0) break; // End
                    String name = readString(in);
                    map.put(name, readTagValue(in, childType));
                }
                return map;
            }
            case 11: { // IntArray
                int len = in.readInt();
                int[] ints = new int[len];
                for (int i = 0; i < len; i++) ints[i] = in.readInt();
                return ints;
            }
            case 12: { // LongArray
                int len = in.readInt();
                long[] longs = new long[len];
                for (int i = 0; i < len; i++) longs[i] = in.readLong();
                return longs;
            }
            default:
                throw new IOException("Unknown NBT type: " + type);
        }
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = in.readUnsignedShort();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, "UTF-8");
    }
}
