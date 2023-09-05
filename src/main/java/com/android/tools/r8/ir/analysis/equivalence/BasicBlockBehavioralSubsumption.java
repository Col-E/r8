// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.equivalence;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.DexItemBasedConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.WorkList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Analysis that can be used to determine if the behavior of one basic block is subsumed by the
 * behavior of another basic block.
 *
 * <p>Example: If the behavior of a switch case is subsumed by the behavior of the switch's default
 * target, then the switch case can simply be removed from the switch instruction.
 */
public class BasicBlockBehavioralSubsumption {

  private final AppView<?> appView;
  private final IRCode code;
  private final ProgramMethod context;

  public BasicBlockBehavioralSubsumption(AppView<?> appView, IRCode code) {
    this.appView = appView;
    this.code = code;
    this.context = code.context();
  }

  public boolean isSubsumedBy(Value conditionValue, BasicBlock block, BasicBlock other) {
    return isSubsumedBy(conditionValue, block.iterator(), other.iterator(), null);
  }

  private boolean dependsOnConditionValue(Value conditionValue, Instruction instruction) {
    if (instruction.isAssume()) {
      // Assume instructions are just virtual alias instructions so they are not materializing uses.
      return false;
    }
    WorkList<Assume> assumptionUses = null;
    for (Value value : instruction.inValues()) {
      Assume assumption = value.isPhi() ? null : value.getDefinition().asAssume();
      if (assumption != null) {
        if (assumptionUses == null) {
          assumptionUses = WorkList.newIdentityWorkList();
        }
        assumptionUses.addIfNotSeen(assumption);
      }
    }
    if (assumptionUses != null) {
      while (assumptionUses.hasNext()) {
        Assume next = assumptionUses.next();
        for (Value assumptionArgument : next.inValues()) {
          if (assumptionArgument == conditionValue) {
            return true;
          }
          Assume indirectAssumption =
              assumptionArgument.isPhi() ? null : assumptionArgument.getDefinition().asAssume();
          if (indirectAssumption != null) {
            assumptionUses.addIfNotSeen(indirectAssumption);
          }
        }
      }
    }
    return false;
  }

  private Instruction skipNonDependentInstructionsUntil(
      InstructionIterator iterator, Value conditionValue, Predicate<Instruction> predicate) {
    return iterator.nextUntil(
        predicate.or(i -> i.isJumpInstruction() || dependsOnConditionValue(conditionValue, i)));
  }

  private boolean isSubsumedBy(
      Value conditionValue,
      InstructionIterator iterator,
      InstructionIterator otherIterator,
      Set<BasicBlock> visited) {
    // Skip over block-local instructions (i.e., instructions that define values that are not used
    // outside the block itself) that do not have side effects.
    Instruction instruction =
        skipNonDependentInstructionsUntil(
            iterator, conditionValue, this::isNonLocalDefinitionOrSideEffecting);

    // If the instruction defines a value with non-local usages, then we would need a dominator
    // analysis to verify that all these non-local usages can in fact be replaced by a value
    // defined in `other`. We just give up in this case.
    if (definesValueWithNonLocalUsages(instruction)) {
      return false;
    }

    // Skip over non-throwing instructions in the other block.
    Instruction otherInstruction =
        skipNonDependentInstructionsUntil(
            otherIterator, conditionValue, this::instructionMayHaveSideEffects);
    assert otherInstruction != null;

    if (instruction.isGoto()) {
      BasicBlock targetBlock = instruction.asGoto().getTarget();
      if (otherInstruction.isGoto()) {
        BasicBlock otherTargetBlock = otherInstruction.asGoto().getTarget();
        // If the other instruction is also a goto instruction, which targets the same block, then
        // we are done.
        if (targetBlock == otherTargetBlock) {
          return passesIdenticalValuesForPhis(
              instruction.getBlock(), otherInstruction.getBlock(), targetBlock);
        }
        if (otherTargetBlock.hasPhis()) {
          // TODO(b/136162993): handle this case.
          return false;
        }
        // Otherwise we continue the search from the two successor blocks.
        otherIterator = otherTargetBlock.iterator();
      } else {
        if (targetBlock.hasPhis()) {
          // TODO(b/136162993): handle this case.
          return false;
        }
        // Move the cursor backwards to ensure that the recursive call will revisit the current
        // instruction.
        otherIterator.previous();
      }
      if (visited == null) {
        visited = SetUtils.newIdentityHashSet(instruction.getBlock());
      }
      if (visited.add(targetBlock)) {
        return isSubsumedBy(conditionValue, targetBlock.iterator(), otherIterator, visited);
      }
      // Guard against cycles in the control flow graph.
      return false;
    }

    // If the current instruction is not a goto instruction, but the other instruction is, then
    // we continue the search from the target of the other goto instruction.
    Set<BasicBlock> otherVisited = null;
    while (otherInstruction.isGoto()) {
      BasicBlock block = otherInstruction.getBlock();
      if (otherVisited != null && !otherVisited.add(block)) {
        // Guard against cycles in the control flow graph.
        return false;
      }

      BasicBlock targetBlock = otherInstruction.asGoto().getTarget();
      if (targetBlock.hasPhis()) {
        // TODO(b/136162993): handle this case.
        return false;
      }

      otherIterator = targetBlock.iterator();
      otherInstruction =
          skipNonDependentInstructionsUntil(
              otherIterator, conditionValue, this::instructionMayHaveSideEffects);

      // If following the first goto instruction leads to another goto instruction, then we need to
      // keep track of the set of visited blocks to guard against cycles in the control flow graph.
      if (otherInstruction.isGoto() && otherVisited == null) {
        otherVisited = SetUtils.newIdentityHashSet(block);
      }
    }

    if (instruction.isInvokeConstructor(appView.dexItemFactory())) {
      InvokeDirect invoke = instruction.asInvokeDirect();
      if (otherInstruction.isInvokeConstructor(appView.dexItemFactory())) {
        InvokeDirect otherInvoke = otherInstruction.asInvokeDirect();
        // If neither has side effects, then continue.
        DexClassAndMethod singleTarget = invoke.lookupSingleTarget(appView, context);
        if (singleTarget == null
            || singleTarget.getDefinition().getOptimizationInfo().mayHaveSideEffects()) {
          return false;
        }
        DexClassAndMethod otherSingleTarget = otherInvoke.lookupSingleTarget(appView, context);
        if (otherSingleTarget == null
            || otherSingleTarget.getDefinition().getOptimizationInfo().mayHaveSideEffects()) {
          return false;
        }
        return isSubsumedBy(conditionValue, iterator, otherIterator, visited);
      }
      return false;
    }

    if (instruction.isReturn()) {
      Return returnInstruction = instruction.asReturn();
      if (otherInstruction.isReturn()) {
        Return otherReturnInstruction = otherInstruction.asReturn();
        if (returnInstruction.isReturnVoid()) {
          assert otherReturnInstruction.isReturnVoid();
          return true;
        }
        return valuesAreIdentical(
            otherReturnInstruction.returnValue(), returnInstruction.returnValue());
      }
      return false;
    }

    return false;
  }

  private boolean isNonLocalDefinitionOrSideEffecting(Instruction instruction) {
    return !definesBlockLocalValue(instruction) || instructionMayHaveSideEffects(instruction);
  }

  private boolean definesBlockLocalValue(Instruction instruction) {
    return !definesValueWithNonLocalUsages(instruction);
  }

  private boolean definesValueWithNonLocalUsages(Instruction instruction) {
    if (instruction.hasOutValue()) {
      Value outValue = instruction.outValue();
      if (outValue.hasPhiUsers()) {
        return true;
      }
      for (Instruction user : outValue.uniqueUsers()) {
        if (user.getBlock() != instruction.getBlock()) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean instructionMayHaveSideEffects(Instruction instruction) {
    return instruction.isInvokeConstructor(appView.dexItemFactory())
        || instruction.instructionMayHaveSideEffects(appView, context);
  }

  private boolean valuesAreIdentical(Value value, Value other) {
    value = value.getAliasedValue();
    other = other.getAliasedValue();
    if (value == other) {
      return true;
    }
    if (value.isPhi() || other.isPhi()) {
      return false;
    }
    return instructionsDefineIdenticalValues(value.definition, other.definition);
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean instructionsDefineIdenticalValues(Instruction instruction, Instruction other) {
    assert instruction.hasOutValue();
    assert other.hasOutValue();

    Value outValue = instruction.outValue();
    Value otherOutValue = other.outValue();
    if (!outValue.getType().equals(otherOutValue.getType())) {
      return false;
    }

    if (instruction.isConstClass()) {
      if (!other.isConstClass()) {
        return false;
      }
      ConstClass constClassInstruction = instruction.asConstClass();
      ConstClass otherConstClassInstruction = other.asConstClass();
      return constClassInstruction.getValue() == otherConstClassInstruction.getValue();
    }

    if (instruction.isConstNumber()) {
      if (!other.isConstNumber()) {
        return false;
      }
      ConstNumber constNumberInstruction = instruction.asConstNumber();
      ConstNumber otherConstNumberInstruction = other.asConstNumber();
      return constNumberInstruction.getRawValue() == otherConstNumberInstruction.getRawValue();
    }

    if (instruction.isConstString()) {
      if (!other.isConstString()) {
        return false;
      }
      ConstString constStringInstruction = instruction.asConstString();
      ConstString otherConstStringInstruction = other.asConstString();
      return constStringInstruction.getValue() == otherConstStringInstruction.getValue();
    }

    if (instruction.isDexItemBasedConstString()) {
      if (!other.isDexItemBasedConstString()) {
        return false;
      }
      DexItemBasedConstString constStringInstruction = instruction.asDexItemBasedConstString();
      DexItemBasedConstString otherConstStringInstruction = other.asDexItemBasedConstString();
      return constStringInstruction.getItem() == otherConstStringInstruction.getItem();
    }

    return false;
  }

  private boolean passesIdenticalValuesForPhis(
      BasicBlock block, BasicBlock other, BasicBlock blockWithPhis) {
    if (block == other) {
      return true;
    }

    int predecessorIndex = -1, otherPredecessorIndex = -1;
    List<BasicBlock> predecessors = blockWithPhis.getPredecessors();
    for (int i = 0; i < predecessors.size(); i++) {
      BasicBlock predecessor = predecessors.get(i);
      if (predecessor == block) {
        predecessorIndex = i;
        if (otherPredecessorIndex >= 0) {
          break;
        }
      } else if (predecessor == other) {
        otherPredecessorIndex = i;
        if (predecessorIndex >= 0) {
          break;
        }
      }
    }

    assert predecessorIndex >= 0;
    assert otherPredecessorIndex >= 0;

    for (Phi phi : blockWithPhis.getPhis()) {
      if (!valuesAreIdentical(
          phi.getOperand(predecessorIndex), phi.getOperand(otherPredecessorIndex))) {
        return false;
      }
    }
    return true;
  }
}
