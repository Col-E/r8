// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import static com.android.tools.r8.utils.FunctionUtils.ignoreArgument;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class TraversalUtils {

  public static <BT, CT> BT getFirst(
      Function<Function<BT, TraversalContinuation<BT, CT>>, TraversalContinuation<BT, CT>>
          traversal) {
    return traversal.apply(TraversalContinuation::doBreak).asBreak().getValue();
  }

  public static <BT, CT> boolean isSingleton(
      Consumer<Function<CT, TraversalContinuation<BT, CT>>> traversal) {
    return isSizeExactly(traversal, 1);
  }

  public static <BT, CT> boolean isSizeExactly(
      Consumer<Function<CT, TraversalContinuation<BT, CT>>> traversal, int value) {
    IntBox counter = new IntBox();
    traversal.accept(
        ignoreArgument(() -> TraversalContinuation.breakIf(counter.incrementAndGet() > value)));
    return counter.get() == value;
  }

  public static <BT, CT> boolean isSizeGreaterThan(
      Consumer<Function<CT, TraversalContinuation<BT, CT>>> traversal, int value) {
    IntBox counter = new IntBox();
    traversal.accept(
        ignoreArgument(() -> TraversalContinuation.breakIf(counter.incrementAndGet() > value)));
    return counter.get() > value;
  }

  public static <S, BT, CT> TraversalContinuation<BT, CT> traverseIterable(
      Iterable<S> iterable,
      BiFunction<? super S, ? super CT, TraversalContinuation<BT, CT>> fn,
      CT initialValue) {
    TraversalContinuation<BT, CT> traversalContinuation =
        TraversalContinuation.doContinue(initialValue);
    for (S element : iterable) {
      traversalContinuation =
          fn.apply(element, traversalContinuation.asContinue().getValueOrDefault(null));
      if (traversalContinuation.isBreak()) {
        break;
      }
    }
    return traversalContinuation;
  }
}
