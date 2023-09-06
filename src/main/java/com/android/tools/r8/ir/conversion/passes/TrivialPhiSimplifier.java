// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.algorithms.scc.SCC;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.UnusedArgument;
import com.android.tools.r8.ir.code.Value;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;

public class TrivialPhiSimplifier {

  public static void replaceUnusedArgumentTrivialPhis(UnusedArgument unusedArgument) {
    replaceTrivialPhis(unusedArgument.outValue());
    for (Phi phiUser : unusedArgument.outValue().uniquePhiUsers()) {
      phiUser.removeTrivialPhi();
    }
    assert !unusedArgument.outValue().hasPhiUsers();
  }

  public static void ensureDirectStringNewToInit(AppView<?> appView, IRCode code) {
    boolean changed = false;
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    for (Instruction instruction : code.instructions()) {
      if (instruction.isInvokeDirect()) {
        InvokeDirect invoke = instruction.asInvokeDirect();
        DexMethod method = invoke.getInvokedMethod();
        if (dexItemFactory.isConstructor(method)
            && method.holder == dexItemFactory.stringType
            && invoke.getReceiver().isPhi()) {
          NewInstance newInstance = findNewInstance(invoke.getReceiver().asPhi());
          replaceTrivialPhis(newInstance.outValue());
          if (invoke.getReceiver().isPhi()) {
            throw new CompilationError(
                "Failed to remove trivial phis between new-instance and <init>");
          }
          newInstance.markNoSpilling();
          changed = true;
        }
      }
    }
    assert !changed || code.isConsistentSSA(appView);
  }

  private static NewInstance findNewInstance(Phi phi) {
    Set<Phi> seen = Sets.newIdentityHashSet();
    Set<Value> values = Sets.newIdentityHashSet();
    recursiveAddOperands(phi, seen, values);
    if (values.size() != 1) {
      throw new CompilationError("Failed to identify unique new-instance for <init>");
    }
    Value newInstanceValue = values.iterator().next();
    if (newInstanceValue.definition == null || !newInstanceValue.definition.isNewInstance()) {
      throw new CompilationError("Invalid defining value for call to <init>");
    }
    return newInstanceValue.definition.asNewInstance();
  }

  private static void recursiveAddOperands(Phi phi, Set<Phi> seen, Set<Value> values) {
    for (Value operand : phi.getOperands()) {
      if (!operand.isPhi()) {
        values.add(operand);
      } else {
        Phi phiOp = operand.asPhi();
        if (seen.add(phiOp)) {
          recursiveAddOperands(phiOp, seen, values);
        }
      }
    }
  }

  // We compute the set of strongly connected phis making use of the out value and replace all
  // trivial ones by the out value.
  // This is a simplified variant of the removeRedundantPhis algorithm in Section 3.2 of:
  // http://compilers.cs.uni-saarland.de/papers/bbhlmz13cc.pdf
  private static void replaceTrivialPhis(Value outValue) {
    List<Set<Value>> components = new SCC<Value>(Value::uniquePhiUsers).computeSCC(outValue);
    for (int i = components.size() - 1; i >= 0; i--) {
      Set<Value> component = components.get(i);
      if (component.size() == 1 && component.iterator().next() == outValue) {
        continue;
      }
      Set<Phi> trivialPhis = Sets.newIdentityHashSet();
      for (Value value : component) {
        boolean isTrivial = true;
        Phi p = value.asPhi();
        for (Value op : p.getOperands()) {
          if (op != outValue && !component.contains(op)) {
            isTrivial = false;
            break;
          }
        }
        if (isTrivial) {
          trivialPhis.add(p);
        }
      }
      for (Phi trivialPhi : trivialPhis) {
        for (Value op : trivialPhi.getOperands()) {
          op.removePhiUser(trivialPhi);
        }
        trivialPhi.replaceUsers(outValue);
        trivialPhi.getBlock().removePhi(trivialPhi);
      }
    }
  }
}
