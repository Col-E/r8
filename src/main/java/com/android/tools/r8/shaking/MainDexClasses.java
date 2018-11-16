// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class MainDexClasses {

  public static class Builder {
    public final AppInfo appInfo;
    public final Set<DexType> roots = Sets.newIdentityHashSet();
    public final Set<DexType> dependencies = Sets.newIdentityHashSet();

    private Builder(AppInfo appInfo) {
      this.appInfo = appInfo;
    }

    public Builder addRoots(Collection<DexType> rootSet) {
      assert rootSet.stream().allMatch(this::isProgramClass);
      this.roots.addAll(rootSet);
      return this;
    }

    public Builder addDependency(DexType type) {
      assert isProgramClass(type);
      dependencies.add(type);
      return this;
    }

    public boolean contains(DexType type) {
      return roots.contains(type) || dependencies.contains(type);
    }

    public MainDexClasses build() {
      return new MainDexClasses(roots, dependencies);
    }

    private boolean isProgramClass(DexType dexType) {
      DexClass clazz = appInfo.definitionFor(dexType);
      return clazz != null && clazz.isProgramClass();
    }
  }

  // The classes in the root set.
  private final Set<DexType> rootSet;
  // Additional dependencies (direct dependencise and runtime annotations with enums).
  private final Set<DexType> dependencies;
  // All main dex classes.
  private final Set<DexType> classes;

  private MainDexClasses(Set<DexType> rootSet, Set<DexType> dependencies) {
    assert Sets.intersection(rootSet, dependencies).isEmpty();
    this.rootSet = Collections.unmodifiableSet(rootSet);
    this.dependencies = Collections.unmodifiableSet(dependencies);
    this.classes = Sets.union(rootSet, dependencies);
  }

  public Set<DexType> getRoots() {
    return rootSet;
  }

  public Set<DexType> getDependencies() {
    return dependencies;
  }

  public Set<DexType> getClasses() {
    return classes;
  }

  public static Builder builder(AppInfo appInfo) {
    return new Builder(appInfo);
  }
}
