// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.optimize.ClassInitializerDefaultsOptimization.ClassInitializerDefaultsResult;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.annotations.VisibleForTesting;

public class FieldAccessAnalysis {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final FieldAssignmentTracker fieldAssignmentTracker;
  private final FieldBitAccessAnalysis fieldBitAccessAnalysis;
  private final FieldReadForInvokeReceiverAnalysis fieldReadForInvokeReceiverAnalysis;
  private final FieldReadForWriteAnalysis fieldReadForWriteAnalysis;

  public FieldAccessAnalysis(AppView<AppInfoWithLiveness> appView) {
    InternalOptions options = appView.options();
    this.appView = appView;
    this.fieldBitAccessAnalysis =
        options.enableFieldBitAccessAnalysis ? new FieldBitAccessAnalysis() : null;
    this.fieldAssignmentTracker = new FieldAssignmentTracker(appView);
    this.fieldReadForInvokeReceiverAnalysis = new FieldReadForInvokeReceiverAnalysis(appView);
    this.fieldReadForWriteAnalysis = new FieldReadForWriteAnalysis(appView);
  }

  @VisibleForTesting
  public FieldAccessAnalysis(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      FieldAssignmentTracker fieldAssignmentTracker,
      FieldBitAccessAnalysis fieldBitAccessAnalysis,
      FieldReadForInvokeReceiverAnalysis fieldReadForInvokeReceiverAnalysis,
      FieldReadForWriteAnalysis fieldReadForWriteAnalysis) {
    this.appView = appView;
    this.fieldAssignmentTracker = fieldAssignmentTracker;
    this.fieldBitAccessAnalysis = fieldBitAccessAnalysis;
    this.fieldReadForInvokeReceiverAnalysis = fieldReadForInvokeReceiverAnalysis;
    this.fieldReadForWriteAnalysis = fieldReadForWriteAnalysis;
  }

  public FieldAssignmentTracker fieldAssignmentTracker() {
    return fieldAssignmentTracker;
  }

  public void acceptClassInitializerDefaultsResult(
      ClassInitializerDefaultsResult classInitializerDefaultsResult) {
    if (fieldAssignmentTracker != null) {
      fieldAssignmentTracker.acceptClassInitializerDefaultsResult(classInitializerDefaultsResult);
    }
  }

  public void recordFieldAccesses(
      IRCode code,
      BytecodeMetadataProvider.Builder bytecodeMetadataProviderBuilder,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor) {
    if (!methodProcessor.isPrimaryMethodProcessor()) {
      return;
    }

    if (!code.metadata().mayHaveFieldInstruction() && !code.metadata().mayHaveNewInstance()) {
      return;
    }

    for (Instruction instruction : code.instructions()) {
      if (instruction.isFieldInstruction()) {
        FieldInstruction fieldInstruction = instruction.asFieldInstruction();
        ProgramField field =
            appView.appInfo().resolveField(fieldInstruction.getField()).getProgramField();
        if (field != null) {
          if (fieldAssignmentTracker != null) {
            fieldAssignmentTracker.recordFieldAccess(fieldInstruction, field, code.context());
          }
          if (fieldBitAccessAnalysis != null) {
            fieldBitAccessAnalysis.recordFieldAccess(
                fieldInstruction, field.getDefinition(), feedback);
          }
          if (fieldReadForInvokeReceiverAnalysis != null) {
            fieldReadForInvokeReceiverAnalysis.recordFieldAccess(
                fieldInstruction, field, bytecodeMetadataProviderBuilder, code.context());
          }
          if (fieldReadForWriteAnalysis != null) {
            fieldReadForWriteAnalysis.recordFieldAccess(
                fieldInstruction, field, bytecodeMetadataProviderBuilder);
          }
        }
      } else if (instruction.isNewInstance()) {
        NewInstance newInstance = instruction.asNewInstance();
        DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(newInstance.clazz));
        if (clazz != null) {
          if (fieldAssignmentTracker != null) {
            fieldAssignmentTracker.recordAllocationSite(newInstance, clazz, code.context());
          }
        }
      }
    }
  }
}
