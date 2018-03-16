// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.optimize.Inliner.Constraint;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
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

  /**
   * Returns the target of this field instruction, if such target is known, or null.
   * <p>
   * A result of null indicates that the field is either undefined or not of the right kind.
   */
  abstract DexEncodedField lookupTarget(DexType type, AppInfo appInfo);

  @Override
  public Constraint inliningConstraint(AppInfoWithLiveness info, DexType invocationContext) {
    // Resolve the field if possible and decide whether the instruction can inlined.
    DexType fieldHolder = field.getHolder();
    DexEncodedField target = lookupTarget(fieldHolder, info);
    DexClass fieldClass = info.definitionFor(fieldHolder);
    if ((target != null) && (fieldClass != null)) {
      Constraint fieldConstraint = Constraint
          .deriveConstraint(invocationContext, fieldHolder, target.accessFlags, info);
      Constraint classConstraint = Constraint
          .deriveConstraint(invocationContext, fieldHolder, fieldClass.accessFlags, info);
      return Constraint.min(fieldConstraint, classConstraint);
    }
    return Constraint.NEVER;
  }
}
