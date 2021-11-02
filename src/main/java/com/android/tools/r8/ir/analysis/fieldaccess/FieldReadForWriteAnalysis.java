// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class FieldReadForWriteAnalysis {

  private final AppView<AppInfoWithLiveness> appView;

  FieldReadForWriteAnalysis(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public void recordFieldAccess(
      FieldInstruction instruction,
      ProgramField field,
      BytecodeMetadataProvider.Builder bytecodeMetadataProviderBuilder) {
    // TODO(b/149673849): Determine if field read is only used for field write.
  }
}
