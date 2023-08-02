// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import java.util.List;

public abstract class ArrayAccess extends Instruction implements ImpreciseMemberTypeInstruction {

  // Input values are ordered according to the stack order of the Java bytecodes.
  private static final int ARRAY_INDEX = 0;
  private static final int INDEX_INDEX = 1;

  ArrayAccess(Value outValue, List<? extends Value> inValues) {
    super(outValue, inValues);
  }

  public Value array() {
    return inValues.get(ARRAY_INDEX);
  }

  public Value index() {
    return inValues.get(INDEX_INDEX);
  }

  public int getIndexOrDefault(int defaultValue) {
    return index().isConstant()
        ? index().getConstInstruction().asConstInstruction().asConstNumber().getIntValue()
        : defaultValue;
  }

  @Override
  public boolean isArrayAccess() {
    return true;
  }

  @Override
  public ArrayAccess asArrayAccess() {
    return this;
  }

  public abstract ArrayAccess withMemberType(MemberType newMemberType);

  @Override
  public boolean instructionTypeCanThrow() {
    return true;
  }

  @Override
  public boolean instructionInstanceCanThrow(AppView<?> appView, ProgramMethod context) {
    // TODO(b/203731608): Add parameters to the method and use abstract value in R8.
    if (index().isConstant() && !array().isPhi() && array().definition.isNewArrayEmpty()) {
      Value newArraySizeValue = array().definition.asNewArrayEmpty().size();
      if (newArraySizeValue.isConstant()) {
        int newArraySize = newArraySizeValue.getConstInstruction().asConstNumber().getIntValue();
        int index = index().getConstInstruction().asConstNumber().getIntValue();
        return newArraySize <= 0 || index < 0 || newArraySize <= index;
      }
    }
    return true;
  }
}
