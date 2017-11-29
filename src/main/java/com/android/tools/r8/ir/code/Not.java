// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.code.CfBinop;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.code.NotInt;
import com.android.tools.r8.code.NotLong;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.analysis.Bottom;
import com.android.tools.r8.ir.analysis.ConstLatticeElement;
import com.android.tools.r8.ir.analysis.LatticeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import java.util.Map;
import org.objectweb.asm.Opcodes;

public class Not extends Unop {

  public final NumericType type;

  public Not(NumericType type, Value dest, Value source) {
    super(dest, source);
    this.type = type;
  }

  @Override
  public boolean canBeFolded() {
    return source().isConstant();
  }

  @Override
  public LatticeElement evaluate(IRCode code, Map<Value, LatticeElement> mapping) {
    LatticeElement sourceLattice = mapping.get(source());
    if (sourceLattice.isConst()) {
      ConstNumber sourceConst = sourceLattice.asConst().getConstNumber();
      ValueType valueType = ValueType.fromNumericType(type);
      Value value = code.createValue(valueType, getLocalInfo());
      ConstNumber newConst;
      if (type == NumericType.INT) {
        newConst = new ConstNumber(value, ~sourceConst.getIntValue());
      } else {
        assert type == NumericType.LONG;
        newConst = new ConstNumber(value, ~sourceConst.getLongValue());
      }
      return new ConstLatticeElement(newConst);
    }
    return Bottom.getInstance();
  }

  @Override
  public void buildDex(DexBuilder builder) {
    com.android.tools.r8.code.Instruction instruction;
    int dest = builder.allocatedRegister(dest(), getNumber());
    int src = builder.allocatedRegister(source(), getNumber());
    switch (type) {
      case INT:
        instruction = new NotInt(dest, src);
        break;
      case LONG:
        instruction = new NotLong(dest, src);
        break;
      default:
        throw new Unreachable("Unexpected type " + type);
    }
    builder.add(this, instruction);
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.asNot().type == type;
  }

  @Override
  public int compareNonValueParts(Instruction other) {
    return type.ordinal() - other.asNot().type.ordinal();
  }

  @Override
  public boolean isNot() {
    return true;
  }

  @Override
  public Not asNot() {
    return this;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.loadInValues(this, it);
    helper.storeOutValue(this, it);
  }

  @Override
  public int getCfOpcode() {
    throw new Unreachable("Unexpected request for 'not' opcode which is translated to 'xor -1'");
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfConstNumber(-1, ValueType.fromNumericType(type)));
    builder.add(new CfBinop(type.isWide() ? Opcodes.LXOR : Opcodes.IXOR));
  }
}
