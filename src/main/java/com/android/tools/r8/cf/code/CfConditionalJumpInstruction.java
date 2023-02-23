// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code;

import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;

public abstract class CfConditionalJumpInstruction extends CfJumpInstruction {

  final IfType kind;
  final ValueType type;
  final CfLabel target;

  CfConditionalJumpInstruction(IfType kind, ValueType type, CfLabel target) {
    this.kind = kind;
    this.type = type;
    this.target = target;
  }

  @Override
  public final int bytecodeSizeUpperBound() {
    return 3;
  }

  @Override
  public final ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    return inliningConstraints.forJumpInstruction();
  }

  @Override
  public final boolean isConditionalJump() {
    return true;
  }

  @Override
  public final boolean isJumpWithNormalTarget() {
    return true;
  }

  public final IfType getKind() {
    return kind;
  }

  @Override
  public final CfLabel getTarget() {
    return target;
  }

  public final ValueType getType() {
    return type;
  }

  @Override
  public final boolean hasFallthrough() {
    return true;
  }
}
