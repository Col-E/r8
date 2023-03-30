// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess.readbeforewrite;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Value;

public abstract class FieldReadBeforeWriteAnalysis {

  public static FieldReadBeforeWriteAnalysis create(
      AppView<?> appView, IRCode code, ProgramMethod context) {
    if (appView.hasLiveness()) {
      return new FieldReadBeforeWriteAnalysisImpl(appView.withLiveness(), code, context);
    }
    return trivial();
  }

  public static TrivialFieldReadBeforeWriteAnalysis trivial() {
    return new TrivialFieldReadBeforeWriteAnalysis();
  }

  public abstract boolean isInstanceFieldMaybeReadBeforeInstruction(
      Value receiver, DexEncodedField field, Instruction instruction);

  public abstract boolean isStaticFieldMaybeReadBeforeInstruction(
      DexEncodedField field, Instruction instruction);
}
