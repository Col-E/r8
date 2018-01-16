// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexType;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class CfConstClass extends CfInstruction {

  private final DexType type;

  public CfConstClass(DexType type) {
    this.type = type;
  }

  public DexType getType() {
    return type;
  }

  @Override
  public void write(MethodVisitor visitor) {
    visitor.visitLdcInsn(Type.getObjectType(getInternalName()));
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  private String getInternalName() {
    switch (type.toShorty()) {
      case '[':
      case 'L':
        return type.getInternalName();
      case 'Z':
        return "java/lang/Boolean/TYPE";
      case 'B':
        return "java/lang/Byte/TYPE";
      case 'S':
        return "java/lang/Short/TYPE";
      case 'C':
        return "java/lang/Character/TYPE";
      case 'I':
        return "java/lang/Integer/TYPE";
      case 'F':
        return "java/lang/Float/TYPE";
      case 'J':
        return "java/lang/Long/TYPE";
      case 'D':
        return "java/lang/Double/TYPE";
      default:
        throw new Unreachable("Unexpected type in const-class: " + type);
    }
  }
}
