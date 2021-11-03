// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.utils.ConsumerUtils;
import java.util.List;
import java.util.function.Consumer;

public abstract class SyntheticStraightLineSourceCode implements SourceCode {

  private final List<Consumer<IRBuilder>> instructionBuilders;
  private final Position position;

  protected SyntheticStraightLineSourceCode(
      List<Consumer<IRBuilder>> instructionBuilders, Position position) {
    this.instructionBuilders = instructionBuilders;
    this.position = position;
  }

  @Override
  public int instructionCount() {
    return instructionBuilders.size();
  }

  @Override
  public int instructionIndex(int instructionOffset) {
    return instructionOffset;
  }

  @Override
  public int instructionOffset(int instructionIndex) {
    return instructionIndex;
  }

  @Override
  public void buildPrelude(IRBuilder builder) {
    int firstArgumentRegister = 0;
    builder.buildArgumentsWithRewrittenPrototypeChanges(
        firstArgumentRegister, builder.getMethod(), ConsumerUtils.emptyBiConsumer());
  }

  @Override
  public void buildInstruction(
      IRBuilder builder, int instructionIndex, boolean firstBlockInstruction) {
    instructionBuilders.get(instructionIndex).accept(builder);
  }

  @Override
  public void buildPostlude(IRBuilder builder) {
    // Intentionally empty.
  }

  @Override
  public void clear() {
    // Intentionally empty.
  }

  @Override
  public Position getCanonicalDebugPositionAtOffset(int offset) {
    return null;
  }

  @Override
  public CatchHandlers<Integer> getCurrentCatchHandlers(IRBuilder builder) {
    return null;
  }

  @Override
  public Position getCurrentPosition() {
    return position;
  }

  @Override
  public DebugLocalInfo getIncomingLocal(int register) {
    return null;
  }

  @Override
  public DebugLocalInfo getIncomingLocalAtBlock(int register, int blockOffset) {
    return null;
  }

  @Override
  public DebugLocalInfo getOutgoingLocal(int register) {
    return null;
  }

  @Override
  public void setUp() {
    // Intentionally empty.
  }

  @Override
  public int traceInstruction(int instructionIndex, IRBuilder builder) {
    // This instruction does not close the block.
    return -1;
  }

  @Override
  public boolean verifyCurrentInstructionCanThrow() {
    return true;
  }

  @Override
  public boolean verifyRegister(int register) {
    return true;
  }

  @Override
  public void buildBlockTransfer(
      IRBuilder builder, int predecessorOffset, int successorOffset, boolean isExceptional) {
    throw new Unreachable();
  }

  @Override
  public int getMoveExceptionRegister(int instructionIndex) {
    throw new Unreachable();
  }

  @Override
  public void resolveAndBuildNewArrayFilledData(
      int arrayRef, int payloadOffset, IRBuilder builder) {
    throw new Unreachable();
  }

  @Override
  public void resolveAndBuildSwitch(
      int value, int fallthroughOffset, int payloadOffset, IRBuilder builder) {
    throw new Unreachable();
  }

  @Override
  public boolean verifyLocalInScope(DebugLocalInfo local) {
    throw new Unreachable();
  }
}
