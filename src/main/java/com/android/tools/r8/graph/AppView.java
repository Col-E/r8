// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.OptionalBool;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.RootSetBuilder.RootSet;
import com.android.tools.r8.shaking.VerticalClassMerger.VerticallyMergedClasses;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableSet;
import java.util.Set;

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
  private RootSet rootSet;
  private Set<DexMethod> unneededVisibilityBridgeMethods = ImmutableSet.of();
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

  public <U extends T> AppView<U> setAppInfo(U appInfo) {
    assert !appInfo.isObsolete();
    AppInfo previous = this.appInfo;
    this.appInfo = appInfo;
    if (appInfo != previous) {
      previous.markObsolete();
    }
    @SuppressWarnings("unchecked")
    AppView<U> appViewWithSpecializedAppInfo = (AppView<U>) this;
    return appViewWithSpecializedAppInfo;
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

  public OptionalBool isInterface(DexType type) {
    // Without whole program information we should not assume anything about any other class than
    // the current holder in a given context.
    if (enableWholeProgramOptimizations()) {
      assert appInfo().hasSubtyping();
      if (appInfo().hasSubtyping()) {
        AppInfoWithSubtyping appInfo = appInfo().withSubtyping();
        return appInfo.isUnknown(type)
            ? OptionalBool.unknown()
            : OptionalBool.of(appInfo.isMarkedAsInterface(type));
      }
    }
    return OptionalBool.unknown();
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

  public RootSet rootSet() {
    return rootSet;
  }

  public void setRootSet(RootSet rootSet) {
    assert this.rootSet == null : "Root set should never be recomputed";
    this.rootSet = rootSet;
  }

  public Set<DexMethod> unneededVisibilityBridgeMethods() {
    return unneededVisibilityBridgeMethods;
  }

  public void setUnneededVisibilityBridgeMethods(Set<DexMethod> unneededVisibilityBridgeMethods) {
    this.unneededVisibilityBridgeMethods = unneededVisibilityBridgeMethods;
  }

  // Get the result of vertical class merging. Returns null if vertical class merging has not been
  // run.
  public VerticallyMergedClasses verticallyMergedClasses() {
    return verticallyMergedClasses;
  }

  public void setVerticallyMergedClasses(VerticallyMergedClasses verticallyMergedClasses) {
    this.verticallyMergedClasses = verticallyMergedClasses;
  }

  @SuppressWarnings("unchecked")
  public AppView<AppInfoWithSubtyping> withSubtyping() {
    return appInfo.hasSubtyping()
        ? (AppView<AppInfoWithSubtyping>) this
        : null;
  }

  public AppView<AppInfoWithLiveness> withLiveness() {
    @SuppressWarnings("unchecked")
    AppView<AppInfoWithLiveness> appViewWithLiveness = (AppView<AppInfoWithLiveness>) this;
    return appViewWithLiveness;
  }

  public OptionalBool isSubtype(DexType subtype, DexType supertype) {
    return appInfo().hasSubtyping()
        ? OptionalBool.of(appInfo().withSubtyping().isSubtype(subtype, supertype))
        : OptionalBool.unknown();
  }
}
