// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.util.stream;

import static java.util.ConversionRuntimeException.exception;

import java.util.function.Function;

public class StackWalkerApiFlips {

  public static Function<?, ?> flipFunctionStream(Function<?, ?> stackWalker) {
    return new FunctionStreamWrapper<>(stackWalker);
  }

  public static class FunctionStreamWrapper<T, R> implements Function<T, R> {

    public Function<T, R> function;

    public FunctionStreamWrapper(Function<T, R> function) {
      this.function = function;
    }

    private T flipStream(T maybeStream) {
      if (maybeStream == null) {
        return null;
      }
      if (maybeStream instanceof java.util.stream.Stream<?>) {
        return (T)
            j$.util.stream.Stream.inverted_wrap_convert((java.util.stream.Stream<?>) maybeStream);
      }
      if (maybeStream instanceof j$.util.stream.Stream<?>) {
        return (T)
            j$.util.stream.Stream.inverted_wrap_convert((j$.util.stream.Stream<?>) maybeStream);
      }
      throw exception("java.util.stream.Stream", maybeStream.getClass());
    }

    public R apply(T arg) {
      return function.apply(flipStream(arg));
    }
  }
}
