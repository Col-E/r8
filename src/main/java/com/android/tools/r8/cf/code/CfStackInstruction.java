// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.naming.NamingLens;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfStackInstruction extends CfInstruction {

  public enum Opcode {
    Pop(Opcodes.POP),
    Pop2(Opcodes.POP2),
    Dup(Opcodes.DUP),
    DupX1(Opcodes.DUP_X1),
    DupX2(Opcodes.DUP_X2),
    Dup2(Opcodes.DUP2),
    Dup2X1(Opcodes.DUP2_X1),
    Dup2X2(Opcodes.DUP2_X2),
    Swap(Opcodes.SWAP);

    private final int opcode;

    Opcode(int opcode) {
      this.opcode = opcode;
    }
  }

  private final Opcode opcode;

  public static CfStackInstruction fromAsm(int opcode) {
    switch (opcode) {
      case Opcodes.POP:
        return new CfStackInstruction(Opcode.Pop);
      case Opcodes.POP2:
        return new CfStackInstruction(Opcode.Pop2);
      case Opcodes.DUP:
        return new CfStackInstruction(Opcode.Dup);
      case Opcodes.DUP_X1:
        return new CfStackInstruction(Opcode.DupX1);
      case Opcodes.DUP_X2:
        return new CfStackInstruction(Opcode.DupX2);
      case Opcodes.DUP2:
        return new CfStackInstruction(Opcode.Dup2);
      case Opcodes.DUP2_X1:
        return new CfStackInstruction(Opcode.Dup2X1);
      case Opcodes.DUP2_X2:
        return new CfStackInstruction(Opcode.Dup2X2);
      case Opcodes.SWAP:
        return new CfStackInstruction(Opcode.Swap);
      default:
        throw new Unreachable("Invalid opcode for CfStackInstruction");
    }
  }

  public static CfStackInstruction popType(ValueType type) {
    return new CfStackInstruction(type.isWide() ? Opcode.Pop2 : Opcode.Pop);
  }

  public CfStackInstruction(Opcode opcode) {
    this.opcode = opcode;
  }

  @Override
  public void write(MethodVisitor visitor, NamingLens lens) {
    visitor.visitInsn(opcode.opcode);
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  public Opcode getOpcode() {
    return opcode;
  }
}
