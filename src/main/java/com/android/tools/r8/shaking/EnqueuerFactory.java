// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.experimental.graphinfo.GraphConsumer;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.shaking.Enqueuer.Mode;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class EnqueuerFactory {

  public static Enqueuer createForInitialTreeShaking(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ExecutorService executorService,
      SubtypingInfo subtypingInfo) {
    return new Enqueuer(appView, executorService, subtypingInfo, null, Mode.INITIAL_TREE_SHAKING);
  }

  public static Enqueuer createForFinalTreeShaking(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ExecutorService executorService,
      SubtypingInfo subtypingInfo,
      GraphConsumer keptGraphConsumer,
      Set<DexType> initialPrunedTypes) {
    Enqueuer enqueuer =
        new Enqueuer(
            appView, executorService, subtypingInfo, keptGraphConsumer, Mode.FINAL_TREE_SHAKING);
    appView.withProtoShrinker(
        shrinker -> enqueuer.setInitialDeadProtoTypes(shrinker.getDeadProtoTypes()));
    enqueuer.setInitialPrunedTypes(initialPrunedTypes);
    return enqueuer;
  }

  public static Enqueuer createForInitialMainDexTracing(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ExecutorService executorService,
      SubtypingInfo subtypingInfo) {
    return new Enqueuer(
        appView, executorService, subtypingInfo, null, Mode.INITIAL_MAIN_DEX_TRACING);
  }

  public static Enqueuer createForFinalMainDexTracing(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ExecutorService executorService,
      SubtypingInfo subtypingInfo,
      GraphConsumer keptGraphConsumer) {
    return new Enqueuer(
        appView, executorService, subtypingInfo, keptGraphConsumer, Mode.FINAL_MAIN_DEX_TRACING);
  }

  public static Enqueuer createForWhyAreYouKeeping(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ExecutorService executorService,
      SubtypingInfo subtypingInfo,
      GraphConsumer keptGraphConsumer) {
    return new Enqueuer(
        appView, executorService, subtypingInfo, keptGraphConsumer, Mode.WHY_ARE_YOU_KEEPING);
  }
}
