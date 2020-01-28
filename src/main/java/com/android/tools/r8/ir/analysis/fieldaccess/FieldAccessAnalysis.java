// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.optimize.ClassInitializerDefaultsOptimization.ClassInitializerDefaultsResult;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;

public class FieldAccessAnalysis {

  private final AppView<?> appView;
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
      AppView<?> appView,
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
    if (!code.metadata().mayHaveFieldInstruction() || !methodProcessor.isPrimary()) {
      return;
    }

    Iterable<FieldInstruction> fieldInstructions =
        code.instructions(Instruction::isFieldInstruction);
    for (FieldInstruction fieldInstruction : fieldInstructions) {
      DexEncodedField encodedField = appView.appInfo().resolveField(fieldInstruction.getField());
      if (encodedField != null && encodedField.isProgramField(appView)) {
        if (fieldAssignmentTracker != null) {
          fieldAssignmentTracker.recordFieldAccess(fieldInstruction, encodedField, code.method);
        }
        if (fieldBitAccessAnalysis != null) {
          fieldBitAccessAnalysis.recordFieldAccess(fieldInstruction, encodedField, feedback);
        }
      }
    }
  }
}
