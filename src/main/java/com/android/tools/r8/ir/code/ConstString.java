// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.utils.InternalOptions;
import java.io.UTFDataFormatException;

public class ConstString extends ConstInstruction {

  private final DexString value;

  public ConstString(Value dest, DexString value) {
    super(dest);
    dest.markNeverNull();
    this.value = value;
  }

  public static ConstString copyOf(IRCode code, ConstString original) {
    Value newValue =
        new Value(code.valueNumberGenerator.next(), original.outType(), original.getLocalInfo());
    return new ConstString(newValue, original.getValue());
  }

  public Value dest() {
    return outValue;
  }

  public DexString getValue() {
    return value;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    builder.registerStringReference(value);
    int dest = builder.allocatedRegister(dest(), getNumber());
    builder.add(this, new com.android.tools.r8.code.ConstString(dest, value));
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isConstString() && other.asConstString().value == value;
  }

  @Override
  public int compareNonValueParts(Instruction other) {
    return value.slowCompareTo(other.asConstString().value);
  }

  @Override
  public int maxInValueRegister() {
    assert false : "ConstString has no register arguments.";
    return 0;
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public String toString() {
    return super.toString() + " \"" + value + "\"";
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
  public ConstString asConstString() {
    return this;
  }

  @Override
  public boolean instructionInstanceCanThrow() {
    // The const-string instruction can be a throwing instruction in DEX, if decode() fails.
    try {
      value.toString();
    } catch (RuntimeException e) {
      if (e.getCause() instanceof UTFDataFormatException) {
        return true;
      } else {
        throw e;
      }
    }
    return false;
  }

  @Override
  public boolean canBeDeadCode(IRCode code, InternalOptions options) {
    // No side-effect, such as throwing an exception, in CF.
    return options.isGeneratingClassFiles() || !instructionInstanceCanThrow();
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.storeOutValue(this, it);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfConstString(value));
  }

  @Override
  public DexType computeVerificationType(TypeVerificationHelper helper) {
    return helper.getFactory().stringType;
  }

  @Override
  public TypeLatticeElement evaluate(AppInfo appInfo) {
    return TypeLatticeElement.fromDexType(appInfo, appInfo.dexItemFactory.stringType, false);
  }
}
