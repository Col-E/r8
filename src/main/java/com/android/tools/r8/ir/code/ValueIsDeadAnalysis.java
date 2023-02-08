// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.optimize.DeadCodeRemover.DeadInstructionResult;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
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
    return isDead(value, Sets.newIdentityHashSet());
  }

  public boolean hasDeadPhi(BasicBlock block) {
    return Iterables.any(block.getPhis(), this::isDead);
  }

  private boolean isDead(Value value, Set<Value> active) {
    // Give up when the dependent set of values reach a given threshold (otherwise this fails with
    // a StackOverflowError on Art003_omnibus_opcodesTest).
    if (active.size() > 100) {
      return false;
    }

    // If the value has debug users we cannot eliminate it since it represents a value in a local
    // variable that should be visible in the debugger.
    if (value.hasDebugUsers()) {
      return false;
    }
    // This is a candidate for a dead value. Guard against looping by adding it to the set of
    // currently active values.
    active.add(value);
    for (Instruction instruction : value.uniqueUsers()) {
      DeadInstructionResult result = instruction.canBeDeadCode(appView, code);
      if (result.isNotDead()) {
        return false;
      }
      if (result.isMaybeDead()) {
        for (Value valueRequiredToBeDead : result.getValuesRequiredToBeDead()) {
          if (!active.contains(valueRequiredToBeDead) && !isDead(valueRequiredToBeDead, active)) {
            return false;
          }
        }
      }
      Value outValue = instruction.outValue();
      if (outValue != null && !active.contains(outValue) && !isDead(outValue, active)) {
        return false;
      }
    }
    for (Phi phi : value.uniquePhiUsers()) {
      if (!active.contains(phi) && !isDead(phi, active)) {
        return false;
      }
    }
    return true;
  }
}
