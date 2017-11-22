// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexType;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class CfNewArray extends CfInstruction {

  private final DexType type;

  public CfNewArray(DexType type) {
    assert type.isArrayType();
    this.type = type;
  }

  public DexType getType() {
    return type;
  }

  private int getPrimitiveTypeCode() {
    switch (type.descriptor.content[1]) {
      case 'Z':
        return Opcodes.T_BOOLEAN;
      case 'C':
        return Opcodes.T_CHAR;
      case 'F':
        return Opcodes.T_FLOAT;
      case 'D':
        return Opcodes.T_DOUBLE;
      case 'B':
        return Opcodes.T_BYTE;
      case 'S':
        return Opcodes.T_SHORT;
      case 'I':
        return Opcodes.T_INT;
      case 'J':
        return Opcodes.T_LONG;
      default:
        throw new Unreachable("Unexpected type for new-array: " + type);
    }
  }

  private String getElementInternalName() {
    assert !type.isPrimitiveArrayType();
    return Type.getType(type.toDescriptorString().substring(1)).getInternalName();
  }

  @Override
  public void write(MethodVisitor visitor) {
    if (type.isPrimitiveArrayType()) {
      visitor.visitIntInsn(Opcodes.NEWARRAY, getPrimitiveTypeCode());
    } else {
      visitor.visitTypeInsn(Opcodes.ANEWARRAY, getElementInternalName());
    }
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }
}
