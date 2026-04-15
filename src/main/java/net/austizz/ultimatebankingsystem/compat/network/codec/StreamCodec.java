package net.austizz.ultimatebankingsystem.compat.network.codec;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Minimal 1.21-style StreamCodec shim for 1.20.1.
 */
public class StreamCodec<B, T> {
    private final BiConsumer<B, T> encoder;
    private final Function<B, T> decoder;

    private StreamCodec(BiConsumer<B, T> encoder, Function<B, T> decoder) {
        this.encoder = encoder;
        this.decoder = decoder;
    }

    public static <B, T> StreamCodec<B, T> of(BiConsumer<B, T> encoder, Function<B, T> decoder) {
        return new StreamCodec<>(encoder, decoder);
    }

    public void encode(B buf, T value) {
        encoder.accept(buf, value);
    }

    public T decode(B buf) {
        return decoder.apply(buf);
    }

    public <R> StreamCodec<B, R> apply(Function<StreamCodec<B, T>, StreamCodec<B, R>> applier) {
        return applier.apply(this);
    }

    @SuppressWarnings("unchecked")
    private static <B, T> void encodeWith(StreamCodec<? super B, T> codec, B buf, T value) {
        ((StreamCodec<B, T>) codec).encode(buf, value);
    }

    @SuppressWarnings("unchecked")
    private static <B, T> T decodeWith(StreamCodec<? super B, T> codec, B buf) {
        return ((StreamCodec<B, T>) codec).decode(buf);
    }

    public static <B, C, T1> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1,
            Function<C, T1> g1,
            Function<T1, C> ctor
    ) {
        return of(
                (buf, value) -> encodeWith(c1, buf, g1.apply(value)),
                buf -> ctor.apply(decodeWith(c1, buf))
        );
    }

    public static <B, C, T1, T2> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2,
            BiFunction<T1, T2, C> ctor
    ) {
        return of(
                (buf, value) -> {
                    encodeWith(c1, buf, g1.apply(value));
                    encodeWith(c2, buf, g2.apply(value));
                },
                buf -> ctor.apply(
                        decodeWith(c1, buf),
                        decodeWith(c2, buf)
                )
        );
    }

    public static <B, C, T1, T2, T3> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2,
            StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            Function3<T1, T2, T3, C> ctor
    ) {
        return of(
                (buf, value) -> {
                    encodeWith(c1, buf, g1.apply(value));
                    encodeWith(c2, buf, g2.apply(value));
                    encodeWith(c3, buf, g3.apply(value));
                },
                buf -> ctor.apply(
                        decodeWith(c1, buf),
                        decodeWith(c2, buf),
                        decodeWith(c3, buf)
                )
        );
    }

    public static <B, C, T1, T2, T3, T4> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2,
            StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            StreamCodec<? super B, T4> c4, Function<C, T4> g4,
            Function4<T1, T2, T3, T4, C> ctor
    ) {
        return of(
                (buf, value) -> {
                    encodeWith(c1, buf, g1.apply(value));
                    encodeWith(c2, buf, g2.apply(value));
                    encodeWith(c3, buf, g3.apply(value));
                    encodeWith(c4, buf, g4.apply(value));
                },
                buf -> ctor.apply(
                        decodeWith(c1, buf),
                        decodeWith(c2, buf),
                        decodeWith(c3, buf),
                        decodeWith(c4, buf)
                )
        );
    }

    public static <B, C, T1, T2, T3, T4, T5> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2,
            StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            StreamCodec<? super B, T4> c4, Function<C, T4> g4,
            StreamCodec<? super B, T5> c5, Function<C, T5> g5,
            Function5<T1, T2, T3, T4, T5, C> ctor
    ) {
        return of(
                (buf, value) -> {
                    encodeWith(c1, buf, g1.apply(value));
                    encodeWith(c2, buf, g2.apply(value));
                    encodeWith(c3, buf, g3.apply(value));
                    encodeWith(c4, buf, g4.apply(value));
                    encodeWith(c5, buf, g5.apply(value));
                },
                buf -> ctor.apply(
                        decodeWith(c1, buf),
                        decodeWith(c2, buf),
                        decodeWith(c3, buf),
                        decodeWith(c4, buf),
                        decodeWith(c5, buf)
                )
        );
    }

    public static <B, C, T1, T2, T3, T4, T5, T6> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2,
            StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            StreamCodec<? super B, T4> c4, Function<C, T4> g4,
            StreamCodec<? super B, T5> c5, Function<C, T5> g5,
            StreamCodec<? super B, T6> c6, Function<C, T6> g6,
            Function6<T1, T2, T3, T4, T5, T6, C> ctor
    ) {
        return of(
                (buf, value) -> {
                    encodeWith(c1, buf, g1.apply(value));
                    encodeWith(c2, buf, g2.apply(value));
                    encodeWith(c3, buf, g3.apply(value));
                    encodeWith(c4, buf, g4.apply(value));
                    encodeWith(c5, buf, g5.apply(value));
                    encodeWith(c6, buf, g6.apply(value));
                },
                buf -> ctor.apply(
                        decodeWith(c1, buf),
                        decodeWith(c2, buf),
                        decodeWith(c3, buf),
                        decodeWith(c4, buf),
                        decodeWith(c5, buf),
                        decodeWith(c6, buf)
                )
        );
    }

    @FunctionalInterface
    public interface Function3<A, B, C, R> {
        R apply(A a, B b, C c);
    }

    @FunctionalInterface
    public interface Function4<A, B, C, D, R> {
        R apply(A a, B b, C c, D d);
    }

    @FunctionalInterface
    public interface Function5<A, B, C, D, E, R> {
        R apply(A a, B b, C c, D d, E e);
    }

    @FunctionalInterface
    public interface Function6<A, B, C, D, E, F, R> {
        R apply(A a, B b, C c, D d, E e, F f);
    }
}
