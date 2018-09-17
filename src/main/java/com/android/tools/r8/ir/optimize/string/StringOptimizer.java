// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Value;
import com.google.common.annotations.VisibleForTesting;
import java.util.List;

public class StringOptimizer {

  @VisibleForTesting
  static boolean isStringLength(DexMethod method, DexItemFactory factory) {
    boolean isStringClass;
    if (factory != null) {
      isStringClass = method.getHolder().equals(factory.stringType);
    } else {
      isStringClass = method.getHolder().toDescriptorString().equals("Ljava/lang/String;");
    }
    return isStringClass
        && method.getArity() == 0
        && method.proto.returnType.isIntType()
        && method.name.toString().equals("length");
  }

  // Find String#length() with a constant string and compute the length of it at compile time.
  public void computeConstStringLength(IRCode code, DexItemFactory factory) {
    // TODO(jsjeon): is it worth having an indicator of String#length()?
    if (!code.hasConstString) {
      return;
    }
    InstructionIterator it = code.instructionIterator();
    while (it.hasNext()) {
      Instruction instr = it.next();
      if (!instr.isInvokeVirtual()) {
        continue;
      }
      InvokeVirtual invoke = instr.asInvokeVirtual();
      DexMethod invokedMethod = invoke.getInvokedMethod();
      if (!isStringLength(invokedMethod, factory)) {
        continue;
      }
      List<Value> ins = invoke.arguments();
      assert ins.size() == 1;
      Value in = ins.get(0);
      if (in.definition == null || !in.definition.isConstString()) {
        continue;
      }
      ConstString constString = in.definition.asConstString();
      int length = constString.getValue().toString().length();
      ConstNumber constNumber = code.createIntConstant(length);
      if (invoke.outValue().hasLocalInfo()) {
        constNumber.outValue().setLocalInfo(invoke.outValue().getLocalInfo());
      }
      it.replaceCurrentInstruction(constNumber);
    }
  }

}
