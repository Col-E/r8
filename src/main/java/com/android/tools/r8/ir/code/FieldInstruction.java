// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.DexField;
import java.util.List;

public abstract class FieldInstruction extends Instruction {

  protected final MemberType type;
  protected final DexField field;

  protected FieldInstruction(MemberType type, DexField field, Value dest, Value value) {
    super(dest, value);
    assert type != null;
    assert field != null;
    this.type = type;
    this.field = field;
  }

  protected FieldInstruction(MemberType type, DexField field, Value dest, List<Value> inValues) {
    super(dest, inValues);
    assert type != null;
    assert field != null;
    this.type = type;
    this.field = field;
  }

  public MemberType getType() {
    return type;
  }

  public DexField getField() {
    return field;
  }

  @Override
  public boolean isFieldInstruction() {
    return true;
  }

  @Override
  public FieldInstruction asFieldInstruction() {
    return this;
  }

  @Override
  public boolean hasInvariantOutType() {
    // TODO(jsjeon): what if the target field is known to be non-null?
    return true;
  }
}
