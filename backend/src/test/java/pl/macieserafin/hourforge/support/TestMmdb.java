package pl.macieserafin.hourforge.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a tiny IPv4 location database in the MaxMind DB format (the format of DB-IP Lite and GeoLite2 files), so the
 * location lookup is tested against a real file without shipping a third-party database in the repository.
 */
public final class TestMmdb {
    /** A network such as "83.24.0.0/16" placed in a city (may be null) of a country. */
    public record Place(String network, String city, String country) {
    }

    private TestMmdb() {
    }

    public static Path write(Path file, List<Place> places) throws IOException {
        var data = new ByteArrayOutputStream();
        var root = new Node();
        for (var place : places) {
            int offset = data.size();
            data.writeBytes(encode(record(place)));
            insert(root, place.network(), offset);
        }
        var nodes = number(root);
        int nodeCount = nodes.size();
        var out = new ByteArrayOutputStream();
        for (var node : nodes) {
            for (int side = 0; side < 2; side++) {
                long value = node.children[side] != null ? nodes.indexOf(node.children[side])
                        : node.data[side] != null ? nodeCount + 16L + node.data[side] : nodeCount;
                out.write((int) (value >> 16) & 0xff);
                out.write((int) (value >> 8) & 0xff);
                out.write((int) value & 0xff);
            }
        }
        out.writeBytes(new byte[16]);
        out.writeBytes(data.toByteArray());
        out.writeBytes(new byte[] {(byte) 0xAB, (byte) 0xCD, (byte) 0xEF});
        out.writeBytes("MaxMind.com".getBytes(StandardCharsets.US_ASCII));
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("binary_format_major_version", new Uint16(2));
        metadata.put("binary_format_minor_version", new Uint16(0));
        metadata.put("build_epoch", new Uint64(1_700_000_000L));
        metadata.put("database_type", "HourForge-Test-City");
        metadata.put("description", Map.of("en", "Test locations"));
        metadata.put("ip_version", new Uint16(4));
        metadata.put("languages", List.of("en"));
        metadata.put("node_count", new Uint32(nodeCount));
        metadata.put("record_size", new Uint16(24));
        out.writeBytes(encode(metadata));
        return Files.write(file, out.toByteArray());
    }

    private static Map<String, Object> record(Place place) {
        var record = new LinkedHashMap<String, Object>();
        if (place.city() != null) record.put("city", Map.of("names", Map.of("en", place.city())));
        record.put("country", Map.of("names", Map.of("en", place.country())));
        return record;
    }

    private static final class Node {
        final Node[] children = new Node[2];
        final Integer[] data = new Integer[2];
    }

    private static void insert(Node root, String network, int offset) throws IOException {
        var parts = network.split("/");
        var bytes = InetAddress.getByName(parts[0]).getAddress();
        int prefix = Integer.parseInt(parts[1]);
        var node = root;
        for (int bit = 0; bit < prefix; bit++) {
            int side = (bytes[bit / 8] >> (7 - bit % 8)) & 1;
            if (bit == prefix - 1) {
                node.data[side] = offset;
            } else {
                if (node.children[side] == null) node.children[side] = new Node();
                node = node.children[side];
            }
        }
    }

    private static List<Node> number(Node root) {
        var nodes = new ArrayList<Node>();
        var queue = new ArrayDeque<Node>(List.of(root));
        while (!queue.isEmpty()) {
            var node = queue.poll();
            nodes.add(node);
            for (var child : node.children) if (child != null) queue.add(child);
        }
        return nodes;
    }

    private record Uint16(int value) {
    }

    private record Uint32(long value) {
    }

    private record Uint64(long value) {
    }

    private static byte[] encode(Object value) {
        var out = new ByteArrayOutputStream();
        switch (value) {
            case String text -> {
                var bytes = text.getBytes(StandardCharsets.UTF_8);
                control(out, 2, bytes.length);
                out.writeBytes(bytes);
            }
            case Map<?, ?> map -> {
                control(out, 7, map.size());
                map.forEach((key, item) -> { out.writeBytes(encode(key)); out.writeBytes(encode(item)); });
            }
            case List<?> list -> {
                control(out, 11, list.size());
                list.forEach(item -> out.writeBytes(encode(item)));
            }
            case Uint16 number -> unsigned(out, 5, number.value());
            case Uint32 number -> unsigned(out, 6, number.value());
            case Uint64 number -> unsigned(out, 9, number.value());
            default -> throw new IllegalArgumentException("Unsupported value " + value);
        }
        return out.toByteArray();
    }

    private static void unsigned(ByteArrayOutputStream out, int type, long value) {
        var bytes = new ByteArrayOutputStream();
        for (int shift = 56; shift >= 0; shift -= 8) {
            int part = (int) (value >> shift) & 0xff;
            if (part != 0 || bytes.size() > 0) bytes.write(part);
        }
        control(out, type, bytes.size());
        out.writeBytes(bytes.toByteArray());
    }

    /** Control byte(s): the type (extended types take a second byte) and the size, here always below 29. */
    private static void control(ByteArrayOutputStream out, int type, int size) {
        if (size >= 29) throw new IllegalArgumentException("Test values are small");
        if (type <= 7) {
            out.write((type << 5) | size);
        } else {
            out.write(size);
            out.write(type - 7);
        }
    }
}
