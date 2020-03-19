// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

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

  @Override
  public boolean isArrayAccess() {
    return true;
  }

  @Override
  public ArrayAccess asArrayAccess() {
    return this;
  }

  public abstract ArrayAccess withMemberType(MemberType newMemberType);
}
