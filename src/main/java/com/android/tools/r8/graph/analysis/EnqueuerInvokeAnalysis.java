// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;

public interface EnqueuerInvokeAnalysis {

  /**
   * Each traceInvokeXX method is called when a corresponding invoke is found while tracing a live
   * method.
   */
  void traceInvokeStatic(DexMethod invokedMethod, ProgramMethod context);

  void traceInvokeDirect(DexMethod invokedMethod, ProgramMethod context);

  void traceInvokeInterface(DexMethod invokedMethod, ProgramMethod context);

  void traceInvokeSuper(DexMethod invokedMethod, ProgramMethod context);

  void traceInvokeVirtual(DexMethod invokedMethod, ProgramMethod context);
}
