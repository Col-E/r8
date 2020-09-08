// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.graph.LazyLoadedDexApplication.AllClasses;
import com.android.tools.r8.graph.classmerging.MergedClasses;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class DirectMappedDexApplication extends DexApplication {

  // Mapping from code objects to their encoded-method owner. Used for asserting unique ownership
  // and debugging purposes.
  private final Map<Code, DexEncodedMethod> codeOwners = new IdentityHashMap<>();

  // Unmodifiable mapping of all types to their definitions.
  private final Map<DexType, DexClass> allClasses;
  // Collections of the three different types for iteration.
  private final ImmutableList<DexProgramClass> programClasses;
  private final ImmutableList<DexClasspathClass> classpathClasses;
  private final ImmutableList<DexLibraryClass> libraryClasses;

  private DirectMappedDexApplication(
      ClassNameMapper proguardMap,
      Map<DexType, DexClass> allClasses,
      ImmutableList<DexProgramClass> programClasses,
      ImmutableList<DexClasspathClass> classpathClasses,
      ImmutableList<DexLibraryClass> libraryClasses,
      ImmutableList<DataResourceProvider> dataResourceProviders,
      InternalOptions options,
      DexString highestSortingString,
      Timing timing) {
    super(
        proguardMap,
        dataResourceProviders,
        options,
        highestSortingString,
        timing);
    this.allClasses = Collections.unmodifiableMap(allClasses);
    this.programClasses = programClasses;
    this.classpathClasses = classpathClasses;
    this.libraryClasses = libraryClasses;
  }

  public Collection<DexClass> allClasses() {
    return allClasses.values();
  }

  @Override
  List<DexProgramClass> programClasses() {
    return programClasses;
  }

  public Collection<DexLibraryClass> libraryClasses() {
    return libraryClasses;
  }

  public Collection<DexClasspathClass> classpathClasses() {
    return classpathClasses;
  }

  @Override
  public DexClass definitionFor(DexType type) {
    assert type.isClassType() : "Cannot lookup definition for type: " + type;
    return allClasses.get(type);
  }

  @Override
  public DexProgramClass programDefinitionFor(DexType type) {
    // The direct mapped application has no duplicates so this coincides with definitionFor.
    return DexProgramClass.asProgramClassOrNull(definitionFor(type));
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
  public boolean isDirect() {
    return true;
  }

  @Override
  public DirectMappedDexApplication asDirect() {
    return this;
  }

  @Override
  public String toString() {
    return "DexApplication (direct)";
  }

  public boolean verifyWithLens(GraphLens lens) {
    assert mappingIsValid(lens, allClasses.keySet());
    assert verifyCodeObjectsOwners();
    return true;
  }

  public boolean verifyNothingToRewrite(AppView<?> appView, GraphLens lens) {
    assert allClasses.keySet().stream()
        .allMatch(
            type ->
                lens.lookupType(type) == type
                    || MergedClasses.hasBeenMerged(appView.verticallyMergedClasses(), type)
                    || MergedClasses.hasBeenMerged(appView.horizontallyMergedClasses(), type));
    assert verifyCodeObjectsOwners();
    return true;
  }

  private boolean mappingIsValid(GraphLens graphLens, Iterable<DexType> types) {
    // The lens might either map to a different type that is already present in the application
    // (e.g. relinking a type) or it might encode a type that was renamed, in which case the
    // original type will point to a definition that was renamed.
    for (DexType type : types) {
      DexType renamed = graphLens.lookupType(type);
      if (renamed.isIntType()) {
        continue;
      }
      if (renamed != type) {
        if (definitionFor(type) == null && definitionFor(renamed) != null) {
          continue;
        }
        assert definitionFor(type).type == renamed || definitionFor(renamed) != null;
      }
    }
    return true;
  }

  // Debugging helper to compute the code-object owner map.
  public Map<Code, DexEncodedMethod> computeCodeObjectOwnersForDebugging() {
    // Call the verification method without assert to ensure owners are computed.
    verifyCodeObjectsOwners();
    return codeOwners;
  }

  // Debugging helper to find the method a code object belongs to.
  public DexEncodedMethod getCodeOwnerForDebugging(Code code) {
    return computeCodeObjectOwnersForDebugging().get(code);
  }

  public boolean verifyCodeObjectsOwners() {
    codeOwners.clear();
    for (DexProgramClass clazz : programClasses) {
      for (DexEncodedMethod method :
          clazz.methods(DexEncodedMethod::isNonAbstractNonNativeMethod)) {
        Code code = method.getCode();
        assert code != null;
        // If code is (lazy) CF code, then use the CF code object rather than the lazy wrapper.
        if (code.isCfCode()) {
          code = code.asCfCode();
        }
        DexEncodedMethod otherMethod = codeOwners.put(code, method);
        assert otherMethod == null;
      }
    }
    return true;
  }

  public static class Builder extends DexApplication.Builder<Builder> {

    private ImmutableList<DexLibraryClass> libraryClasses;
    private ImmutableList<DexClasspathClass> classpathClasses;

    Builder(LazyLoadedDexApplication application) {
      super(application);
      // As a side-effect, this will force-load all classes.
      AllClasses allClasses = application.loadAllClasses();
      libraryClasses = allClasses.getLibraryClasses();
      classpathClasses = allClasses.getClasspathClasses();
      replaceProgramClasses(allClasses.getProgramClasses());
    }

    private Builder(DirectMappedDexApplication application) {
      super(application);
      libraryClasses = application.libraryClasses;
      classpathClasses = application.classpathClasses;
    }

    @Override
    Builder self() {
      return this;
    }

    public Builder replaceLibraryClasses(Collection<DexLibraryClass> libraryClasses) {
      this.libraryClasses = ImmutableList.copyOf(libraryClasses);
      return self();
    }

    public Builder replaceClasspathClasses(Collection<DexClasspathClass> classpathClasses) {
      this.classpathClasses = ImmutableList.copyOf(classpathClasses);
      return self();
    }

    public Builder addClasspathClasses(Collection<DexClasspathClass> classes) {
      classpathClasses =
          ImmutableList.<DexClasspathClass>builder()
              .addAll(classpathClasses)
              .addAll(classes)
              .build();
      return self();
    }

    @Override
    public DirectMappedDexApplication build() {
      // Rebuild the map. This will fail if keys are not unique.
      // TODO(zerny): Consider not rebuilding the map if no program classes are added.
      Map<DexType, DexClass> allClasses =
          new IdentityHashMap<>(
              programClasses.size() + classpathClasses.size() + libraryClasses.size());
      // Note: writing classes in reverse priority order, so a duplicate will be correctly ordered.
      // There should never be duplicates and that is asserted in the addAll subroutine.
      addAll(allClasses, libraryClasses);
      addAll(allClasses, classpathClasses);
      addAll(allClasses, programClasses);
      return new DirectMappedDexApplication(
          proguardMap,
          allClasses,
          ImmutableList.copyOf(programClasses),
          classpathClasses,
          libraryClasses,
          ImmutableList.copyOf(dataResourceProviders),
          options,
          highestSortingString,
          timing);
    }
  }

  private static <T extends DexClass> void addAll(
      Map<DexType, DexClass> allClasses, Iterable<T> toAdd) {
    for (DexClass clazz : toAdd) {
      DexClass old = allClasses.put(clazz.type, clazz);
      assert old == null : "Class " + old.type.toString() + " was already present.";
    }
  }
}
