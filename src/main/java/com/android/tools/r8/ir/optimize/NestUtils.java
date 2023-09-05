// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeInterface;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeVirtual;

public class NestUtils {

  @SuppressWarnings("ReferenceEquality")
  public static boolean sameNest(DexType type1, DexType type2, DexDefinitionSupplier definitions) {
    if (type1 == type2) {
      return true;
    }
    DexClass clazz1 = definitions.definitionFor(type1);
    if (clazz1 == null) {
      // Conservatively return false
      return false;
    }
    if (!clazz1.isInANest()) {
      return false;
    }
    DexClass clazz2 = definitions.definitionFor(type2);
    if (clazz2 == null) {
      // Conservatively return false
      return false;
    }
    return clazz1.getNestHost() == clazz2.getNestHost();
  }

  @SuppressWarnings("ReferenceEquality")
  public static void rewriteNestCallsForInlining(
      IRCode code, ProgramMethod callerContext, AppView<?> appView) {
    // This method is called when inlining code into the nest member callerHolder.
    InstructionListIterator iterator = code.instructionListIterator();
    assert code.context().getHolder() != callerContext.getHolder();
    while (iterator.hasNext()) {
      Instruction instruction = iterator.next();
      if (instruction.isInvokeDirect()) {
        InvokeDirect invoke = instruction.asInvokeDirect();
        DexMethod method = invoke.getInvokedMethod();
        DexClass holder = appView.definitionForHolder(method);
        DexEncodedMethod encodedMethod = method.lookupOnClass(holder);
        if (encodedMethod != null && !encodedMethod.isInstanceInitializer()) {
          assert encodedMethod.isPrivateMethod();
          // Call to private method which has now to be interface/virtual
          // (Now call to nest member private method).
          if (invoke.getInterfaceBit()) {
            iterator.replaceCurrentInstruction(
                new InvokeInterface(method, invoke.outValue(), invoke.arguments()));
          } else {
            iterator.replaceCurrentInstruction(
                new InvokeVirtual(method, invoke.outValue(), invoke.arguments()));
          }
        }
      } else if (instruction.isInvokeInterface() || instruction.isInvokeVirtual()) {
        InvokeMethod invoke = instruction.asInvokeMethod();
        DexMethod method = invoke.getInvokedMethod();
        if (method.holder == callerContext.getHolderType()) {
          DexClass holder = appView.definitionForHolder(method);
          DexEncodedMethod encodedMethod = method.lookupOnClass(holder);
          if (encodedMethod != null && encodedMethod.isPrivateMethod()) {
            // Interface/virtual nest member call to private method,
            // which has now to be a direct call
            // (Now call to same class private method).
            iterator.replaceCurrentInstruction(
                new InvokeDirect(
                    method,
                    invoke.outValue(),
                    invoke.arguments(),
                    callerContext.getHolder().isInterface()));
          }
        }
      }
    }
  }
}
