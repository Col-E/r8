// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.shaking.KeepMethodInfo.Joiner;

public class VirtuallyMergedMethodsKeepInfo {

  private final DexMethod representative;
  private final KeepMethodInfo.Joiner keepInfo = KeepMethodInfo.newEmptyJoiner();

  public VirtuallyMergedMethodsKeepInfo(DexMethod representative) {
    this.representative = representative;
  }

  public void amendKeepInfo(KeepMethodInfo keepInfo) {
    this.keepInfo.merge(keepInfo.joiner());
  }

  public DexMethod getRepresentative() {
    return representative;
  }

  public Joiner getKeepInfo() {
    return keepInfo;
  }
}
