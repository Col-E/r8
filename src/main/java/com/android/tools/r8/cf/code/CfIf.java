// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.ValueType;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfIf extends CfInstruction {

  private final If.Type kind;
  private final ValueType type;
  private final CfLabel target;

  public CfIf(If.Type kind, ValueType type, CfLabel target) {
    this.kind = kind;
    this.type = type;
    this.target = target;
  }

  public CfLabel getTarget() {
    return target;
  }

  public int getOpcode() {
    switch (kind) {
      case EQ:
        return type.isObject() ? Opcodes.IFNULL : Opcodes.IFEQ;
      case GE:
        return Opcodes.IFGE;
      case GT:
        return Opcodes.IFGT;
      case LE:
        return Opcodes.IFLE;
      case LT:
        return Opcodes.IFLT;
      case NE:
        return type.isObject() ? Opcodes.IFNONNULL : Opcodes.IFNE;
      default:
        throw new Unreachable("Unexpected type " + type);
    }
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  public void write(MethodVisitor visitor) {
    visitor.visitJumpInsn(getOpcode(), target.getLabel());
  }
}
