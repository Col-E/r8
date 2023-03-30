// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldvalueanalysis;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.fieldaccess.readbeforewrite.FieldReadBeforeWriteAnalysis;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.DominatorTree;
import com.android.tools.r8.ir.code.DominatorTree.Assumption;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.ClassInitializerDefaultsOptimization.ClassInitializerDefaultsResult;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfo;
import com.android.tools.r8.ir.optimize.info.field.UnknownInstanceFieldInitializationInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ListUtils;
import java.util.ArrayList;
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
  private FieldReadBeforeWriteAnalysis fieldReadBeforeWriteAnalysis;

  final Map<DexEncodedField, List<FieldInitializationInfo>> putsPerField = new IdentityHashMap<>();

  FieldValueAnalysis(
      AppView<AppInfoWithLiveness> appView,
      IRCode code,
      OptimizationFeedback feedback,
      FieldReadBeforeWriteAnalysis fieldReadBeforeWriteAnalysis) {
    this.appView = appView;
    this.code = code;
    this.context = code.context();
    this.feedback = feedback;
    this.fieldReadBeforeWriteAnalysis = fieldReadBeforeWriteAnalysis;
  }

  DominatorTree getOrCreateDominatorTree() {
    if (dominatorTree == null) {
      dominatorTree = new DominatorTree(code, Assumption.NO_UNREACHABLE_BLOCKS);
    }
    return dominatorTree;
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

  abstract boolean isSubjectToOptimizationIgnoringPinning(ProgramField field);

  abstract boolean isSubjectToOptimization(ProgramField field);

  void recordFieldPut(DexEncodedField field, Instruction instruction) {
    recordFieldPut(field, instruction, UnknownInstanceFieldInitializationInfo.getInstance());
  }

  void recordFieldPut(
      DexEncodedField field, Instruction instruction, InstanceFieldInitializationInfo info) {
    putsPerField
        .computeIfAbsent(field, ignore -> new ArrayList<>())
        .add(new FieldInitializationInfo(instruction, info));
  }

  /** This method analyzes initializers with the purpose of computing field optimization info. */
  void computeFieldOptimizationInfo(ClassInitializerDefaultsResult classInitializerDefaultsResult) {
    AppInfoWithLiveness appInfo = appView.appInfo();

    // Find all the static-put instructions that assign a field in the enclosing class which is
    // guaranteed to be assigned only in the current initializer.
    boolean isStraightLineCode = true;
    for (BasicBlock block : code.blocks) {
      if (block.getSuccessors().size() >= 2) {
        isStraightLineCode = false;
      }
      for (Instruction instruction : block.getInstructions()) {
        if (instruction.isFieldPut()) {
          FieldInstruction fieldPut = instruction.asFieldInstruction();
          DexField field = fieldPut.getField();
          ProgramField programField = appInfo.resolveField(field).getProgramField();
          if (programField != null) {
            if (isSubjectToOptimization(programField)) {
              recordFieldPut(programField.getDefinition(), fieldPut);
            } else if (isStaticFieldValueAnalysis()
                && programField.getHolder().isEnum()
                && isSubjectToOptimizationIgnoringPinning(programField)) {
              recordFieldPut(programField.getDefinition(), fieldPut);
            }
          }
        } else if (isInstanceFieldValueAnalysis()
            && instruction.isInvokeConstructor(appView.dexItemFactory())) {
          InvokeDirect invoke = instruction.asInvokeDirect();
          asInstanceFieldValueAnalysis().analyzeForwardingConstructorCall(invoke, code.getThis());
        }
      }
    }

    boolean checkDominance = !isStraightLineCode;
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
          if (checkDominance
              && !getOrCreateDominatorTree()
                  .dominatesAllOf(fieldPut.getBlock(), normalExitBlocks)) {
            return;
          }
          boolean priorReadsWillReadSameValue =
              !classInitializerDefaultsResult.hasStaticValue(field) && fieldPut.value().isZero();
          if (!priorReadsWillReadSameValue) {
            if (fieldPut.isInstancePut()) {
              InstancePut instancePut = fieldPut.asInstancePut();
              if (fieldReadBeforeWriteAnalysis.isInstanceFieldMaybeReadBeforeInstruction(
                  instancePut.object(), field, instancePut)) {
                return;
              }
            } else {
              if (fieldReadBeforeWriteAnalysis.isStaticFieldMaybeReadBeforeInstruction(
                  field, fieldPut)) {
                // TODO(b/172528424): Generalize to InstanceFieldValueAnalysis.
                // At this point the value read in the field can be only the default static value,
                // if read prior to the put, or the value put, if read after the put. We still want
                // to record it because the default static value is typically null/0, so code
                // present after a null/0 check can take advantage of the optimization.
                DexValue valueBeforePut = classInitializerDefaultsResult.getStaticValue(field);
                asStaticFieldValueAnalysis()
                    .updateFieldOptimizationInfoWith2Values(
                        field, fieldPut.value(), valueBeforePut);
                return;
              }
            }
          }
          updateFieldOptimizationInfo(field, fieldPut, fieldPut.value());
        });
  }

  abstract void updateFieldOptimizationInfo(
      DexEncodedField field, FieldInstruction fieldPut, Value value);
}
