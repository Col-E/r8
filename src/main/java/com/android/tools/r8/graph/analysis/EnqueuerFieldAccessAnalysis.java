// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;

public interface EnqueuerFieldAccessAnalysis {

  void traceInstanceFieldRead(
      DexField field, FieldResolutionResult resolutionResult, ProgramMethod context);

  void traceInstanceFieldWrite(
      DexField field, FieldResolutionResult resolutionResult, ProgramMethod context);

  void traceStaticFieldRead(
      DexField field, FieldResolutionResult resolutionResult, ProgramMethod context);

  void traceStaticFieldWrite(
      DexField field, FieldResolutionResult resolutionResult, ProgramMethod context);
}
