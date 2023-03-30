// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess.readbeforewrite;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.AbstractFieldSet;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.ConcreteMutableFieldSet;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.EmptyFieldSet;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.KnownFieldSet;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.UnknownFieldSet;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DequeUtils;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;

class FieldReadBeforeWriteAnalysisImpl extends FieldReadBeforeWriteAnalysis {

  private final AppView<AppInfoWithLiveness> appView;
  private final IRCode code;
  private final ProgramMethod context;

  private Map<BasicBlock, AbstractFieldSet> fieldsMaybeReadBeforeBlockInclusiveCache;

  public FieldReadBeforeWriteAnalysisImpl(
      AppView<AppInfoWithLiveness> appView, IRCode code, ProgramMethod context) {
    this.appView = appView;
    this.code = code;
    this.context = context;
  }

  @Override
  public boolean isInstanceFieldMaybeReadBeforeInstruction(
      Value receiver, DexEncodedField field, Instruction instruction) {
    if (!code.context().getDefinition().isInstanceInitializer()
        || receiver.getAliasedValue() != code.getThis()) {
      return true;
    }
    return isFieldMaybeReadBeforeInstructionInInitializer(field, instruction);
  }

  @Override
  public boolean isStaticFieldMaybeReadBeforeInstruction(
      DexEncodedField field, Instruction instruction) {
    if (!code.context().getDefinition().isClassInitializer()
        || field.getHolderType() != code.context().getHolderType()) {
      return true;
    }
    return isFieldMaybeReadBeforeInstructionInInitializer(field, instruction);
  }

  public boolean isFieldMaybeReadBeforeInstructionInInitializer(
      DexEncodedField field, Instruction instruction) {
    BasicBlock block = instruction.getBlock();

    // First check if the field may be read in any of the (transitive) predecessor blocks.
    if (fieldMaybeReadBeforeBlock(field, block)) {
      return true;
    }

    // Then check if any of the instructions that precede the given instruction in the current block
    // may read the field.
    InstructionIterator instructionIterator = block.iterator();
    while (instructionIterator.hasNext()) {
      Instruction current = instructionIterator.next();
      if (current == instruction) {
        break;
      }
      if (current.readSet(appView, context).contains(field)) {
        return true;
      }
    }

    // Otherwise, the field is not read prior to the given instruction.
    return false;
  }

  private boolean fieldMaybeReadBeforeBlock(DexEncodedField field, BasicBlock block) {
    for (BasicBlock predecessor : block.getPredecessors()) {
      if (fieldMaybeReadBeforeBlockInclusive(field, predecessor)) {
        return true;
      }
    }
    return false;
  }

  private boolean fieldMaybeReadBeforeBlockInclusive(DexEncodedField field, BasicBlock block) {
    return getOrCreateFieldsMaybeReadBeforeBlockInclusive().get(block).contains(field);
  }

  private Map<BasicBlock, AbstractFieldSet> getOrCreateFieldsMaybeReadBeforeBlockInclusive() {
    if (fieldsMaybeReadBeforeBlockInclusiveCache == null) {
      fieldsMaybeReadBeforeBlockInclusiveCache = createFieldsMaybeReadBeforeBlockInclusive();
    }
    return fieldsMaybeReadBeforeBlockInclusiveCache;
  }

  /**
   * Eagerly creates a mapping from each block to the set of fields that may be read in that block
   * and its transitive predecessors.
   */
  private Map<BasicBlock, AbstractFieldSet> createFieldsMaybeReadBeforeBlockInclusive() {
    Map<BasicBlock, AbstractFieldSet> result = new IdentityHashMap<>();
    Deque<BasicBlock> worklist = DequeUtils.newArrayDeque(code.entryBlock());
    while (!worklist.isEmpty()) {
      BasicBlock block = worklist.removeFirst();
      boolean seenBefore = result.containsKey(block);
      AbstractFieldSet readSet =
          result.computeIfAbsent(block, ignore -> EmptyFieldSet.getInstance());
      if (readSet.isTop()) {
        // We already have unknown information for this block.
        continue;
      }

      assert readSet.isKnownFieldSet();
      KnownFieldSet knownReadSet = readSet.asKnownFieldSet();
      int oldSize = seenBefore ? knownReadSet.size() : -1;

      // Everything that is read in the predecessor blocks should also be included in the read set
      // for the current block, so here we join the information from the predecessor blocks into the
      // current read set.
      boolean blockOrPredecessorMaybeReadAnyField = false;
      for (BasicBlock predecessor : block.getPredecessors()) {
        AbstractFieldSet predecessorReadSet =
            result.getOrDefault(predecessor, EmptyFieldSet.getInstance());
        if (predecessorReadSet.isBottom()) {
          continue;
        }
        if (predecessorReadSet.isTop()) {
          blockOrPredecessorMaybeReadAnyField = true;
          break;
        }
        assert predecessorReadSet.isConcreteFieldSet();
        if (!knownReadSet.isConcreteFieldSet()) {
          knownReadSet = new ConcreteMutableFieldSet();
        }
        knownReadSet.asConcreteFieldSet().addAll(predecessorReadSet.asConcreteFieldSet());
      }

      if (!blockOrPredecessorMaybeReadAnyField) {
        // Finally, we update the read set with the fields that are read by the instructions in the
        // current block. This can be skipped if the block has already been processed.
        if (seenBefore) {
          assert verifyFieldSetContainsAllFieldReadsInBlock(knownReadSet, block, context);
        } else {
          for (Instruction instruction : block.getInstructions()) {
            AbstractFieldSet instructionReadSet = instruction.readSet(appView, context);
            if (instructionReadSet.isBottom()) {
              continue;
            }
            if (instructionReadSet.isTop()) {
              blockOrPredecessorMaybeReadAnyField = true;
              break;
            }
            if (!knownReadSet.isConcreteFieldSet()) {
              knownReadSet = new ConcreteMutableFieldSet();
            }
            knownReadSet.asConcreteFieldSet().addAll(instructionReadSet.asConcreteFieldSet());
          }
        }
      }

      boolean changed = false;
      if (blockOrPredecessorMaybeReadAnyField) {
        // Record that this block reads all fields.
        result.put(block, UnknownFieldSet.getInstance());
        changed = true;
      } else {
        if (knownReadSet != readSet) {
          result.put(block, knownReadSet.asConcreteFieldSet());
        }
        if (knownReadSet.size() != oldSize) {
          assert knownReadSet.size() > oldSize;
          changed = true;
        }
      }

      if (changed) {
        // Rerun the analysis for all successors because the state of the current block changed.
        worklist.addAll(block.getSuccessors());
      }
    }
    return result;
  }

  private boolean verifyFieldSetContainsAllFieldReadsInBlock(
      KnownFieldSet readSet, BasicBlock block, ProgramMethod context) {
    for (Instruction instruction : block.getInstructions()) {
      AbstractFieldSet instructionReadSet = instruction.readSet(appView, context);
      assert !instructionReadSet.isTop();
      if (instructionReadSet.isBottom()) {
        continue;
      }
      for (DexEncodedField field : instructionReadSet.asConcreteFieldSet().getFields()) {
        assert readSet.contains(field);
      }
    }
    return true;
  }
}
