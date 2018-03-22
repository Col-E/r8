// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.UseRegistry;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class CfConstMethodType extends CfInstruction {

  private DexProto type;

  public CfConstMethodType(DexProto type) {
    this.type = type;
  }

  public DexProto getType() {
    return type;
  }

  @Override
  public void write(MethodVisitor visitor) {
    visitor.visitLdcInsn(Type.getType(type.toDescriptorString()));
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  public void registerUse(UseRegistry registry, DexType clazz) {
    registry.registerProto(type);
  }
}
