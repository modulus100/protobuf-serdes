package dev.alma.protobuf.serdes.bench;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import com.google.protobuf.StringValue;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 4, time = 1)
@Fork(1)
public class ProtobufDeserializerBenchmark {

    private byte[] payload;
    private Parser<StringValue> parser;
    private Method reflectiveParseFrom;
    private MethodHandle methodHandleParseFrom;

    @Setup
    public void setup() throws Throwable {
        payload = StringValue.of("benchmark-payload").toByteArray();
        parser = StringValue.parser();
        reflectiveParseFrom = StringValue.class.getMethod("parseFrom", byte[].class);
        methodHandleParseFrom = MethodHandles.lookup()
            .findStatic(StringValue.class, "parseFrom", MethodType.methodType(StringValue.class, byte[].class));
    }

    @Benchmark
    public void directParser(Blackhole blackhole) throws InvalidProtocolBufferException {
        blackhole.consume(parser.parseFrom(payload));
    }

    @Benchmark
    public void reflectionPerMessage(Blackhole blackhole) throws Throwable {
        blackhole.consume(reflectiveParseFrom.invoke(null, payload));
    }

    @Benchmark
    public void methodHandlePerMessage(Blackhole blackhole) throws Throwable {
        blackhole.consume((StringValue) methodHandleParseFrom.invokeExact(payload));
    }
}
