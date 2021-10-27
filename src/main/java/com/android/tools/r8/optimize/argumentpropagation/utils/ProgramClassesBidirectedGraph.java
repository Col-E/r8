// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.utils;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import java.util.function.Consumer;

public class ProgramClassesBidirectedGraph extends BidirectedGraph<DexProgramClass> {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final ImmediateProgramSubtypingInfo immediateSubtypingInfo;

  public ProgramClassesBidirectedGraph(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    this.appView = appView;
    this.immediateSubtypingInfo = immediateSubtypingInfo;
  }

  @Override
  public void forEachNeighbor(DexProgramClass node, Consumer<? super DexProgramClass> consumer) {
    immediateSubtypingInfo.forEachImmediateProgramSuperClass(node, consumer);
    immediateSubtypingInfo.getSubclasses(node).forEach(consumer);
  }

  @Override
  public void forEachNode(Consumer<? super DexProgramClass> consumer) {
    appView.appInfo().classes().forEach(consumer);
  }
}
