// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.lightir.IR2LirConverter;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirStrategy;
import com.android.tools.r8.utils.Timing;

public class IRToLirFinalizer extends IRFinalizer<LirCode<Integer>> {

  public IRToLirFinalizer(AppView<?> appView, DeadCodeRemover deadCodeRemover) {
    super(appView, deadCodeRemover);
  }

  @Override
  public LirCode<Integer> finalizeCode(
      IRCode code, BytecodeMetadataProvider bytecodeMetadataProvider, Timing timing) {
    assert deadCodeRemover.verifyNoDeadCode(code);
    timing.begin("Finalize LIR code");
    LirCode<Integer> lirCode =
        IR2LirConverter.translate(
            code,
            bytecodeMetadataProvider,
            LirStrategy.getDefaultStrategy().getEncodingStrategy(),
            appView.options());
    timing.end();
    return lirCode;
  }
}
