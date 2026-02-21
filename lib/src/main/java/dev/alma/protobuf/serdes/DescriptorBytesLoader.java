package dev.alma.protobuf.serdes;

@FunctionalInterface
interface DescriptorBytesLoader extends AutoCloseable {

    byte[] load(String subject, int version);

    @Override
    default void close() {
    }
}
