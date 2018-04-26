// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfMultiANewArray;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.Constraint;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import java.util.List;
import java.util.function.Function;

public class InvokeMultiNewArray extends Invoke {

  private final DexType type;

  public InvokeMultiNewArray(DexType type, Value result, List<Value> arguments) {
    super(result, arguments);
    this.type = type;
  }

  @Override
  public boolean isInvokeMultiNewArray() {
    return true;
  }

  @Override
  public InvokeMultiNewArray asInvokeMultiNewArray() {
    return this;
  }

  @Override
  public Type getType() {
    return Type.MULTI_NEW_ARRAY;
  }

  public DexType getArrayType() {
    return type;
  }

  @Override
  public DexType getReturnType() {
    return getArrayType();
  }

  @Override
  protected String getTypeString() {
    return "MultiNewArray";
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isInvokeMultiNewArray() && type == other.asInvokeMultiNewArray().type;
  }

  @Override
  public int compareNonValueParts(Instruction other) {
    if (!other.isInvokeMultiNewArray()) {
      return -1;
    }
    return type.slowCompareTo(other.asInvokeMultiNewArray().type);
  }

  @Override
  public Constraint inliningConstraint(AppInfoWithLiveness info, DexType invocationContext) {
    return Constraint.classIsVisible(invocationContext, type, info);
  }

  @Override
  public TypeLatticeElement evaluate(
      AppInfo appInfo, Function<Value, TypeLatticeElement> getLatticeElement) {
    return TypeLatticeElement.newArray(type, false);
  }

  @Override
  public boolean hasInvariantVerificationType() {
    return true;
  }

  @Override
  public DexType computeVerificationType(TypeVerificationHelper helper) {
    return type;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.loadInValues(this, it);
    helper.storeOutValue(this, it);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfMultiANewArray(type, arguments().size()));
  }

  @Override
  public void buildDex(DexBuilder builder) {
    throw new Unreachable("InvokeNewArray (non-empty) not supported when compiling to dex files.");
  }
}
