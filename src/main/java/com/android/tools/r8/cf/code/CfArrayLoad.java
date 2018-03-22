// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.naming.NamingLens;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfArrayLoad extends CfInstruction {

  private final MemberType type;

  public CfArrayLoad(MemberType type) {
    this.type = type;
  }

  public MemberType getType() {
    return type;
  }

  private int getLoadType() {
    switch (type) {
      case OBJECT:
        return Opcodes.AALOAD;
      case BYTE:
      case BOOLEAN:
        return Opcodes.BALOAD;
      case CHAR:
        return Opcodes.CALOAD;
      case SHORT:
        return Opcodes.SALOAD;
      case INT:
        return Opcodes.IALOAD;
      case FLOAT:
        return Opcodes.FALOAD;
      case LONG:
        return Opcodes.LALOAD;
      case DOUBLE:
        return Opcodes.DALOAD;
      default:
        throw new Unreachable("Unexpected type " + type);
    }
  }

  @Override
  public void write(MethodVisitor visitor, NamingLens lens) {
    visitor.visitInsn(getLoadType());
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }
}
