// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;

public class AppView<T extends AppInfo> {

  private T appInfo;
  private final DexItemFactory dexItemFactory;
  private GraphLense graphLense;

  public AppView(T appInfo, GraphLense graphLense) {
    this.appInfo = appInfo;
    this.dexItemFactory = appInfo.dexItemFactory;
    this.graphLense = graphLense;
  }

  public T getAppInfo() {
    return appInfo;
  }

  public void setAppInfo(T appInfo) {
    this.appInfo = appInfo;
  }

  public DexItemFactory getDexItemFactory() {
    return dexItemFactory;
  }

  public GraphLense getGraphLense() {
    return graphLense;
  }

  public void setGraphLense(GraphLense graphLense) {
    this.graphLense = graphLense;
  }

  public AppView<AppInfoWithLiveness> withLiveness() {
    return new AppViewWithLiveness();
  }

  private class AppViewWithLiveness extends AppView<AppInfoWithLiveness> {

    private AppViewWithLiveness() {
      super(null, null);
    }

    @Override
    public AppInfoWithLiveness getAppInfo() {
      return AppView.this.getAppInfo().withLiveness();
    }

    @Override
    public void setAppInfo(AppInfoWithLiveness appInfoWithLiveness) {
      @SuppressWarnings("unchecked")
      T appInfo = (T) appInfoWithLiveness;
      AppView.this.setAppInfo(appInfo);
    }

    @Override
    public GraphLense getGraphLense() {
      return AppView.this.getGraphLense();
    }

    @Override
    public void setGraphLense(GraphLense graphLense) {
      AppView.this.setGraphLense(graphLense);
    }

    @Override
    public AppView<AppInfoWithLiveness> withLiveness() {
      return this;
    }
  }
}
