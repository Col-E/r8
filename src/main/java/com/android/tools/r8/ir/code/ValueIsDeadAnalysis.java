// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.optimize.DeadCodeRemover.DeadInstructionResult;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Iterables;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class ValueIsDeadAnalysis {

  private final AppView<?> appView;
  private final IRCode code;

  public ValueIsDeadAnalysis(AppView<?> appView, IRCode code) {
    this.appView = appView;
    this.code = code;
  }

  public boolean isDead(Value value) {
    // Totally unused values are trivially dead.
    if (value.isUnused()) {
      return true;
    }
    WorkList<Value> worklist = WorkList.newIdentityWorkList(value);
    Value notDeadWitness = findNotDeadWitness(worklist);
    boolean isDead = Objects.isNull(notDeadWitness);
    return isDead;
  }

  public boolean hasDeadPhi(BasicBlock block) {
    return Iterables.any(block.getPhis(), this::isDead);
  }

  private Value findNotDeadWitness(WorkList<Value> worklist) {
    while (worklist.hasNext()) {
      Value value = worklist.next();

      // Give up when the dependent set of values reach a given threshold (otherwise this fails with
      // a StackOverflowError on Art003_omnibus_opcodesTest).
      // TODO(b/267990059): Remove this bail-out when the analysis is linear in the size of the code
      //  object.
      if (worklist.getSeenSet().size() > 100) {
        return value;
      }

      // If the value has debug users we cannot eliminate it since it represents a value in a local
      // variable that should be visible in the debugger.
      if (value.hasDebugUsers()) {
        return value;
      }

      Set<Value> valuesRequiredToBeDead = new LinkedHashSet<>(value.uniquePhiUsers());
      for (Instruction instruction : value.uniqueUsers()) {
        DeadInstructionResult result = instruction.canBeDeadCode(appView, code);
        if (result.isNotDead()) {
          return value;
        }
        if (result.isMaybeDead()) {
          result.getValuesRequiredToBeDead().forEach(valuesRequiredToBeDead::add);
        }
        if (instruction.hasOutValue()) {
          valuesRequiredToBeDead.add(instruction.outValue());
        }
      }

      // Continue the analysis of the dependents.
      worklist.addIfNotSeen(valuesRequiredToBeDead);
    }
    return null;
  }
}
