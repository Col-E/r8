// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.shaking.VerticalClassMerger.VerticallyMergedClasses;
import com.android.tools.r8.utils.InternalOptions;

public class AppView<T extends AppInfo> {

  private T appInfo;
  private final DexItemFactory dexItemFactory;
  private GraphLense graphLense;
  private final InternalOptions options;
  private VerticallyMergedClasses verticallyMergedClasses;

  public AppView(T appInfo, GraphLense graphLense, InternalOptions options) {
    this.appInfo = appInfo;
    this.dexItemFactory = appInfo != null ? appInfo.dexItemFactory : null;
    this.graphLense = graphLense;
    this.options = options;
  }

  public T appInfo() {
    return appInfo;
  }

  public void setAppInfo(T appInfo) {
    this.appInfo = appInfo;
  }

  public DexItemFactory dexItemFactory() {
    return dexItemFactory;
  }

  // TODO(b/114469298): If we at some point replace all occurences of AppInfo with AppView,
  // then this method should return false when we are running with D8.
  public boolean enableWholeProgramOptimizations() {
    return true;
  }

  public GraphLense graphLense() {
    return graphLense;
  }

  public void setGraphLense(GraphLense graphLense) {
    this.graphLense = graphLense;
  }

  public InternalOptions options() {
    return options;
  }

  // Get the result of vertical class merging. Returns null if vertical class merging has not been
  // run.
  public VerticallyMergedClasses verticallyMergedClasses() {
    return verticallyMergedClasses;
  }

  public void setVerticallyMergedClasses(VerticallyMergedClasses verticallyMergedClasses) {
    this.verticallyMergedClasses = verticallyMergedClasses;
  }

  public AppView<AppInfoWithLiveness> withLiveness() {
    return new AppViewWithLiveness();
  }

  private class AppViewWithLiveness extends AppView<AppInfoWithLiveness> {

    private AppViewWithLiveness() {
      super(null, null, null);
    }

    @Override
    public AppInfoWithLiveness appInfo() {
      return AppView.this.appInfo().withLiveness();
    }

    @Override
    public void setAppInfo(AppInfoWithLiveness appInfoWithLiveness) {
      @SuppressWarnings("unchecked")
      T appInfo = (T) appInfoWithLiveness;
      AppView.this.setAppInfo(appInfo);
    }

    @Override
    public DexItemFactory dexItemFactory() {
      return AppView.this.dexItemFactory();
    }

    @Override
    public GraphLense graphLense() {
      return AppView.this.graphLense();
    }

    @Override
    public void setGraphLense(GraphLense graphLense) {
      AppView.this.setGraphLense(graphLense);
    }

    @Override
    public InternalOptions options() {
      return AppView.this.options();
    }

    @Override
    public AppView<AppInfoWithLiveness> withLiveness() {
      return this;
    }
  }
}
