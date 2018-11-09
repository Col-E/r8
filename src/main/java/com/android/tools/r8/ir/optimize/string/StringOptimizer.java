// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.ir.optimize.CodeRewriter.removeOrReplaceByDebugLocalWrite;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.BasicBlock.ThrowingInfo;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Value;

public class StringOptimizer {

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
      if (invokedMethod != factory.stringMethods.length) {
        continue;
      }
      assert invoke.inValues().size() == 1;
      Value in = invoke.getReceiver();
      if (in.definition == null
          || !in.definition.isConstString()
          || !in.definition.outValue().isConstant()) {
        continue;
      }
      ConstString constString = in.definition.asConstString();
      int length = constString.getValue().toString().length();
      ConstNumber constNumber = code.createIntConstant(length);
      it.replaceCurrentInstruction(constNumber);
    }
  }

  // String#valueOf(null) -> "null"
  // String#valueOf(String s) -> s
  // str.toString() -> str
  public void removeTrivialConversions(IRCode code, AppInfo appInfo) {
    InstructionIterator it = code.instructionIterator();
    while (it.hasNext()) {
      Instruction instr = it.next();
      if (instr.isInvokeStatic()) {
        InvokeStatic invoke = instr.asInvokeStatic();
        DexMethod invokedMethod = invoke.getInvokedMethod();
        if (invokedMethod != appInfo.dexItemFactory.stringMethods.valueOf) {
          continue;
        }
        assert invoke.inValues().size() == 1;
        Value in = invoke.inValues().get(0);
        if (in.hasLocalInfo()) {
          continue;
        }
        TypeLatticeElement inType = in.getTypeLattice();
        if (inType.isNull()) {
          Value nullStringValue =
              code.createValue(TypeLatticeElement.stringClassType(appInfo), invoke.getLocalInfo());
          ThrowingInfo throwingInfo =
              code.options.isGeneratingClassFiles() ? ThrowingInfo.NO_THROW : ThrowingInfo.CAN_THROW;
          ConstString nullString =
              new ConstString(nullStringValue, appInfo.dexItemFactory.createString("null"), throwingInfo);
          it.replaceCurrentInstruction(nullString);
        } else if (inType.isClassType()
            && inType.asClassTypeLatticeElement().getClassType()
                .equals(appInfo.dexItemFactory.stringType)) {
          Value out = invoke.outValue();
          if (out != null) {
            removeOrReplaceByDebugLocalWrite(invoke, it, in, out);
          } else {
            it.removeOrReplaceByDebugLocalRead();
          }
        }
      } else if (instr.isInvokeVirtual()) {
        InvokeVirtual invoke = instr.asInvokeVirtual();
        DexMethod invokedMethod = invoke.getInvokedMethod();
        if (invokedMethod != appInfo.dexItemFactory.stringMethods.toString) {
          continue;
        }
        assert invoke.inValues().size() == 1;
        Value in = invoke.getReceiver();
        TypeLatticeElement inType = in.getTypeLattice();
        if (inType.nullElement().isDefinitelyNotNull()
            && inType.isClassType()
            && inType.asClassTypeLatticeElement().getClassType()
                .equals(appInfo.dexItemFactory.stringType)) {
          Value out = invoke.outValue();
          if (out != null) {
            removeOrReplaceByDebugLocalWrite(invoke, it, in, out);
          } else {
            it.removeOrReplaceByDebugLocalRead();
          }
        }
      }
    }
    assert code.isConsistentSSA();
  }

}
