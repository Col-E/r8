// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Value;
import com.google.common.collect.Sets;
import java.util.Set;

/**
 * When we have Assume instructions we generally verify that the Assume instructions contribute with
 * non-trivial information to the IR (e.g., the dynamic type should be more precise than the static
 * type).
 *
 * <p>Therefore, when this property may no longer hold for an Assume instruction, we need to remove
 * it.
 *
 * <p>This class is a helper class to remove these instructions. Unlike {@link
 * CodeRewriter#removeAssumeInstructions} this class does not unconditionally remove all Assume
 * instructions.
 */
public class AssumeRemover {

  private final AppView<?> appView;
  private final IRCode code;

  private final Set<Value> affectedValues = Sets.newIdentityHashSet();
  private final Set<Assume> assumeInstructionsToRemove = Sets.newIdentityHashSet();

  private boolean mayHaveIntroducedTrivialPhi = false;

  public AssumeRemover(AppView<?> appView, IRCode code) {
    this.appView = appView;
    this.code = code;
  }

  public Set<Value> getAffectedValues() {
    return affectedValues;
  }

  public boolean mayHaveIntroducedTrivialPhi() {
    return mayHaveIntroducedTrivialPhi;
  }

  public void markAssumeDynamicTypeUsersForRemoval(Value value) {
    for (Instruction user : value.aliasedUsers()) {
      if (user.isAssume()) {
        Assume assumeInstruction = user.asAssume();
        assumeInstruction.unsetDynamicTypeAssumption();
        if (!assumeInstruction.hasNonNullAssumption()) {
          assumeInstruction.unsetDynamicTypeAssumption();
        }
      }
    }
  }

  private void markForRemoval(Assume assumeInstruction) {
    assumeInstructionsToRemove.add(assumeInstruction);
  }

  public void removeIfMarked(
      Assume assumeInstruction, InstructionListIterator instructionIterator) {
    if (assumeInstructionsToRemove.remove(assumeInstruction)) {
      Value inValue = assumeInstruction.src();
      Value outValue = assumeInstruction.outValue();

      // Check if we need to run the type analysis for the affected values of the out-value.
      if (!outValue.getType().equals(inValue.getType())) {
        affectedValues.addAll(outValue.affectedValues());
      }

      if (outValue.hasPhiUsers()) {
        mayHaveIntroducedTrivialPhi = true;
      }

      outValue.replaceUsers(inValue);
      instructionIterator.removeOrReplaceByDebugLocalRead();
    }
  }

  public AssumeRemover removeMarkedInstructions() {
    return removeMarkedInstructions(null);
  }

  public AssumeRemover removeMarkedInstructions(Set<BasicBlock> blocksToBeRemoved) {
    if (!assumeInstructionsToRemove.isEmpty()) {
      for (BasicBlock block : code.blocks) {
        if (blocksToBeRemoved != null && blocksToBeRemoved.contains(block)) {
          continue;
        }
        InstructionListIterator instructionIterator = block.listIterator(code);
        while (instructionIterator.hasNext()) {
          Instruction instruction = instructionIterator.next();
          if (instruction.isAssume()) {
            removeIfMarked(instruction.asAssume(), instructionIterator);
          }
        }
      }
    }
    return this;
  }

  public void finish() {
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView).narrowing(affectedValues);
    }
  }
}
