// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.graph.DexType;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfInstanceOf extends CfInstruction {

  private final DexType type;

  public CfInstanceOf(DexType type) {
    this.type = type;
  }

  public DexType getType() {
    return type;
  }

  @Override
  public void write(MethodVisitor visitor) {
    visitor.visitTypeInsn(Opcodes.INSTANCEOF, type.getInternalName());
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }
}
