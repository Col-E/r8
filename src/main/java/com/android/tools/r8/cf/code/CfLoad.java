// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.conversion.CfBuilder.LocalType;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfLoad extends CfInstruction {

  private final int var;
  private final LocalType type;

  public CfLoad(LocalType type, int var) {
    this.var = var;
    this.type = type;
  }

  private int getLoadType() {
    switch (type) {
      case REFERENCE:
        return Opcodes.ALOAD;
      case INTEGER:
        return Opcodes.ILOAD;
      case FLOAT:
        return Opcodes.FLOAD;
      case LONG:
        return Opcodes.LLOAD;
      case DOUBLE:
        return Opcodes.DLOAD;
      default:
        throw new Unreachable("Unexpected type " + type);
    }
  }

  @Override
  public void write(MethodVisitor visitor) {
    visitor.visitVarInsn(getLoadType(), var);
  }
}
