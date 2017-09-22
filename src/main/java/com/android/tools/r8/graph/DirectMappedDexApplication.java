// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.utils.ProgramClassCollection;
import com.android.tools.r8.utils.Timing;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

public class DirectMappedDexApplication extends DexApplication {

  private final ImmutableMap<DexType, DexLibraryClass> libraryClasses;

  private DirectMappedDexApplication(ClassNameMapper proguardMap,
      ProgramClassCollection programClasses,
      ImmutableMap<DexType, DexLibraryClass> libraryClasses,
      ImmutableSet<DexType> mainDexList, byte[] deadCode,
      DexItemFactory dexItemFactory, DexString highestSortingString,
      Timing timing) {
    super(proguardMap, programClasses, mainDexList, deadCode,
        dexItemFactory, highestSortingString, timing);
    this.libraryClasses = libraryClasses;
  }

  public Collection<DexLibraryClass> libraryClasses() {
    return libraryClasses.values();
  }

  @Override
  public DexClass definitionFor(DexType type) {
    DexClass result = programClasses.get(type);
    if (result == null) {
      result = libraryClasses.get(type);
    }
    return result;
  }

  @Override
  public Builder builder() {
    return new Builder(this);
  }

  @Override
  public DirectMappedDexApplication toDirect() {
    return this;
  }

  @Override
  public DirectMappedDexApplication asDirect() {
    return this;
  }

  public String toString() {
    return "DexApplication (direct)";
  }

  public static class Builder extends DexApplication.Builder<Builder> {

    private Map<DexType, DexLibraryClass> libraryClasses = new IdentityHashMap<>();

    Builder(LazyLoadedDexApplication application) {
      super(application);
      // As a side-effect, this will force-load all classes.
      Map<DexType, DexClass> allClasses = application.getFullClassMap();
      Iterables.filter(allClasses.values(), DexLibraryClass.class)
          .forEach(k -> libraryClasses.put(k.type, k));

    }

    private Builder(DirectMappedDexApplication application) {
      super(application);
      this.libraryClasses.putAll(application.libraryClasses);
    }

    @Override
    Builder self() {
      return this;
    }

    @Override
    public DexApplication build() {
      return new DirectMappedDexApplication(proguardMap,
          ProgramClassCollection.create(programClasses),
          ImmutableMap.copyOf(libraryClasses), ImmutableSet.copyOf(mainDexList), deadCode,
          dexItemFactory, highestSortingString, timing);
    }
  }
}
