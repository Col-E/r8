// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.code.CfCmp;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.code.DexCmpLong;
import com.android.tools.r8.dex.code.DexCmpgDouble;
import com.android.tools.r8.dex.code.DexCmpgFloat;
import com.android.tools.r8.dex.code.DexCmplDouble;
import com.android.tools.r8.dex.code.DexCmplFloat;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.ConstantOrNonConstantNumberValue;
import com.android.tools.r8.ir.analysis.value.SingleNumberValue;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.StringUtils.BraceType;

public class Cmp extends Binop {

  public enum Bias {
    NONE, GT, LT
  }

  private final Bias bias;

  public Cmp(NumericType type, Bias bias, Value dest, Value left, Value right) {
    super(type, dest, left, right);
    this.bias = bias;
  }

  @Override
  public int opcode() {
    return Opcodes.CMP;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public boolean isCommutative() {
    return false;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    DexInstruction instruction;
    int dest = builder.allocatedRegister(outValue, getNumber());
    int left = builder.allocatedRegister(leftValue(), getNumber());
    int right = builder.allocatedRegister(rightValue(), getNumber());
    switch (type) {
      case DOUBLE:
        assert bias != Bias.NONE;
        if (bias == Bias.GT) {
          instruction = new DexCmpgDouble(dest, left, right);
        } else {
          assert bias == Bias.LT;
          instruction = new DexCmplDouble(dest, left, right);
        }
        break;
      case FLOAT:
        assert bias != Bias.NONE;
        if (bias == Bias.GT) {
          instruction = new DexCmpgFloat(dest, left, right);
        } else {
          assert bias == Bias.LT;
          instruction = new DexCmplFloat(dest, left, right);
        }
        break;
      case LONG:
        assert bias == Bias.NONE;
        instruction = new DexCmpLong(dest, left, right);
        break;
      default:
        throw new Unreachable("Unexpected type " + type);
    }
    builder.add(this, instruction);
  }

  private String biasToString(Bias bias) {
    switch (bias) {
      case NONE:
        return "none";
      case GT:
        return "gt";
      case LT:
        return "lt";
      default:
        throw new Unreachable("Unexpected bias " + bias);
    }
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(getClass().getSimpleName());
    builder.append(" (");
    switch (type) {
      case DOUBLE:
        builder.append("double, ");
        builder.append(biasToString(bias));
        break;
      case FLOAT:
        builder.append("float, ");
        builder.append(biasToString(bias));
        break;
      case LONG:
        builder.append("long");
        break;
      default:
        throw new Unreachable("Unexpected type " + type);
    }
    builder.append(")");
    for (int i = builder.length(); i < 20; i++) {
      builder.append(" ");
    }
    if (outValue != null) {
      builder.append(outValue);
      builder.append(" <- ");
    }
    StringUtils.append(builder, inValues, ", ", BraceType.NONE);
    return builder.toString();
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isCmp() && other.asCmp().bias == bias;
  }

  @Override
  public int maxInValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U8BIT_MAX;
  }

  private boolean nonOverlapingRanges() {
    return type == NumericType.LONG
        && leftValue().hasValueRange()
        && rightValue().hasValueRange()
        && leftValue().getValueRange().doesntOverlapWith(rightValue().getValueRange());
  }

  @Override
  public boolean canBeFolded() {
    return (leftValue().isConstNumber() && rightValue().isConstNumber()) || nonOverlapingRanges();
  }

  @Override
  public AbstractValue getAbstractValue(
      AppView<?> appView, ProgramMethod context, AbstractValueSupplier abstractValueSupplier) {
    if (outValue.hasLocalInfo()) {
      return AbstractValue.unknown();
    }
    AbstractValue leftAbstractValue = abstractValueSupplier.getAbstractValue(leftValue());
    AbstractValue rightAbstractValue = abstractValueSupplier.getAbstractValue(rightValue());
    if (leftAbstractValue.isSingleNumberValue() && rightAbstractValue.isSingleNumberValue()) {
      SingleNumberValue leftConst = leftAbstractValue.asSingleNumberValue();
      SingleNumberValue rightConst = rightAbstractValue.asSingleNumberValue();
      int result;
      if (type == NumericType.LONG) {
        result = Integer.signum(Long.compare(leftConst.getLongValue(), rightConst.getLongValue()));
      } else if (type == NumericType.FLOAT) {
        float left = leftConst.getFloatValue();
        float right = rightConst.getFloatValue();
        if (Float.isNaN(left) || Float.isNaN(right)) {
          result = bias == Bias.GT ? 1 : -1;
        } else {
          result = (int) Math.signum(left - right);
        }
      } else {
        assert type == NumericType.DOUBLE;
        double left = leftConst.getDoubleValue();
        double right = rightConst.getDoubleValue();
        if (Double.isNaN(left) || Double.isNaN(right)) {
          result = bias == Bias.GT ? 1 : -1;
        } else {
          result = (int) Math.signum(left - right);
        }
      }
      return appView.abstractValueFactory().createSingleNumberValue(result);
    } else if (leftAbstractValue.isConstantOrNonConstantNumberValue()
        && rightAbstractValue.isConstantOrNonConstantNumberValue()) {
      return buildLatticeResult(
          appView,
          leftAbstractValue.asConstantOrNonConstantNumberValue(),
          rightAbstractValue.asConstantOrNonConstantNumberValue());
    }
    return AbstractValue.unknown();
  }

  private AbstractValue buildLatticeResult(
      AppView<?> appView,
      ConstantOrNonConstantNumberValue leftRange,
      ConstantOrNonConstantNumberValue rightRange) {
    if (leftRange.mayOverlapWith(rightRange)) {
      return AbstractValue.unknown();
    }
    // Use min value as representative when values cannot overlap.
    int result =
        Integer.signum(Long.compare(leftRange.getMinInclusive(), rightRange.getMinInclusive()));
    return appView.abstractValueFactory().createSingleNumberValue(result);
  }

  @Override
  public boolean isCmp() {
    return true;
  }

  @Override
  public Cmp asCmp() {
    return this;
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(CfCmp.compare(bias,type), this);
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addCmp(type, bias, leftValue(), rightValue());
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    return TypeElement.getInt();
  }

}
