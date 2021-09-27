// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.utils.InternalOptions;

public class NoCheckDiscard extends SingleClassPolicy {

  private final KeepInfoCollection keepInfo;
  private final InternalOptions options;

  public NoCheckDiscard(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.keepInfo = appView.getKeepInfo();
    this.options = appView.options();
  }

  @Override
  public boolean canMerge(DexProgramClass clazz) {
    return !keepInfo.getClassInfo(clazz).isCheckDiscardedEnabled(options);
  }

  @Override
  public String getName() {
    return "NoCheckDiscard";
  }
}
