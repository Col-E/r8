// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.VerticalClassMerger.VerticallyMergedClasses;
import com.android.tools.r8.utils.InternalOptions;

public class AppView<T extends AppInfo> implements DexDefinitionSupplier {

  private enum WholeProgramOptimizations {
    ON,
    OFF
  }

  private T appInfo;
  private AppServices appServices;
  private final DexItemFactory dexItemFactory;
  private final WholeProgramOptimizations wholeProgramOptimizations;
  private GraphLense graphLense;
  private final InternalOptions options;
  private VerticallyMergedClasses verticallyMergedClasses;

  private AppView(
      T appInfo, WholeProgramOptimizations wholeProgramOptimizations, InternalOptions options) {
    this.appInfo = appInfo;
    this.dexItemFactory = appInfo != null ? appInfo.dexItemFactory() : null;
    this.wholeProgramOptimizations = wholeProgramOptimizations;
    this.graphLense = GraphLense.getIdentityLense();
    this.options = options;
  }

  public static <T extends AppInfo> AppView<T> createForD8(T appInfo, InternalOptions options) {
    return new AppView<>(appInfo, WholeProgramOptimizations.OFF, options);
  }

  public static <T extends AppInfo> AppView<T> createForR8(T appInfo, InternalOptions options) {
    return new AppView<>(appInfo, WholeProgramOptimizations.ON, options);
  }

  public T appInfo() {
    return appInfo;
  }

  public void setAppInfo(T appInfo) {
    assert !appInfo.isObsolete();
    AppInfo previous = this.appInfo;
    this.appInfo = appInfo;
    if (appInfo != previous) {
      previous.markObsolete();
    }
  }

  public AppServices appServices() {
    return appServices;
  }

  public void setAppServices(AppServices appServices) {
    this.appServices = appServices;
  }

  @Override
  public final DexDefinition definitionFor(DexReference reference) {
    return appInfo().definitionFor(reference);
  }

  @Override
  public final DexEncodedField definitionFor(DexField field) {
    return appInfo().definitionFor(field);
  }

  @Override
  public final DexEncodedMethod definitionFor(DexMethod method) {
    return appInfo().definitionFor(method);
  }

  @Override
  public final DexClass definitionFor(DexType type) {
    return appInfo().definitionFor(type);
  }

  @Override
  public DexItemFactory dexItemFactory() {
    return dexItemFactory;
  }

  public boolean enableWholeProgramOptimizations() {
    return wholeProgramOptimizations == WholeProgramOptimizations.ON;
  }

  public GraphLense graphLense() {
    return graphLense;
  }

  /** @return true if the graph lens changed, otherwise false. */
  public boolean setGraphLense(GraphLense graphLense) {
    if (graphLense != this.graphLense) {
      this.graphLense = graphLense;
      return true;
    }
    return false;
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
    @SuppressWarnings("unchecked")
    AppView<AppInfoWithLiveness> appViewWithLiveness = (AppView<AppInfoWithLiveness>) this;
    return appViewWithLiveness;
  }
}
