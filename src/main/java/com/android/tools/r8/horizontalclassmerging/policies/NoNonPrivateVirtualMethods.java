// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;

public class NoNonPrivateVirtualMethods extends SingleClassPolicy {

  public NoNonPrivateVirtualMethods(Mode mode) {
    // TODO(b/181846319): Allow virtual methods as long as they do not require any merging.
    assert mode.isFinal();
  }

  @Override
  public boolean canMerge(DexProgramClass clazz) {
    return clazz.isInterface() || !clazz.getMethodCollection().hasVirtualMethods();
  }

  @Override
  public String getName() {
    return "NoNonPrivateVirtualMethods";
  }
}
