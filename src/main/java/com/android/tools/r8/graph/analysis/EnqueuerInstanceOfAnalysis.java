// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;

public interface EnqueuerInstanceOfAnalysis {
  void traceInstanceOf(DexType type, ProgramMethod context);
}
