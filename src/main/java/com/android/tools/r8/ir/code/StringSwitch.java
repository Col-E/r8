// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import java.util.function.BiConsumer;

public class StringSwitch extends JumpInstruction {

  private final DexString[] keys;
  private final int[] targetBlockIndices;
  private final int fallthroughBlockIndex;

  public StringSwitch(
      Value value, DexString[] keys, int[] targetBlockIndices, int fallthroughBlockIndex) {
    super(null, value);
    this.keys = keys;
    this.targetBlockIndices = targetBlockIndices;
    this.fallthroughBlockIndex = fallthroughBlockIndex;
    assert valid();
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public void forEachCase(BiConsumer<DexString, BasicBlock> fn) {
    for (int i = 0; i < keys.length; i++) {
      fn.accept(getKey(i), targetBlock(i));
    }
  }

  private boolean valid() {
    assert keys.length >= 1;
    assert keys.length <= Constants.U16BIT_MAX;
    assert keys.length == targetBlockIndices.length;
    for (int i = 1; i < keys.length - 1; i++) {
      assert targetBlockIndices[i] != fallthroughBlockIndex;
    }
    assert targetBlockIndices[keys.length - 1] != fallthroughBlockIndex;
    return true;
  }

  public int size() {
    return keys.length;
  }

  public Value value() {
    return inValues.get(0);
  }

  @Override
  public boolean isStringSwitch() {
    return true;
  }

  @Override
  public StringSwitch asStringSwitch() {
    return this;
  }

  public DexString getKey(int index) {
    return keys[index];
  }

  public BasicBlock targetBlock(int index) {
    return getBlock().getSuccessors().get(targetBlockIndices[index]);
  }

  @Override
  public BasicBlock fallthroughBlock() {
    return getBlock().getSuccessors().get(fallthroughBlockIndex);
  }

  @Override
  public void setFallthroughBlock(BasicBlock block) {
    getBlock().getMutableSuccessors().set(fallthroughBlockIndex, block);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder(super.toString()).append(System.lineSeparator());
    for (int i = 0; i < size(); i++) {
      builder
          .append("          \"")
          .append(getKey(i))
          .append("\" -> ")
          .append(targetBlock(i).getNumberAsString())
          .append(System.lineSeparator());
    }
    return builder.append("          F -> ").append(fallthroughBlock().getNumber()).toString();
  }

  @Override
  public void buildCf(CfBuilder builder) {
    throw new Unreachable();
  }

  @Override
  public void buildDex(DexBuilder builder) {
    throw new Unreachable();
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    throw new Unreachable();
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    throw new Unreachable();
  }

  @Override
  public int maxInValueRegister() {
    throw new Unreachable();
  }

  @Override
  public int maxOutValueRegister() {
    throw new Unreachable();
  }
}
