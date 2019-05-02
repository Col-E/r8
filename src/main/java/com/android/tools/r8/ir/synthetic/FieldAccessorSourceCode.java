// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic;

import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.desugar.NestBasedAccessDesugaring.FieldAccess;

// Source code representing simple forwarding method.
public final class FieldAccessorSourceCode extends SyntheticSourceCode {

  private final DexField field;
  private final FieldAccess access;

  public FieldAccessorSourceCode(
      DexType receiver,
      DexMethod method,
      Position callerPosition,
      DexMethod originalMethod,
      DexField field,
      FieldAccess access) {
    super(receiver, method, callerPosition, originalMethod);
    this.field = field;
    this.access = access;
    assert method.proto.returnType == field.type || access.isPut();
  }

  @Override
  protected void prepareInstructions() {
    if (access == FieldAccess.INSTANCE_GET) {
      ValueType valueType = ValueType.fromDexType(proto.returnType);
      int objReg = getParamRegister(0);
      int returnReg = nextRegister(valueType);
      add(builder -> builder.addInstanceGet(returnReg, objReg, field));
      add(builder -> builder.addReturn(returnReg));
    } else if (access == FieldAccess.STATIC_GET) {
      ValueType valueType = ValueType.fromDexType(proto.returnType);
      int returnReg = nextRegister(valueType);
      add(builder -> builder.addStaticGet(returnReg, field));
      add(builder -> builder.addReturn(returnReg));
    } else if (access == FieldAccess.INSTANCE_PUT) {
      int objReg = getParamRegister(0);
      int putValueReg = getParamRegister(1);
      add(builder -> builder.addInstancePut(putValueReg, objReg, field));
      add(IRBuilder::addReturn);
    } else {
      assert access == FieldAccess.STATIC_PUT;
      int putValueReg = getParamRegister(0);
      add(builder -> builder.addStaticPut(putValueReg, field));
      add(IRBuilder::addReturn);
    }
  }
}
