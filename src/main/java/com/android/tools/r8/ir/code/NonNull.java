// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;

public class NonNull extends Instruction {
  private final static String ERROR_MESSAGE = "This fake IR should be removed after inlining.";

  final Instruction origin;

  public NonNull(Value dest, Value src, Instruction origin) {
    super(dest, src);
    assert !src.isNeverNull();
    dest.markNeverNull();
    this.origin = origin;
  }

  public Value dest() {
    return outValue;
  }

  public Value src() {
    return inValues.get(0);
  }

  public Instruction origin() {
    return origin;
  }

  @Override
  public boolean isNonNull() {
    return true;
  }

  @Override
  public NonNull asNonNull() {
    return this;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    throw new Unreachable(ERROR_MESSAGE);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    throw new Unreachable(ERROR_MESSAGE);
  }

  @Override
  public int maxInValueRegister() {
    throw new Unreachable(ERROR_MESSAGE);
  }

  @Override
  public int maxOutValueRegister() {
    throw new Unreachable(ERROR_MESSAGE);
  }

  @Override
  public boolean isOutConstant() {
    return false;
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isNonNull();
  }

  @Override
  public int compareNonValueParts(Instruction other) {
    assert other instanceof NonNull;
    return 0;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, DexType invocationContext) {
    return inliningConstraints.forNonNull();
  }

  @Override
  public TypeLatticeElement evaluate(AppInfo appInfo) {
    TypeLatticeElement l = src().getTypeLatticeRaw();
    // Flipping the nullability bit for reference type is the main use case.
    if (l.isClassTypeLatticeElement() || l.isArrayTypeLatticeElement()) {
      return l.asNonNullable();
    }
    // non_null_rcv <- non-null NULL ?!
    // The chances are that the in is phi, and the only available operand is null. If another
    // operand is, say, class A, phi's type is nullable A, and the out value of this instruction
    // would be non-null A. Until that phi is saturated, we will ignore the current null.
    if (l.mustBeNull()) {
      return l.asNonNullable();
    }
    return l;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    throw new Unreachable(ERROR_MESSAGE);
  }
}
