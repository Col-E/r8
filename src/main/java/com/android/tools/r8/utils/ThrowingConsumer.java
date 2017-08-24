package com.android.tools.r8.utils;

import java.util.function.Consumer;

/**
 * Similar to a {@link Consumer} but throws a single {@link Throwable}.
 *
 * @param <T> the type of the input
 * @param <E> the type of the {@link Throwable}
 */
@FunctionalInterface
public interface ThrowingConsumer<T, E extends Throwable> {
  void accept(T t) throws E;
}
