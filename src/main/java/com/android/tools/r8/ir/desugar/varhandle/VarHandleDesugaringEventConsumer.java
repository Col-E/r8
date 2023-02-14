// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.varhandle;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramDefinition;

public interface VarHandleDesugaringEventConsumer {

  void acceptVarHandleDesugaringClass(DexProgramClass clazz);

  void acceptVarHandleDesugaringClassContext(DexProgramClass clazz, ProgramDefinition context);
}
