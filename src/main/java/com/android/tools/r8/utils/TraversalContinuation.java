// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.errors.Unreachable;

/** Two value continuation value to indicate the continuation of a loop/traversal. */
/* This class is used for building up api class member traversals. */
public abstract class TraversalContinuation<TB, TC> {

  public boolean isBreak() {
    return false;
  }

  public Break<TB, TC> asBreak() {
    return null;
  }

  public boolean isContinue() {
    return false;
  }

  public Continue<TB, TC> asContinue() {
    return null;
  }

  public static class Continue<TB, TC> extends TraversalContinuation<TB, TC> {
    private static final TraversalContinuation<?, ?> CONTINUE_NO_VALUE =
        new Continue<Object, Object>(null) {
          @Override
          public Object getValue() {
            return new Unreachable(
                "Invalid attempt at getting a value from a no-value continue state.");
          }
        };

    private final TC value;

    private Continue(TC value) {
      this.value = value;
    }

    public TC getValue() {
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
    private static final TraversalContinuation<?, ?> BREAK_NO_VALUE =
        new Break<Object, Object>(null) {
          @Override
          public Object getValue() {
            return new Unreachable(
                "Invalid attempt at getting a value from a no-value break state.");
          }
        };

    private final TB value;

    private Break(TB value) {
      this.value = value;
    }

    public TB getValue() {
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

  public static TraversalContinuation<?, ?> breakIf(boolean condition) {
    return continueIf(!condition);
  }

  public static TraversalContinuation<?, ?> continueIf(boolean condition) {
    return condition ? doContinue() : doBreak();
  }

  @SuppressWarnings("unchecked")
  public static <TB, TC> TraversalContinuation<TB, TC> doContinue() {
    return (TraversalContinuation<TB, TC>) Continue.CONTINUE_NO_VALUE;
  }

  public static <TB, TC> TraversalContinuation<TB, TC> doContinue(TC value) {
    return new Continue<>(value);
  }

  @SuppressWarnings("unchecked")
  public static <TB, TC> TraversalContinuation<TB, TC> doBreak() {
    return (TraversalContinuation<TB, TC>) Break.BREAK_NO_VALUE;
  }

  public static <TB, TC> TraversalContinuation<TB, TC> doBreak(TB value) {
    return new Break<>(value);
  }

  public final boolean shouldBreak() {
    return isBreak();
  }

  public final boolean shouldContinue() {
    return isContinue();
  }
}
