// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.code.Const;
import com.android.tools.r8.code.Const16;
import com.android.tools.r8.code.Const4;
import com.android.tools.r8.code.ConstHigh16;
import com.android.tools.r8.code.ConstWide;
import com.android.tools.r8.code.ConstWide16;
import com.android.tools.r8.code.ConstWide32;
import com.android.tools.r8.code.ConstWideHigh16;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.constant.Bottom;
import com.android.tools.r8.ir.analysis.constant.ConstLatticeElement;
import com.android.tools.r8.ir.analysis.constant.LatticeElement;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.utils.NumberUtils;
import java.util.function.Function;

public class ConstNumber extends ConstInstruction {

  private final long value;

  public ConstNumber(Value dest, long value) {
    super(dest);
    // We create const numbers after register allocation for rematerialization of values. Those
    // are all for fixed register values. All other values that are used as the destination for
    // const number instructions should be marked as constants.
    assert dest.isFixedRegisterValue() || dest.definition.isConstNumber();
    this.value = value;
  }

  public static ConstNumber copyOf(IRCode code, ConstNumber original) {
    Value newValue = new Value(
        code.valueNumberGenerator.next(),
        original.outValue().getTypeLattice(),
        original.getLocalInfo());
    return copyOf(newValue, original);
  }

  public static ConstNumber copyOf(Value newValue, ConstNumber original) {
    return new ConstNumber(newValue, original.getRawValue());
  }

  private boolean preciseTypeUnknown() {
    return !outType().isPreciseType();
  }

  public Value dest() {
    return outValue;
  }

  public boolean getBooleanValue() {
    return !isZero();
  }

  public int getIntValue() {
    assert outType() == ValueType.INT
        || outType() == ValueType.INT_OR_FLOAT
        || outType() == ValueType.INT_OR_FLOAT_OR_NULL
        || outType() == ValueType.OBJECT; // Used for is-null conditionals.
    return (int) value;
  }

  public long getLongValue() {
    assert outType() == ValueType.LONG || outType() == ValueType.LONG_OR_DOUBLE;
    return value;
  }

  public float getFloatValue() {
    assert outType() == ValueType.FLOAT
        || outType() == ValueType.INT_OR_FLOAT
        || outType() == ValueType.INT_OR_FLOAT_OR_NULL;
    return Float.intBitsToFloat((int) value);
  }

  public double getDoubleValue() {
    assert outType() == ValueType.DOUBLE || outType() == ValueType.LONG_OR_DOUBLE;
    return Double.longBitsToDouble(value);
  }

  public long getRawValue() {
    return value;
  }

  public boolean isZero() {
    return value == 0;
  }

  public boolean isIntegerZero() {
    return outType() == ValueType.INT && getIntValue() == 0;
  }

  public boolean isIntegerOne() {
    return outType() == ValueType.INT && getIntValue() == 1;
  }

  public boolean isIntegerNegativeOne(NumericType type) {
    assert type == NumericType.INT || type == NumericType.LONG;
    if (type == NumericType.INT) {
      return getIntValue() == -1;
    }
    return getLongValue() == -1;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    if (!dest().needsRegister()) {
      builder.addNothing(this);
      return;
    }

    int register = builder.allocatedRegister(dest(), getNumber());
    if (outType().isObjectOrSingle()) {
      assert NumberUtils.is32Bit(value);
      if ((register & 0xf) == register && NumberUtils.is4Bit(value)) {
        builder.add(this, new Const4(register, (int) value));
      } else if (NumberUtils.is16Bit(value)) {
        builder.add(this, new Const16(register, (int) value));
      } else if ((value & 0x0000ffffL) == 0) {
        builder.add(this, new ConstHigh16(register, ((int) value) >>> 16));
      } else {
        builder.add(this, new Const(register, (int) value));
      }
    } else {
      assert outType().isWide();
      if (NumberUtils.is16Bit(value)) {
        builder.add(this, new ConstWide16(register, (int) value));
      } else if ((value & 0x0000ffffffffffffL) == 0) {
        builder.add(this, new ConstWideHigh16(register, (int) (value >>> 48)));
      } else if (NumberUtils.is32Bit(value)) {
        builder.add(this, new ConstWide32(register, (int) value));
      } else {
        builder.add(this, new ConstWide(register, value));
      }
    }
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.storeOutValue(this, it);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    if (outType().isObject()) {
      builder.add(new CfConstNull());
    } else {
      builder.add(new CfConstNumber(value, outType()));
    }
  }

  // Estimated size of the resulting dex instruction in code units.
  public static int estimatedDexSize(ValueType type, long value) {
    if (type.isSingle()) {
      assert NumberUtils.is32Bit(value);
      if (NumberUtils.is4Bit(value)) {
        return Const4.SIZE;
      } else if (NumberUtils.is16Bit(value)) {
        return Const16.SIZE;
      } else if ((value & 0x0000ffffL) == 0) {
        return ConstHigh16.SIZE;
      } else {
        return Const.SIZE;
      }
    } else {
      assert type.isWide();
      if (NumberUtils.is16Bit(value)) {
        return ConstWide16.SIZE;
      } else if ((value & 0x0000ffffffffffffL) == 0) {
        return ConstWideHigh16.SIZE;
      } else if (NumberUtils.is32Bit(value)) {
        return ConstWide32.SIZE;
      } else {
        return ConstWide.SIZE;
      }
    }
  }

  @Override
  public int maxInValueRegister() {
    assert false : "Const has no register arguments.";
    return 0;
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public String toString() {
    return super.toString() + " " + value + " (" + outType() + ")";
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    if (other == this) {
      return true;
    }
    if (!other.isConstNumber() || preciseTypeUnknown()) {
      return false;
    }
    ConstNumber o = other.asConstNumber();
    return o.outType() == outType() && o.value == value;
  }

  @Override
  public int compareNonValueParts(Instruction other) {
    ConstNumber o = other.asConstNumber();
    int result;
    result = outType().ordinal() - o.outType().ordinal();
    if (result != 0) {
      return result;
    }
    return Long.signum(value - o.value);
  }

  public boolean is8Bit() {
    return NumberUtils.is8Bit(value);
  }

  public boolean negativeIs8Bit() {
    return NumberUtils.negativeIs8Bit(value);
  }

  public boolean is16Bit() {
    return NumberUtils.is16Bit(value);
  }

  public boolean negativeIs16Bit() {
    return NumberUtils.negativeIs16Bit(value);
  }

  @Override
  public boolean isOutConstant() {
    return true;
  }

  @Override
  public boolean isConstNumber() {
    return true;
  }

  @Override
  public ConstNumber asConstNumber() {
    return this;
  }

  @Override
  public DexType computeVerificationType(TypeVerificationHelper helper) {
    assert outType().isObject();
    return helper.getFactory().nullValueType;
  }

  @Override
  public LatticeElement evaluate(IRCode code, Function<Value, LatticeElement> getLatticeElement) {
    if (outValue.hasLocalInfo()) {
      return Bottom.getInstance();
    }
    return new ConstLatticeElement(this);
  }

  @Override
  public TypeLatticeElement evaluate(AppInfo appInfo) {
    // TODO(b/72693244): IR builder should know the type and assign a proper type lattice.
    switch (outType()) {
      case OBJECT:
        assert isZero();
        return TypeLatticeElement.NULL;
      case INT:
        return TypeLatticeElement.INT;
      case FLOAT:
        return TypeLatticeElement.FLOAT;
      case LONG:
        return TypeLatticeElement.LONG;
      case DOUBLE:
        return TypeLatticeElement.DOUBLE;
      case INT_OR_FLOAT:
        return TypeLatticeElement.SINGLE;
      case LONG_OR_DOUBLE:
        return TypeLatticeElement.DOUBLE;
      case INT_OR_FLOAT_OR_NULL:
      default:
        return TypeLatticeElement.BOTTOM;
    }
  }
}
