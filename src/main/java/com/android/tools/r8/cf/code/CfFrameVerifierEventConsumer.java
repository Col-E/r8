// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code;

import com.android.tools.r8.graph.CfCodeDiagnostics;
import com.android.tools.r8.optimize.interfaces.analysis.CfFrameState;

public interface CfFrameVerifierEventConsumer {

  default void acceptError(CfCodeDiagnostics diagnostics) {}

  default void acceptInstructionState(CfInstruction instruction, CfFrameState state) {}
}
