// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.graph.DexField;
import org.objectweb.asm.MethodVisitor;

public class CfFieldInstruction extends CfInstruction {

  private final int opcode;
  private final DexField field;

  public CfFieldInstruction(int opcode, DexField field) {
    this.opcode = opcode;
    this.field = field;
  }

  public DexField getField() {
    return field;
  }

  public int getOpcode() {
    return opcode;
  }

  @Override
  public void write(MethodVisitor visitor) {
    String owner = field.getHolder().getInternalName();
    String name = field.name.toString();
    String desc = field.type.toDescriptorString();
    visitor.visitFieldInsn(opcode, owner, name, desc);
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }
}
