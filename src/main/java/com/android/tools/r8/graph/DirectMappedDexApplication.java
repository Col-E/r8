// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.graph.LazyLoadedDexApplication.AllClasses;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.utils.ProgramClassCollection;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DirectMappedDexApplication extends DexApplication {

  private final AllClasses allClasses;
  private final ImmutableMap<DexType, DexLibraryClass> libraryClasses;

  private DirectMappedDexApplication(ClassNameMapper proguardMap,
      AllClasses allClasses,
      ProgramClassCollection programClasses,
      ImmutableList<ProgramResourceProvider> programResourceProviders,
      ImmutableMap<DexType, DexLibraryClass> libraryClasses,
      ImmutableSet<DexType> mainDexList, String deadCode,
      DexItemFactory dexItemFactory, DexString highestSortingString,
      Timing timing) {
    super(proguardMap, programClasses, programResourceProviders, mainDexList, deadCode,
        dexItemFactory, highestSortingString, timing);
    this.allClasses = allClasses;
    this.libraryClasses = libraryClasses;
  }

  public Collection<DexLibraryClass> libraryClasses() {
    return libraryClasses.values();
  }

  @Override
  public DexClass definitionFor(DexType type) {
    assert type.isClassType() : "Cannot lookup definition for type: " + type;
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

  @Override
  public String toString() {
    return "DexApplication (direct)";
  }

  public DirectMappedDexApplication rewrittenWithLense(GraphLense graphLense) {
    // As a side effect, this will rebuild the program classes and library classes maps.
    DirectMappedDexApplication rewrittenApplication = this.builder().build().asDirect();
    assert rewrittenApplication.mappingIsValid(graphLense, programClasses.getAllTypes());
    assert rewrittenApplication.mappingIsValid(graphLense, libraryClasses.keySet());
    return rewrittenApplication;
  }

  private boolean mappingIsValid(GraphLense graphLense, Iterable<DexType> types) {
    // The lens might either map to a different type that is already present in the application
    // (e.g. relinking a type) or it might encode a type that was renamed, in which case the
    // original type will point to a definition that was renamed.
    for (DexType type : types) {
      DexType renamed = graphLense.lookupType(type);
      if (renamed != type) {
        if (definitionFor(type) == null && definitionFor(renamed) != null) {
          continue;
        }
        assert definitionFor(type).type != renamed && definitionFor(renamed) == null;
      }
    }
    return true;
  }

  public static class Builder extends DexApplication.Builder<Builder> {

    private final AllClasses allClasses;
    private final List<DexLibraryClass> libraryClasses = new ArrayList<>();

    Builder(LazyLoadedDexApplication application) {
      super(application);
      // As a side-effect, this will force-load all classes.
      this.allClasses = application.loadAllClasses();
      Map<DexType, DexClass> allClasses = this.allClasses.getClasses();
      // TODO(120884788): This filter will only add library classes which are not program classes.
      Iterables.filter(allClasses.values(), DexLibraryClass.class).forEach(libraryClasses::add);
    }

    private Builder(DirectMappedDexApplication application) {
      super(application);
      this.allClasses = application.allClasses;
      this.libraryClasses.addAll(application.libraryClasses.values());
    }

    @Override
    Builder self() {
      return this;
    }

    @Override
    public DexApplication build() {
      // Rebuild the map. This will fail if keys are not unique.
      return new DirectMappedDexApplication(
          proguardMap,
          allClasses,
          ProgramClassCollection.create(
              programClasses, ProgramClassCollection::resolveClassConflictImpl),
          ImmutableList.copyOf(programResourceProviders),
          libraryClasses.stream().collect(ImmutableMap.toImmutableMap(c -> c.type, c -> c)),
          ImmutableSet.copyOf(mainDexList),
          deadCode,
          dexItemFactory,
          highestSortingString,
          timing);
    }
  }
}
