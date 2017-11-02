// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.ir.code.ValueType;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfPop extends CfInstruction {

  private final ValueType type;

  public CfPop(ValueType type) {
    this.type = type;
  }

  @Override
  public void write(MethodVisitor visitor) {
    visitor.visitInsn(type.isWide() ? Opcodes.POP2 : Opcodes.POP);
  }
}
