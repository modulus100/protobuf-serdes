package dev.alma.protobuf.serdes;

import java.io.ByteArrayOutputStream;

final class ConfluentProtobufWire {

    private ConfluentProtobufWire() {
    }

    static byte[] frame(byte[] protobufPayload, int schemaId, int... messageIndexes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        out.write((schemaId >>> 24) & 0xFF);
        out.write((schemaId >>> 16) & 0xFF);
        out.write((schemaId >>> 8) & 0xFF);
        out.write(schemaId & 0xFF);

        if (messageIndexes.length == 1 && messageIndexes[0] == 0) {
            writeUnsignedVarInt(out, 0);
        } else {
            writeUnsignedVarInt(out, encodeZigZag32(messageIndexes.length));
            for (int messageIndex : messageIndexes) {
                writeUnsignedVarInt(out, encodeZigZag32(messageIndex));
            }
        }

        out.writeBytes(protobufPayload);
        return out.toByteArray();
    }

    private static int encodeZigZag32(int value) {
        return (value << 1) ^ (value >> 31);
    }

    private static void writeUnsignedVarInt(ByteArrayOutputStream out, int value) {
        int remaining = value;
        while ((remaining & ~0x7F) != 0) {
            out.write((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        out.write(remaining);
    }
}
