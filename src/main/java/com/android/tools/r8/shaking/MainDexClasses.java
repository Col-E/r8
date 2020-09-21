// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.function.Consumer;

public class MainDexClasses {

  private final Set<DexType> mainDexClasses;

  private MainDexClasses() {
    this(Sets.newIdentityHashSet());
  }

  private MainDexClasses(Set<DexType> mainDexClasses) {
    this.mainDexClasses = mainDexClasses;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static MainDexClasses createEmptyMainDexClasses() {
    return new MainDexClasses();
  }

  public void add(DexProgramClass clazz) {
    mainDexClasses.add(clazz.getType());
  }

  public void addAll(MainDexClasses other) {
    mainDexClasses.addAll(other.mainDexClasses);
  }

  public void addAll(MainDexTracingResult other) {
    mainDexClasses.addAll(other.getClasses());
  }

  public void addAll(Iterable<DexProgramClass> classes) {
    for (DexProgramClass clazz : classes) {
      add(clazz);
    }
  }

  public boolean contains(DexType type) {
    return mainDexClasses.contains(type);
  }

  public boolean contains(DexProgramClass clazz) {
    return contains(clazz.getType());
  }

  public boolean containsAnyOf(Iterable<DexProgramClass> classes) {
    for (DexProgramClass clazz : classes) {
      if (contains(clazz)) {
        return true;
      }
    }
    return false;
  }

  public void forEach(Consumer<DexType> fn) {
    mainDexClasses.forEach(fn);
  }

  public boolean isEmpty() {
    return mainDexClasses.isEmpty();
  }

  public MainDexClasses rewrittenWithLens(GraphLens lens) {
    MainDexClasses rewrittenMainDexClasses = createEmptyMainDexClasses();
    for (DexType mainDexClass : mainDexClasses) {
      DexType rewrittenMainDexClass = lens.lookupType(mainDexClass);
      rewrittenMainDexClasses.mainDexClasses.add(rewrittenMainDexClass);
    }
    return rewrittenMainDexClasses;
  }

  public int size() {
    return mainDexClasses.size();
  }

  public MainDexClasses withoutPrunedClasses(Set<DexType> prunedClasses) {
    MainDexClasses mainDexClassesAfterPruning = createEmptyMainDexClasses();
    for (DexType mainDexClass : mainDexClasses) {
      if (!prunedClasses.contains(mainDexClass)) {
        mainDexClassesAfterPruning.mainDexClasses.add(mainDexClass);
      }
    }
    return mainDexClassesAfterPruning;
  }

  public static class Builder {

    private final Set<DexType> mainDexClasses = Sets.newIdentityHashSet();

    private Builder() {}

    public void add(DexProgramClass clazz) {
      mainDexClasses.add(clazz.getType());
    }

    public MainDexClasses build() {
      return new MainDexClasses(mainDexClasses);
    }
  }
}
