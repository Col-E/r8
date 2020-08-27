// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;

public class NotEntryPoint extends SingleClassPolicy {
  private final DexString main;

  public NotEntryPoint(DexItemFactory factory) {
    main = factory.createString("main");
  }

  @Override
  public boolean canMerge(DexProgramClass program) {
    // TODO(b/165000217): Account for keep rules instead.
    for (DexEncodedMethod method : program.directMethods()) {
      if (method.method.name.equals(main)) {
        return false;
      }
    }
    return true;
  }
}
