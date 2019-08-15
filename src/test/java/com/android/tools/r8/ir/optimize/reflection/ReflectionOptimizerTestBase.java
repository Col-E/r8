// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.reflection;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;

abstract class ReflectionOptimizerTestBase extends TestBase {
  private static boolean isGetClass(DexMethod method) {
    return method.getArity() == 0
        && method.proto.returnType.toDescriptorString().equals("Ljava/lang/Class;")
        && method.name.toString().equals("getClass");
  }

  long countGetClass(MethodSubject method) {
    return method.streamInstructions().filter(instructionSubject -> {
      if (instructionSubject.isInvoke()) {
        return isGetClass(instructionSubject.getMethod());
      }
      return false;
    }).count();
  }

  private static boolean isForName(DexMethod method) {
    return method.getArity() == 1
        && method.proto.returnType.toDescriptorString().equals("Ljava/lang/Class;")
        && method.holder.toDescriptorString().equals("Ljava/lang/Class;")
        && method.name.toString().equals("forName");
  }

  long countForName(MethodSubject method) {
    return method.streamInstructions().filter(instructionSubject -> {
      if (instructionSubject.isInvoke()) {
        return isForName(instructionSubject.getMethod());
      }
      return false;
    }).count();
  }

  long countConstClass(MethodSubject method) {
    return method.streamInstructions().filter(InstructionSubject::isConstClass).count();
  }

  long countConstString(MethodSubject method) {
    return method.streamInstructions().filter(i -> i.isConstString(JumboStringMode.ALLOW)).count();
  }
}
