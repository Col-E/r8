// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldvalueanalysis;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.DominatorTree;
import com.android.tools.r8.ir.code.DominatorTree.Assumption;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.ClassInitializerDefaultsOptimization.ClassInitializerDefaultsResult;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfo;
import com.android.tools.r8.ir.optimize.info.field.UnknownInstanceFieldInitializationInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DequeUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.collections.DexClassAndFieldMap;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public abstract class FieldValueAnalysis {

  static class FieldInitializationInfo {

    private final Instruction instruction;
    private final InstanceFieldInitializationInfo instanceFieldInitializationInfo;

    FieldInitializationInfo(
        Instruction instruction, InstanceFieldInitializationInfo instanceFieldInitializationInfo) {
      this.instruction = instruction;
      this.instanceFieldInitializationInfo = instanceFieldInitializationInfo;
    }
  }

  final AppView<AppInfoWithLiveness> appView;
  final IRCode code;
  final ProgramMethod context;
  final OptimizationFeedback feedback;

  private DominatorTree dominatorTree;
  private Map<BasicBlock, AbstractFieldSet> fieldsMaybeReadBeforeBlockInclusiveCache;

  final DexClassAndFieldMap<List<FieldInitializationInfo>> putsPerField =
      DexClassAndFieldMap.create();

  FieldValueAnalysis(
      AppView<AppInfoWithLiveness> appView, IRCode code, OptimizationFeedback feedback) {
    this.appView = appView;
    this.code = code;
    this.feedback = feedback;
    this.context = code.context();
  }

  DominatorTree getOrCreateDominatorTree() {
    if (dominatorTree == null) {
      dominatorTree = new DominatorTree(code, Assumption.NO_UNREACHABLE_BLOCKS);
    }
    return dominatorTree;
  }

  private Map<BasicBlock, AbstractFieldSet> getOrCreateFieldsMaybeReadBeforeBlockInclusive() {
    if (fieldsMaybeReadBeforeBlockInclusiveCache == null) {
      fieldsMaybeReadBeforeBlockInclusiveCache = createFieldsMaybeReadBeforeBlockInclusive();
    }
    return fieldsMaybeReadBeforeBlockInclusiveCache;
  }

  boolean isInstanceFieldValueAnalysis() {
    return false;
  }

  InstanceFieldValueAnalysis asInstanceFieldValueAnalysis() {
    return null;
  }

  boolean isStaticFieldValueAnalysis() {
    return false;
  }

  StaticFieldValueAnalysis asStaticFieldValueAnalysis() {
    return null;
  }

  abstract boolean isSubjectToOptimizationIgnoringPinning(DexClassAndField field);

  abstract boolean isSubjectToOptimization(DexClassAndField field);

  void recordFieldPut(DexClassAndField field, Instruction instruction) {
    recordFieldPut(field, instruction, UnknownInstanceFieldInitializationInfo.getInstance());
  }

  void recordFieldPut(
      DexClassAndField field, Instruction instruction, InstanceFieldInitializationInfo info) {
    putsPerField
        .computeIfAbsent(field, ignore -> new ArrayList<>())
        .add(new FieldInitializationInfo(instruction, info));
  }

  /** This method analyzes initializers with the purpose of computing field optimization info. */
  void computeFieldOptimizationInfo(ClassInitializerDefaultsResult classInitializerDefaultsResult) {
    AppInfoWithLiveness appInfo = appView.appInfo();

    // Find all the static-put instructions that assign a field in the enclosing class which is
    // guaranteed to be assigned only in the current initializer.
    for (BasicBlock block : code.getBlocks()) {
      for (Instruction instruction : block.getInstructions()) {
        if (instruction.isFieldPut()) {
          FieldInstruction fieldPut = instruction.asFieldInstruction();
          DexField fieldReference = fieldPut.getField();
          ProgramField field = appInfo.resolveField(fieldReference).getProgramField();
          if (field != null) {
            if (isSubjectToOptimization(field)) {
              recordFieldPut(field, fieldPut);
            } else if (isStaticFieldValueAnalysis()
                && field.getHolder().isEnum()
                && isSubjectToOptimizationIgnoringPinning(field)) {
              recordFieldPut(field, fieldPut);
            }
          }
        } else if (isInstanceFieldValueAnalysis()
            && instruction.isInvokeConstructor(appView.dexItemFactory())) {
          InvokeDirect invoke = instruction.asInvokeDirect();
          asInstanceFieldValueAnalysis().analyzeForwardingConstructorCall(invoke, code.getThis());
        }
      }
    }

    boolean isStraightLineCode =
        Iterables.all(code.getBlocks(), block -> block.getSuccessors().size() <= 1);
    List<BasicBlock> normalExitBlocks = code.computeNormalExitBlocks();
    putsPerField.forEach(
        (field, fieldPuts) -> {
          if (fieldPuts.size() > 1) {
            return;
          }
          FieldInitializationInfo info = ListUtils.first(fieldPuts);
          Instruction instruction = info.instruction;
          if (instruction.isInvokeDirect()) {
            asInstanceFieldValueAnalysis()
                .recordInstanceFieldIsInitializedWithInfo(
                    field, info.instanceFieldInitializationInfo);
            return;
          }
          FieldInstruction fieldPut = instruction.asFieldInstruction();
          if (!isStraightLineCode) {
            if (!getOrCreateDominatorTree().dominatesAllOf(fieldPut.getBlock(), normalExitBlocks)) {
              return;
            }
          }
          boolean priorReadsWillReadSameValue =
              !classInitializerDefaultsResult.hasStaticValue(field) && fieldPut.value().isZero();
          if (!priorReadsWillReadSameValue && fieldMaybeReadBeforeInstruction(field, fieldPut)) {
            // TODO(b/172528424): Generalize to InstanceFieldValueAnalysis.
            if (isStaticFieldValueAnalysis()) {
              // At this point the value read in the field can be only the default static value, if
              // read prior to the put, or the value put, if read after the put. We still want to
              // record it because the default static value is typically null/0, so code present
              // after a null/0 check can take advantage of the optimization.
              DexValue valueBeforePut = classInitializerDefaultsResult.getStaticValue(field);
              asStaticFieldValueAnalysis()
                  .updateFieldOptimizationInfoWith2Values(field, fieldPut.value(), valueBeforePut);
            }
            return;
          }
          updateFieldOptimizationInfo(field, fieldPut, fieldPut.value());
        });
  }

  private boolean fieldMaybeReadBeforeInstruction(DexClassAndField field, Instruction instruction) {
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

  private boolean fieldMaybeReadBeforeBlock(DexClassAndField field, BasicBlock block) {
    for (BasicBlock predecessor : block.getPredecessors()) {
      if (fieldMaybeReadBeforeBlockInclusive(field, predecessor)) {
        return true;
      }
    }
    return false;
  }

  private boolean fieldMaybeReadBeforeBlockInclusive(DexClassAndField field, BasicBlock block) {
    return getOrCreateFieldsMaybeReadBeforeBlockInclusive().get(block).contains(field);
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

  abstract void updateFieldOptimizationInfo(
      DexClassAndField field, FieldInstruction fieldPut, Value value);
}
