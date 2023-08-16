// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import static com.android.tools.r8.dex.Constants.U4BIT_MAX;
import static com.android.tools.r8.dex.Constants.U8BIT_MAX;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfIfCmp;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOutputMode;
import java.util.List;

public class If extends JumpInstruction {

  private static boolean verifyTypeCompatible(TypeElement valueType, IfType ifType) {
    return valueType.isInt()
        || (valueType.isFloat() && (ifType == IfType.EQ || ifType == IfType.NE))
        || (valueType.isReferenceType() && (ifType == IfType.EQ || ifType == IfType.NE));
  }

  private IfType type;

  public If(IfType type, Value value) {
    super(value);
    this.type = type;
  }

  public If(IfType type, List<Value> values) {
    super(values);
    this.type = type;
  }

  @Override
  public int opcode() {
    return Opcodes.IF;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public boolean isNullTest() {
    return isZeroTest() && lhs().getType().isReferenceType();
  }

  public boolean isNonTrivialNullTest() {
    return isNullTest() && lhs().getType().isNullable();
  }

  public boolean isZeroTest() {
    return inValues.size() == 1;
  }

  public Value lhs() {
    return inValues.get(0);
  }

  public Value rhs() {
    assert !isZeroTest();
    return inValues.get(1);
  }

  public IfType getType() {
    return type;
  }

  public void invert() {
    BasicBlock tmp = getTrueTarget();
    setTrueTarget(fallthroughBlock());
    setFallthroughBlock(tmp);
    type = type.inverted();
  }

  public BasicBlock getTrueTarget() {
    assert getBlock().exit() == this;
    List<BasicBlock> successors = getBlock().getSuccessors();
    assert successors.size() >= 2;
    return successors.get(successors.size() - 2);
  }

  public void setTrueTarget(BasicBlock block) {
    assert getBlock().exit() == this;
    List<BasicBlock> successors = getBlock().getMutableSuccessors();
    assert successors.size() >= 2;
    successors.set(successors.size() - 2, block);
  }

  @Override
  public BasicBlock fallthroughBlock() {
    assert getBlock().exit() == this;
    List<BasicBlock> successors = getBlock().getSuccessors();
    assert successors.size() >= 2;
    return successors.get(successors.size() - 1);
  }

  @Override
  public void setFallthroughBlock(BasicBlock block) {
    List<BasicBlock> successors = getBlock().getMutableSuccessors();
    successors.set(successors.size() - 1, block);
  }

  @Override
  public void buildDex(DexBuilder builder) {
    builder.addIf(this);
  }

  // Estimated size of the resulting instructions in code units (bytes in CF, 16-bit in Dex).
  public static int estimatedSize(InternalOutputMode mode) {
    if (mode.isGeneratingClassFiles()) {
      // op + branch1 + branch2
      return 3;
    } else {
      return 2;
    }
  }

  @Override
  public String toString() {
    StringBuilder builder =
        new StringBuilder(super.toString())
            .append(' ')
            .append(type)
            .append(isZeroTest() ? 'Z' : ' ');
    // If this instruction is in a block that has been marked for removal, but not yet removed from
    // the IR, make sure we can still print the code.
    if (getBlock().exit() == this && getBlock().getSuccessors().size() >= 2) {
      builder
          .append(" block ")
          .append(getTrueTarget().getNumberAsString())
          .append(" (fallthrough ")
          .append(fallthroughBlock().getNumberAsString())
          .append(')');
    }
    return builder.toString();
  }

  @Override
  public int maxInValueRegister() {
    return isZeroTest() ? U8BIT_MAX : U4BIT_MAX;
  }

  @Override
  public int maxOutValueRegister() {
    assert false : "If instructions define no values.";
    return 0;
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    if (!other.isIf()) {
      return false;
    }
    If o = other.asIf();
    return o.getTrueTarget() == getTrueTarget()
        && o.fallthroughBlock() == fallthroughBlock()
        && o.type == type;
  }

  public BasicBlock targetFromTrue() {
    return targetFromBoolean(true);
  }

  public BasicBlock targetFromFalse() {
    return targetFromBoolean(false);
  }

  public BasicBlock targetFromBoolean(boolean cond) {
    assert isZeroTest();
    return targetFromCondition(BooleanUtils.intValue(cond));
  }

  public BasicBlock targetFromCondition(ConstNumber value) {
    assert isZeroTest();
    assert verifyTypeCompatible(value.getOutType(), type);
    return targetFromCondition(Long.signum(value.getRawValue()));
  }

  public BasicBlock targetFromCondition(ConstNumber left, ConstNumber right) {
    assert !isZeroTest();
    assert left.outType() == right.outType();
    assert verifyTypeCompatible(left.getOutType(), type);
    return targetFromCondition(left.getRawValue(), right.getRawValue());
  }

  public BasicBlock targetFromCondition(long left, long right) {
    assert !isZeroTest();
    return targetFromCondition(Long.signum(left - right));
  }

  public BasicBlock targetFromNonNullObject() {
    assert isZeroTest();
    assert inValues.get(0).outType().isObject();
    return targetFromCondition(1);
  }

  public BasicBlock targetFromNullObject() {
    assert isZeroTest();
    assert inValues.get(0).outType().isObject();
    return targetFromCondition(0);
  }

  public BasicBlock targetFromCondition(int cond) {
    assert Integer.signum(cond) == cond;
    switch (type) {
      case EQ:
        return cond == 0 ? getTrueTarget() : fallthroughBlock();
      case NE:
        return cond != 0 ? getTrueTarget() : fallthroughBlock();
      case GE:
        return cond >= 0 ? getTrueTarget() : fallthroughBlock();
      case GT:
        return cond > 0 ? getTrueTarget() : fallthroughBlock();
      case LE:
        return cond <= 0 ? getTrueTarget() : fallthroughBlock();
      case LT:
        return cond < 0 ? getTrueTarget() : fallthroughBlock();
    }
    throw new Unreachable("Unexpected condition type " + type);
  }

  @Override
  public boolean isIf() {
    return true;
  }

  @Override
  public If asIf() {
    return this;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.loadInValues(this, it);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    ValueType ifType = inValues.get(0).outType();
    if (inValues.size() == 1) {
      builder.add(new CfIf(type, ifType, builder.getLabel(getTrueTarget())), this);
      return;
    }
    assert inValues.size() == 2;
    assert inValues.get(0).outType() == inValues.get(1).outType();
    builder.add(new CfIfCmp(type, ifType, builder.getLabel(getTrueTarget())), this);
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    ValueType ifType = inValues.get(0).outType();
    if (inValues.size() == 1) {
      builder.addIf(type, ifType, inValues.get(0), getTrueTarget());
      return;
    }
    assert inValues.size() == 2;
    assert inValues.get(0).outType() == inValues.get(1).outType();
    builder.addIfCmp(type, ifType, inValues, getTrueTarget());
  }
}
