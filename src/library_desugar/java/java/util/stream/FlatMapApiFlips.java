// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.util.stream;

import static java.util.ConversionRuntimeException.exception;

import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongFunction;

public class FlatMapApiFlips {

  public static Function<?, ?> flipFunctionReturningStream(Function<?, ?> function) {
    return new FunctionStreamWrapper<>(function);
  }

  public static IntFunction<?> flipFunctionReturningStream(IntFunction<?> function) {
    return new IntFunctionStreamWrapper<>(function);
  }

  public static DoubleFunction<?> flipFunctionReturningStream(DoubleFunction<?> function) {
    return new DoubleFunctionStreamWrapper<>(function);
  }

  public static LongFunction<?> flipFunctionReturningStream(LongFunction<?> function) {
    return new LongFunctionStreamWrapper<>(function);
  }

  public static class FunctionStreamWrapper<T, R> implements Function<T, R> {

    public Function<T, R> function;

    public FunctionStreamWrapper(Function<T, R> function) {
      this.function = function;
    }

    @SuppressWarnings("unchecked")
    private R flipStream(R maybeStream) {
      if (maybeStream == null) {
        return null;
      }

      if (maybeStream instanceof java.util.stream.Stream<?>) {
        return (R) j$.util.stream.Stream.wrap_convert((java.util.stream.Stream<?>) maybeStream);
      }
      if (maybeStream instanceof j$.util.stream.Stream<?>) {
        return (R) j$.util.stream.Stream.wrap_convert((j$.util.stream.Stream<?>) maybeStream);
      }

      if (maybeStream instanceof java.util.stream.IntStream) {
        return (R) j$.util.stream.IntStream.wrap_convert((java.util.stream.IntStream) maybeStream);
      }
      if (maybeStream instanceof j$.util.stream.IntStream) {
        return (R) j$.util.stream.IntStream.wrap_convert((j$.util.stream.IntStream) maybeStream);
      }

      if (maybeStream instanceof java.util.stream.DoubleStream) {
        return (R)
            j$.util.stream.DoubleStream.wrap_convert((java.util.stream.DoubleStream) maybeStream);
      }
      if (maybeStream instanceof j$.util.stream.DoubleStream) {
        return (R)
            j$.util.stream.DoubleStream.wrap_convert((j$.util.stream.DoubleStream) maybeStream);
      }

      if (maybeStream instanceof java.util.stream.LongStream) {
        return (R)
            j$.util.stream.LongStream.wrap_convert((java.util.stream.LongStream) maybeStream);
      }
      if (maybeStream instanceof j$.util.stream.LongStream) {
        return (R) j$.util.stream.LongStream.wrap_convert((j$.util.stream.LongStream) maybeStream);
      }

      throw exception("java.util.stream.*Stream", maybeStream.getClass());
    }

    public R apply(T arg) {
      return flipStream(function.apply(arg));
    }
  }

  public static class IntFunctionStreamWrapper<R> implements IntFunction<R> {

    public IntFunction<R> function;

    public IntFunctionStreamWrapper(IntFunction<R> function) {
      this.function = function;
    }

    @SuppressWarnings("unchecked")
    private R flipStream(R maybeStream) {
      if (maybeStream == null) {
        return null;
      }
      if (maybeStream instanceof java.util.stream.IntStream) {
        return (R) j$.util.stream.IntStream.wrap_convert((java.util.stream.IntStream) maybeStream);
      }
      if (maybeStream instanceof j$.util.stream.IntStream) {
        return (R) j$.util.stream.IntStream.wrap_convert((j$.util.stream.IntStream) maybeStream);
      }
      throw exception("java.util.stream.IntStream", maybeStream.getClass());
    }

    public R apply(int arg) {
      return flipStream(function.apply(arg));
    }
  }

  public static class DoubleFunctionStreamWrapper<R> implements DoubleFunction<R> {

    public DoubleFunction<R> function;

    public DoubleFunctionStreamWrapper(DoubleFunction<R> function) {
      this.function = function;
    }

    @SuppressWarnings("unchecked")
    private R flipStream(R maybeStream) {
      if (maybeStream == null) {
        return null;
      }
      if (maybeStream instanceof java.util.stream.DoubleStream) {
        return (R)
            j$.util.stream.DoubleStream.wrap_convert((java.util.stream.DoubleStream) maybeStream);
      }
      if (maybeStream instanceof j$.util.stream.DoubleStream) {
        return (R)
            j$.util.stream.DoubleStream.wrap_convert((j$.util.stream.DoubleStream) maybeStream);
      }
      throw exception("java.util.stream.DoubleStream", maybeStream.getClass());
    }

    public R apply(double arg) {
      return flipStream(function.apply(arg));
    }
  }

  public static class LongFunctionStreamWrapper<R> implements LongFunction<R> {

    public LongFunction<R> function;

    public LongFunctionStreamWrapper(LongFunction<R> function) {
      this.function = function;
    }

    @SuppressWarnings("unchecked")
    private R flipStream(R maybeStream) {
      if (maybeStream == null) {
        return null;
      }
      if (maybeStream instanceof java.util.stream.LongStream) {
        return (R)
            j$.util.stream.LongStream.wrap_convert((java.util.stream.LongStream) maybeStream);
      }
      if (maybeStream instanceof j$.util.stream.LongStream) {
        return (R) j$.util.stream.LongStream.wrap_convert((j$.util.stream.LongStream) maybeStream);
      }
      throw exception("java.util.stream.LongStream", maybeStream.getClass());
    }

    public R apply(long arg) {
      return flipStream(function.apply(arg));
    }
  }
}
