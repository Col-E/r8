// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfConstMethodType;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import java.util.function.Function;

public class ConstMethodType extends ConstInstruction {

  private final DexProto methodType;

  public ConstMethodType(Value dest, DexProto methodType) {
    super(dest);
    dest.markNeverNull();
    this.methodType = methodType;
  }

  public Value dest() {
    return outValue;
  }

  public DexProto getValue() {
    return methodType;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    int dest = builder.allocatedRegister(dest(), getNumber());
    builder.add(this, new com.android.tools.r8.code.ConstMethodType(dest, methodType));
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfConstMethodType(methodType));
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isConstMethodType() && other.asConstMethodType().methodType == methodType;
  }

  @Override
  public int compareNonValueParts(Instruction other) {
    return methodType.slowCompareTo(other.asConstMethodType().methodType);
  }

  @Override
  public int maxInValueRegister() {
    assert false : "ConstMethodType has no register arguments.";
    return 0;
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public String toString() {
    return super.toString() + " \"" + methodType + "\"";
  }

  @Override
  public boolean instructionTypeCanThrow() {
    return true;
  }

  @Override
  public boolean isOutConstant() {
    return true;
  }

  @Override
  public boolean isConstString() {
    return true;
  }

  @Override
  public ConstMethodType asConstMethodType() {
    return this;
  }

  @Override
  public TypeLatticeElement evaluate(
      AppInfo appInfo, Function<Value, TypeLatticeElement> getLatticeElement) {
    return TypeLatticeElement.fromDexType(appInfo.dexItemFactory.methodTypeType, false);
  }

  @Override
  public DexType computeVerificationType(TypeVerificationHelper helper) {
    return helper.getFactory().methodTypeType;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.storeOutValue(this, it);
  }
}
