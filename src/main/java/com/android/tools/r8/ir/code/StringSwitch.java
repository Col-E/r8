// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.utils.ThrowingBiConsumer;
import com.android.tools.r8.utils.ThrowingConsumer;

public class StringSwitch extends Switch {

  private final DexString[] keys;

  public StringSwitch(
      Value value, DexString[] keys, int[] targetBlockIndices, int fallthroughBlockIndex) {
    super(value, targetBlockIndices, fallthroughBlockIndex);
    this.keys = keys;
    assert valid();
  }

  public DexString getFirstKey() {
    return keys[0];
  }

  @Override
  public int opcode() {
    return Opcodes.STRING_SWITCH;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public <E extends Throwable> void forEachKey(ThrowingConsumer<DexString, E> fn) throws E {
    for (DexString key : keys) {
      fn.accept(key);
    }
  }

  public <E extends Throwable> void forEachCase(ThrowingBiConsumer<DexString, BasicBlock, E> fn)
      throws E {
    for (int i = 0; i < keys.length; i++) {
      fn.accept(getKey(i), targetBlock(i));
    }
  }

  @Override
  public Instruction materializeFirstKey(AppView<?> appView, IRCode code) {
    return code.createStringConstant(appView, getFirstKey());
  }

  @Override
  public boolean valid() {
    assert super.valid();
    assert keys.length >= 1;
    assert keys.length <= Constants.U16BIT_MAX;
    assert keys.length == numberOfKeys();
    return true;
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

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder(super.toString()).append(System.lineSeparator());
    for (int i = 0; i < numberOfKeys(); i++) {
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
  public void buildLir(LirBuilder<Value, ?> builder) {
    throw new Unreachable();
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
