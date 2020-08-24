// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.InitClassLens;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import org.objectweb.asm.MethodVisitor;

public class CfJsrRet extends CfInstruction {

  private static CompilationError error() {
    throw new CompilationError(
        "Invalid compilation of code with reachable jump subroutine RET instruction");
  }

  private final int local;

  public CfJsrRet(int local) {
    this.local = local;
  }

  @Override
  public void write(MethodVisitor visitor, InitClassLens initClassLens, NamingLens lens) {
    throw error();
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    throw error();
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, DexProgramClass context) {
    throw error();
  }

  public int getLocal() {
    return local;
  }
}
