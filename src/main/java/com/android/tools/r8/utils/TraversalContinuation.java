// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.errors.Unreachable;

/** Two value continuation value to indicate the continuation of a loop/traversal. */
/* This class is used for building up api class member traversals. */
public abstract class TraversalContinuation<T> {

  public boolean isBreak() {
    return !isContinue();
  }

  public boolean isContinue() {
    return false;
  }

  public Break<T> asBreak() {
    return null;
  }

  public static final class Continue<T> extends TraversalContinuation<T> {
    private static final TraversalContinuation<?> CONTINUE = new Continue<Object>();

    private Continue() {}

    @Override
    public boolean isContinue() {
      return true;
    }
  }

  public static class Break<T> extends TraversalContinuation<T> {
    private static final TraversalContinuation<?> BREAK_NO_VALUE =
        new Break<Object>(null) {
          @Override
          public Object getValue() {
            return new Unreachable(
                "Invalid attempt at getting a value from a no-value break state.");
          }
        };

    private final T value;

    private Break(T value) {
      this.value = value;
    }

    public T getValue() {
      return value;
    }

    @Override
    public Break<T> asBreak() {
      return this;
    }
  }

  public static TraversalContinuation<?> breakIf(boolean condition) {
    return continueIf(!condition);
  }

  public static TraversalContinuation<?> continueIf(boolean condition) {
    return condition ? doContinue() : doBreak();
  }

  @SuppressWarnings("unchecked")
  public static <T> TraversalContinuation<T> doContinue() {
    return (TraversalContinuation<T>) Continue.CONTINUE;
  }

  public static TraversalContinuation<?> doBreak() {
    return Break.BREAK_NO_VALUE;
  }

  public static <T> TraversalContinuation<T> doBreak(T value) {
    return new Break<>(value);
  }

  public final boolean shouldBreak() {
    return isBreak();
  }

  public final boolean shouldContinue() {
    return isContinue();
  }
}
