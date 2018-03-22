// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.UseRegistry;
import org.objectweb.asm.MethodVisitor;

public abstract class CfInstruction {

  public abstract void write(MethodVisitor visitor);

  public abstract void print(CfPrinter printer);

  @Override
  public String toString() {
    CfPrinter printer = new CfPrinter();
    print(printer);
    return printer.toString();
  }

  public void registerUse(UseRegistry registry, DexType clazz) {
    // Intentionally empty.
  }
}
