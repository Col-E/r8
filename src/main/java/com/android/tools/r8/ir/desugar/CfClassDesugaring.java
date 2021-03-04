// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.DexProgramClass;

/** Interface for desugaring a class. */
public interface CfClassDesugaring {

  void desugar(DexProgramClass clazz, CfClassDesugaringEventConsumer eventConsumer);

  /** Returns true if the given class needs desugaring. */
  boolean needsDesugaring(DexProgramClass clazz);
}
