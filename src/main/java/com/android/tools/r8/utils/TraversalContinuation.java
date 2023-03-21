// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.errors.Unreachable;
import java.util.function.Function;

/** Two value continuation value to indicate the continuation of a loop/traversal. */
/* This class is used for building up api class member traversals. */
public abstract class TraversalContinuation<TB, TC> {

  public boolean isBreak() {
    return false;
  }

  public Break<TB, TC> asBreak() {
    return null;
  }

  public Break<TB, TC> asBreakOrDefault(TB defaultValue) {
    Break<TB, TC> breakValue = asBreak();
    return breakValue == null ? doBreak(defaultValue) : breakValue;
  }

  public boolean isContinue() {
    return false;
  }

  public Continue<TB, TC> asContinue() {
    return null;
  }

  public <TBx, TCx> TraversalContinuation<TBx, TCx> map(
      Function<TB, TBx> mapBreak, Function<TC, TCx> mapContinue) {
    if (isBreak()) {
      return new Break<>(mapBreak.apply(asBreak().getValue()));
    } else {
      assert isContinue();
      return new Continue<>(mapContinue.apply(asContinue().getValue()));
    }
  }

  public static class Continue<TB, TC> extends TraversalContinuation<TB, TC> {
    private static final TraversalContinuation.Continue<?, ?> CONTINUE_NO_VALUE =
        new Continue<Object, Object>(null) {
          @Override
          public Object getValue() {
            return new Unreachable(
                "Invalid attempt at getting a value from a no-value continue state.");
          }

          @Override
          public Object getValueOrDefault(Object defaultValue) {
            return defaultValue;
          }
        };

    private final TC value;

    private Continue(TC value) {
      this.value = value;
    }

    public TC getValue() {
      return value;
    }

    public TC getValueOrDefault(TC defaultValue) {
      return value;
    }

    @Override
    public boolean isContinue() {
      return true;
    }

    @Override
    public Continue<TB, TC> asContinue() {
      return this;
    }
  }

  public static class Break<TB, TC> extends TraversalContinuation<TB, TC> {
    private static final TraversalContinuation.Break<?, ?> BREAK_NO_VALUE =
        new Break<Object, Object>(null) {
          @Override
          public Object getValue() {
            return new Unreachable(
                "Invalid attempt at getting a value from a no-value break state.");
          }

          @Override
          public Object getValueOrDefault(Object defaultValue) {
            return defaultValue;
          }
        };

    private final TB value;

    private Break(TB value) {
      this.value = value;
    }

    public TB getValue() {
      return value;
    }

    public TB getValueOrDefault(TB defaultValue) {
      return value;
    }

    @Override
    public boolean isBreak() {
      return true;
    }

    @Override
    public Break<TB, TC> asBreak() {
      return this;
    }
  }

  public static <TB, TC> TraversalContinuation<TB, TC> breakIf(boolean condition) {
    return continueIf(!condition);
  }

  public static <TB, TC> TraversalContinuation<TB, TC> continueIf(boolean condition) {
    return condition ? doContinue() : doBreak();
  }

  public TraversalContinuation<TB, TC> ifContinueThen(
      Function<TraversalContinuation.Continue<TB, TC>, TraversalContinuation<TB, TC>> fn) {
    return isContinue() ? fn.apply(asContinue()) : this;
  }

  @SuppressWarnings("unchecked")
  public static <TB, TC> TraversalContinuation.Continue<TB, TC> doContinue() {
    return (TraversalContinuation.Continue<TB, TC>) Continue.CONTINUE_NO_VALUE;
  }

  public static <TB, TC> TraversalContinuation.Continue<TB, TC> doContinue(TC value) {
    return new Continue<>(value);
  }

  @SuppressWarnings("unchecked")
  public static <TB, TC> TraversalContinuation.Break<TB, TC> doBreak() {
    return (TraversalContinuation.Break<TB, TC>) Break.BREAK_NO_VALUE;
  }

  public static <TB, TC> TraversalContinuation.Break<TB, TC> doBreak(TB value) {
    return new Break<>(value);
  }

  public final boolean shouldBreak() {
    return isBreak();
  }

  public final boolean shouldContinue() {
    return isContinue();
  }
}
