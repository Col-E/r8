// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.graph.DexField;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfGetField extends CfInstruction {

  private final DexField field;

  public CfGetField(DexField field) {
    this.field = field;
  }

  public DexField getField() {
    return field;
  }

  @Override
  public void write(MethodVisitor visitor) {
    String owner = field.getHolder().getInternalName();
    String name = field.name.toString();
    String desc = field.type.toDescriptorString();
    visitor.visitFieldInsn(Opcodes.GETFIELD, owner, name, desc);
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }
}
