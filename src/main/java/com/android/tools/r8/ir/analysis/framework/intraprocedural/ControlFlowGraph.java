// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.framework.intraprocedural;

import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.TraversalUtils;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public interface ControlFlowGraph<Block, Instruction> {

  default boolean hasUniquePredecessor(Block block) {
    return TraversalUtils.isSingleton(counter -> traversePredecessors(block, counter));
  }

  default boolean hasUniqueSuccessor(Block block) {
    return TraversalUtils.isSingleton(counter -> traverseSuccessors(block, counter));
  }

  default boolean hasUniqueSuccessorWithUniquePredecessor(Block block) {
    return hasUniqueSuccessor(block) && hasUniquePredecessor(getUniqueSuccessor(block));
  }

  default Block getUniqueSuccessor(Block block) {
    assert hasUniqueSuccessor(block);
    return TraversalUtils.getFirst(collector -> traverseSuccessors(block, collector));
  }

  // Block traversal.

  default <BT, CT> TraversalContinuation<BT, CT> traversePredecessors(
      Block block, Function<? super Block, TraversalContinuation<BT, CT>> fn) {
    return traversePredecessors(block, (predecessor, ignore) -> fn.apply(predecessor), null);
  }

  default <BT, CT> TraversalContinuation<BT, CT> traverseNormalPredecessors(
      Block block, Function<? super Block, TraversalContinuation<BT, CT>> fn) {
    return traverseNormalPredecessors(block, (predecessor, ignore) -> fn.apply(predecessor), null);
  }

  default <BT, CT> TraversalContinuation<BT, CT> traverseExceptionalPredecessors(
      Block block, Function<? super Block, TraversalContinuation<BT, CT>> fn) {
    return traverseExceptionalPredecessors(
        block, (predecessor, ignore) -> fn.apply(predecessor), null);
  }

  default <BT, CT> TraversalContinuation<BT, CT> traverseSuccessors(
      Block block, Function<? super Block, TraversalContinuation<BT, CT>> fn) {
    return traverseSuccessors(block, (successor, ignore) -> fn.apply(successor), null);
  }

  default <BT, CT> TraversalContinuation<BT, CT> traverseNormalSuccessors(
      Block block, Function<? super Block, TraversalContinuation<BT, CT>> fn) {
    return traverseNormalSuccessors(block, (successor, ignore) -> fn.apply(successor), null);
  }

  default <BT, CT> TraversalContinuation<BT, CT> traverseExceptionalSuccessors(
      Block block, Function<? super Block, TraversalContinuation<BT, CT>> fn) {
    return traverseExceptionalSuccessors(block, (successor, ignore) -> fn.apply(successor), null);
  }

  // Block traversal with result.

  default <BT, CT> TraversalContinuation<BT, CT> traversePredecessors(
      Block block,
      BiFunction<? super Block, ? super CT, TraversalContinuation<BT, CT>> fn,
      CT initialValue) {
    return traverseNormalPredecessors(block, fn, initialValue)
        .ifContinueThen(
            continuation ->
                traverseExceptionalPredecessors(block, fn, continuation.getValueOrDefault(null)));
  }

  <BT, CT> TraversalContinuation<BT, CT> traverseNormalPredecessors(
      Block block,
      BiFunction<? super Block, ? super CT, TraversalContinuation<BT, CT>> fn,
      CT initialValue);

  <BT, CT> TraversalContinuation<BT, CT> traverseExceptionalPredecessors(
      Block block,
      BiFunction<? super Block, ? super CT, TraversalContinuation<BT, CT>> fn,
      CT initialValue);

  default <BT, CT> TraversalContinuation<BT, CT> traverseSuccessors(
      Block block,
      BiFunction<? super Block, ? super CT, TraversalContinuation<BT, CT>> fn,
      CT initialValue) {
    return traverseNormalSuccessors(block, fn, initialValue)
        .ifContinueThen(
            continuation ->
                traverseExceptionalSuccessors(block, fn, continuation.getValueOrDefault(null)));
  }

  <BT, CT> TraversalContinuation<BT, CT> traverseNormalSuccessors(
      Block block,
      BiFunction<? super Block, ? super CT, TraversalContinuation<BT, CT>> fn,
      CT initialValue);

  <BT, CT> TraversalContinuation<BT, CT> traverseExceptionalSuccessors(
      Block block,
      BiFunction<? super Block, ? super CT, TraversalContinuation<BT, CT>> fn,
      CT initialValue);

  // Block iteration.

  default void forEachPredecessor(Block block, Consumer<Block> consumer) {
    forEachNormalPredecessor(block, consumer);
    forEachExceptionalPredecessor(block, consumer);
  }

  default void forEachNormalPredecessor(Block block, Consumer<Block> consumer) {
    traverseNormalPredecessors(
        block,
        predecessor -> {
          consumer.accept(predecessor);
          return TraversalContinuation.doContinue();
        });
  }

  default void forEachExceptionalPredecessor(Block block, Consumer<Block> consumer) {
    traverseExceptionalPredecessors(
        block,
        exceptionalPredecessor -> {
          consumer.accept(exceptionalPredecessor);
          return TraversalContinuation.doContinue();
        });
  }

  default void forEachSuccessor(Block block, Consumer<Block> consumer) {
    forEachNormalSuccessor(block, consumer);
    forEachExceptionalSuccessor(block, consumer);
  }

  default void forEachNormalSuccessor(Block block, Consumer<Block> consumer) {
    traverseNormalSuccessors(
        block,
        successor -> {
          consumer.accept(successor);
          return TraversalContinuation.doContinue();
        });
  }

  default void forEachExceptionalSuccessor(Block block, Consumer<Block> consumer) {
    traverseExceptionalSuccessors(
        block,
        exceptionalSuccessor -> {
          consumer.accept(exceptionalSuccessor);
          return TraversalContinuation.doContinue();
        });
  }

  // Instruction traversal.

  <BT, CT> TraversalContinuation<BT, CT> traverseInstructions(
      Block block, BiFunction<Instruction, CT, TraversalContinuation<BT, CT>> fn, CT initialValue);
}
