import org.gradle.kotlin.dsl.named

plugins {
    id("me.champeau.jmh")
}

jmh {
    includes.set(listOf("dev.alma.protobuf.serdes.bench.ProtobufDeserializerBenchmark"))
}

tasks.named("jmh") {
    group = "benchmark"
    description = "Runs JMH benchmarks for protobuf deserialization paths."
}
