// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.ir.code.Monitor.Type;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfMonitor extends CfInstruction {

  private final Type type;

  public CfMonitor(Type type) {
    this.type = type;
  }

  public Type getType() {
    return type;
  }

  @Override
  public void write(MethodVisitor visitor) {
    visitor.visitInsn(type == Type.ENTER ? Opcodes.MONITORENTER : Opcodes.MONITOREXIT);
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }
}
