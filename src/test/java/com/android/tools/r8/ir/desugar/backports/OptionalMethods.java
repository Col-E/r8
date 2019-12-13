// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class OptionalMethods {

  public static <T> Optional<T> or(
      Optional<T> receiver, Supplier<? extends Optional<? extends T>> supplier) {
    Objects.requireNonNull(supplier);
    if (receiver.isPresent()) {
      return receiver;
    } else {
      @SuppressWarnings("unchecked")
      Optional<T> r = (Optional<T>) supplier.get();
      return Objects.requireNonNull(r);
    }
  }

  public static <T> void ifPresentOrElse(
      Optional<T> receiver, Consumer<? super T> action, Runnable emptyAction) {
    if (receiver.isPresent()) {
      action.accept(receiver.get());
    } else {
      emptyAction.run();
    }
  }

  public static void ifPresentOrElseInt(
      OptionalInt receiver, IntConsumer action, Runnable emptyAction) {
    if (receiver.isPresent()) {
      action.accept(receiver.getAsInt());
    } else {
      emptyAction.run();
    }
  }

  public static void ifPresentOrElseLong(
      OptionalLong receiver, LongConsumer action, Runnable emptyAction) {
    if (receiver.isPresent()) {
      action.accept(receiver.getAsLong());
    } else {
      emptyAction.run();
    }
  }

  public static void ifPresentOrElseDouble(
      OptionalDouble receiver, DoubleConsumer action, Runnable emptyAction) {
    if (receiver.isPresent()) {
      action.accept(receiver.getAsDouble());
    } else {
      emptyAction.run();
    }
  }

  public static <T> Stream<T> stream(Optional<T> receiver) {
    if (receiver.isPresent()) {
      return Stream.of(receiver.get());
    } else {
      return Stream.empty();
    }
  }

  public static IntStream streamInt(OptionalInt receiver) {
    if (receiver.isPresent()) {
      return IntStream.of(receiver.getAsInt());
    } else {
      return IntStream.empty();
    }
  }

  public static LongStream streamLong(OptionalLong receiver) {
    if (receiver.isPresent()) {
      return LongStream.of(receiver.getAsLong());
    } else {
      return LongStream.empty();
    }
  }

  public static DoubleStream streamDouble(OptionalDouble receiver) {
    if (receiver.isPresent()) {
      return DoubleStream.of(receiver.getAsDouble());
    } else {
      return DoubleStream.empty();
    }
  }

  public static boolean isEmpty(Optional<?> receiver) {
    return !receiver.isPresent();
  }

  public static boolean isEmptyInt(OptionalInt receiver) {
    return !receiver.isPresent();
  }

  public static boolean isEmptyLong(OptionalLong receiver) {
    return !receiver.isPresent();
  }

  public static boolean isEmptyDouble(OptionalDouble receiver) {
    return !receiver.isPresent();
  }
}
