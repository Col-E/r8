// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.naming.NamingLens;
import org.objectweb.asm.MethodVisitor;

public class CfIinc extends CfInstruction {

  private final int var;
  private final int increment;

  public CfIinc(int var, int increment) {
    this.var = var;
    this.increment = increment;
  }

  @Override
  public void write(MethodVisitor visitor, NamingLens lens) {
    visitor.visitIincInsn(var, increment);
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  public int getLocalIndex() {
    return var;
  }

  public int getIncrement() {
    return increment;
  }
}
