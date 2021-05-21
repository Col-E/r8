// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.code;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.horizontalclassmerging.MergeGroup;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackIgnore;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Converts synthetic class initializers that have been created as a result of merging class
 * initializers into a single class initializer to DEX.
 */
public class SyntheticClassInitializerConverter {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final List<MergeGroup> groups;

  private SyntheticClassInitializerConverter(
      AppView<? extends AppInfoWithClassHierarchy> appView, List<MergeGroup> groups) {
    this.appView = appView;
    this.groups = groups;
  }

  public static Builder builder(AppView<? extends AppInfoWithClassHierarchy> appView) {
    return new Builder(appView);
  }

  public void convert(ExecutorService executorService) throws ExecutionException {
    // At this point the code rewritings described by repackaging and synthetic finalization have
    // not been applied to the code objects. These code rewritings will be applied in the
    // application writer. We therefore simulate that we are in D8, to allow building IR for each of
    // the class initializers without applying the unapplied code rewritings, to avoid that we apply
    // the lens more than once to the same piece of code.
    AppView<AppInfo> appViewForConversion =
        AppView.createForD8(AppInfo.createInitialAppInfo(appView.appInfo().app()));
    appViewForConversion.setGraphLens(appView.graphLens());

    // Build IR for each of the class initializers and finalize.
    IRConverter converter = new IRConverter(appViewForConversion, Timing.empty());
    ThreadUtils.processItems(
        groups,
        group -> {
          ProgramMethod classInitializer = group.getTarget().getProgramClassInitializer();
          IRCode code =
              classInitializer
                  .getDefinition()
                  .getCode()
                  .buildIR(classInitializer, appViewForConversion, classInitializer.getOrigin());
          converter.removeDeadCodeAndFinalizeIR(
              code, OptimizationFeedbackIgnore.getInstance(), Timing.empty());
        },
        executorService);
  }

  public boolean isEmpty() {
    return groups.isEmpty();
  }

  public static class Builder {

    private final AppView<? extends AppInfoWithClassHierarchy> appView;
    private final List<MergeGroup> groups = new ArrayList<>();

    private Builder(AppView<? extends AppInfoWithClassHierarchy> appView) {
      this.appView = appView;
    }

    public Builder add(MergeGroup group) {
      this.groups.add(group);
      return this;
    }

    public SyntheticClassInitializerConverter build() {
      return new SyntheticClassInitializerConverter(appView, groups);
    }
  }
}
