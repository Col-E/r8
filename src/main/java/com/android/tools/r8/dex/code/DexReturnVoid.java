// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;

public class DexReturnVoid extends DexFormat10x {

  public static final int OPCODE = 0xe;
  public static final String NAME = "ReturnVoid";
  public static final String SMALI_NAME = "return-void";

  DexReturnVoid(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public DexReturnVoid() {}

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getSmaliName() {
    return SMALI_NAME;
  }

  @Override
  public int getOpcode() {
    return OPCODE;
  }

  @Override
  final int internalAcceptCompareTo(DexInstruction other, CompareToVisitor visitor) {
    return DexCompareHelper.compareIdUniquelyDeterminesEquality(this, other);
  }

  @Override
  final void internalAcceptHashing(HashingVisitor visitor) {
    // Nothing to hash except the compare-id.
  }

  @Override
  public int hashCode() {
    return NAME.hashCode();
  }

  @Override
  public int[] getTargets() {
    return EXIT_TARGET;
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addReturn();
  }
}
