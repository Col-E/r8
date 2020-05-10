// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.optimize.ClassInitializerDefaultsOptimization.ClassInitializerDefaultsResult;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;

public class FieldAccessAnalysis {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final FieldAssignmentTracker fieldAssignmentTracker;
  private final FieldBitAccessAnalysis fieldBitAccessAnalysis;

  public FieldAccessAnalysis(AppView<AppInfoWithLiveness> appView) {
    InternalOptions options = appView.options();
    this.appView = appView;
    this.fieldBitAccessAnalysis =
        options.enableFieldBitAccessAnalysis ? new FieldBitAccessAnalysis() : null;
    this.fieldAssignmentTracker =
        options.enableFieldAssignmentTracker ? new FieldAssignmentTracker(appView) : null;
  }

  public FieldAccessAnalysis(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      FieldAssignmentTracker fieldAssignmentTracker,
      FieldBitAccessAnalysis fieldBitAccessAnalysis) {
    this.appView = appView;
    this.fieldAssignmentTracker = fieldAssignmentTracker;
    this.fieldBitAccessAnalysis = fieldBitAccessAnalysis;
  }

  public static boolean enable(InternalOptions options) {
    return options.enableFieldBitAccessAnalysis || options.enableFieldAssignmentTracker;
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
      IRCode code, OptimizationFeedback feedback, MethodProcessor methodProcessor) {
    if (!methodProcessor.isPrimary()) {
      return;
    }

    if (!code.metadata().mayHaveFieldInstruction() && !code.metadata().mayHaveNewInstance()) {
      return;
    }

    for (Instruction instruction : code.instructions()) {
      if (instruction.isFieldInstruction()) {
        FieldInstruction fieldInstruction = instruction.asFieldInstruction();
        FieldResolutionResult resolutionResult =
            appView.appInfo().resolveField(fieldInstruction.getField());
        if (resolutionResult.isSuccessfulResolution()) {
          ProgramField field =
              resolutionResult.asSuccessfulResolution().getResolutionPair().asProgramField();
          if (field != null) {
            if (fieldAssignmentTracker != null) {
              fieldAssignmentTracker.recordFieldAccess(
                  fieldInstruction, field.getDefinition(), code.context());
            }
            if (fieldBitAccessAnalysis != null) {
              fieldBitAccessAnalysis.recordFieldAccess(
                  fieldInstruction, field.getDefinition(), feedback);
            }
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
