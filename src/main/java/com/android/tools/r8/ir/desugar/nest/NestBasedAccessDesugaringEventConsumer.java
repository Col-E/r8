// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.nest;

import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;

public interface NestBasedAccessDesugaringEventConsumer {

  void acceptNestFieldGetBridge(ProgramField target, ProgramMethod bridge);

  void acceptNestFieldPutBridge(ProgramField target, ProgramMethod bridge);

  void acceptNestMethodBridge(ProgramMethod target, ProgramMethod bridge);
}
