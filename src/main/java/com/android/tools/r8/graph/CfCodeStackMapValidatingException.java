// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.cf.code.CfInstruction;

public class CfCodeStackMapValidatingException extends RuntimeException {

  private CfCodeStackMapValidatingException(String message) {
    super(message);
  }

  public static CfCodeStackMapValidatingException error(String message) {
    return new CfCodeStackMapValidatingException(message);
  }

  public static CfCodeDiagnostics unexpectedStackMapFrame(
      ProgramMethod method, AppView<?> appView) {
    StringBuilder sb = new StringBuilder("Unexpected stack map frame without target");
    if (appView.enableWholeProgramOptimizations()) {
      sb.append(" In later version of R8, the method may be assumed not reachable.");
    }
    return new CfCodeDiagnostics(
        method.getOrigin(),
        appView.graphLens().getOriginalMethodSignature(method.getReference()),
        sb.toString());
  }

  public static CfCodeDiagnostics multipleFramesForLabel(ProgramMethod method, AppView<?> appView) {
    StringBuilder sb = new StringBuilder("Multiple frames for label");
    if (appView.enableWholeProgramOptimizations()) {
      sb.append(" In later version of R8, the method may be assumed not reachable.");
    }
    return new CfCodeDiagnostics(
        method.getOrigin(),
        appView.graphLens().getOriginalMethodSignature(method.getReference()),
        sb.toString());
  }

  public static CfCodeDiagnostics noFramesForMethodWithJumps(
      ProgramMethod method, AppView<?> appView) {
    StringBuilder sb =
        new StringBuilder("Expected stack map table for method with non-linear control flow.");
    if (appView.enableWholeProgramOptimizations()) {
      sb.append(" In later version of R8, the method may be assumed not reachable.");
    }
    return new CfCodeDiagnostics(
        method.getOrigin(),
        appView.graphLens().getOriginalMethodSignature(method.getReference()),
        sb.toString());
  }

  public static CfCodeDiagnostics toDiagnostics(
      ProgramMethod method,
      int instructionIndex,
      CfInstruction instruction,
      String detailMessage,
      AppView<?> appView) {
    StringBuilder sb =
        new StringBuilder("Invalid stack map table at ")
            .append(instructionIndex)
            .append(": ")
            .append(instruction)
            .append(", error: ")
            .append(detailMessage)
            .append(".");
    if (appView.enableWholeProgramOptimizations()) {
      sb.append(" In later version of R8, the method may be assumed not reachable.");
    }
    return new CfCodeDiagnostics(
        method.getOrigin(),
        appView.graphLens().getOriginalMethodSignature(method.getReference()),
        sb.toString());
  }
}
