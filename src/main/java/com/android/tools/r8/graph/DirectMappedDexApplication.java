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
import java.util.ArrayList;
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
      DexApplicationReadFlags flags,
      Map<DexType, DexClass> allClasses,
      ImmutableList<DexProgramClass> programClasses,
      ImmutableList<DexClasspathClass> classpathClasses,
      ImmutableList<DexLibraryClass> libraryClasses,
      ImmutableList<DataResourceProvider> dataResourceProviders,
      InternalOptions options,
      DexString highestSortingString,
      Timing timing) {
    super(proguardMap, flags, dataResourceProviders, options, highestSortingString, timing);
    this.allClasses = Collections.unmodifiableMap(allClasses);
    this.programClasses = programClasses;
    this.classpathClasses = classpathClasses;
    this.libraryClasses = libraryClasses;
  }

  public Collection<DexClass> allClasses() {
    return allClasses.values();
  }

  public List<DexClasspathClass> classpathClasses() {
    return classpathClasses;
  }

  @Override
  List<DexProgramClass> programClasses() {
    return programClasses;
  }

  public List<DexLibraryClass> libraryClasses() {
    return libraryClasses;
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

  public boolean verifyWithLens(DirectMappedDexApplication beforeLensApplication, GraphLens lens) {
    assert mappingIsValid(beforeLensApplication.programClasses(), lens);
    assert verifyCodeObjectsOwners();
    return true;
  }

  public boolean verifyNothingToRewrite(AppView<?> appView, GraphLens lens) {
    assert allClasses.keySet().stream()
        .allMatch(
            type ->
                lens.lookupType(type) == type
                    || MergedClasses.hasBeenMergedIntoDifferentType(
                        appView.verticallyMergedClasses(), type)
                    || MergedClasses.hasBeenMergedIntoDifferentType(
                        appView.horizontallyMergedClasses(), type));
    assert verifyCodeObjectsOwners();
    return true;
  }

  private boolean mappingIsValid(
      List<DexProgramClass> classesBeforeLensApplication, GraphLens lens) {
    // The lens might either map to a different type that is already present in the application
    // (e.g. relinking a type) or it might encode a type that was renamed, in which case the
    // original type will point to a definition that was renamed.
    for (DexProgramClass clazz : classesBeforeLensApplication) {
      DexType type = clazz.getType();
      DexType renamed = lens.lookupType(type);
      if (renamed.isIntType()) {
        continue;
      }
      if (renamed != type) {
        if (definitionFor(type) == null && definitionFor(renamed) != null) {
          continue;
        }
        assert definitionFor(type).type == renamed || definitionFor(renamed) != null
            : "The lens and app is inconsistent";
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
        } else if (code.isSharedCodeObject()) {
          continue;
        }
        DexEncodedMethod otherMethod = codeOwners.put(code, method);
        assert otherMethod == null;
      }
    }
    return true;
  }

  public static class Builder extends DexApplication.Builder<Builder> {

    private ImmutableList<DexClasspathClass> classpathClasses;
    private ImmutableList<DexLibraryClass> libraryClasses;

    private final List<DexClasspathClass> pendingClasspathClasses = new ArrayList<>();

    Builder(LazyLoadedDexApplication application) {
      super(application);
      // As a side-effect, this will force-load all classes.
      AllClasses allClasses = application.loadAllClasses();
      classpathClasses = allClasses.getClasspathClasses();
      libraryClasses = allClasses.getLibraryClasses();
      replaceProgramClasses(allClasses.getProgramClasses());
      replaceClasspathClasses(allClasses.getClasspathClasses());
    }

    private Builder(DirectMappedDexApplication application) {
      super(application);
      classpathClasses = application.classpathClasses;
      libraryClasses = application.libraryClasses;
    }

    @Override
    public boolean isDirect() {
      return true;
    }

    @Override
    public Builder asDirect() {
      return this;
    }

    @Override
    public void addProgramClassPotentiallyOverridingNonProgramClass(DexProgramClass clazz) {
      addProgramClass(clazz);
      if (containsType(clazz.type, libraryClasses)) {
        replaceLibraryClasses(withoutType(clazz.type, libraryClasses));
        return;
      }
      if (containsType(clazz.type, classpathClasses)) {
        replaceClasspathClasses(withoutType(clazz.type, classpathClasses));
      }
    }

    private boolean containsType(DexType type, List<? extends DexClass> classes) {
      for (DexClass clazz : classes) {
        if (clazz.type == type) {
          return true;
        }
      }
      return false;
    }

    private <T extends DexClass> ImmutableList<T> withoutType(DexType type, List<T> classes) {
      ImmutableList.Builder<T> builder = ImmutableList.builder();
      for (T clazz : classes) {
        if (clazz.type != type) {
          builder.add(clazz);
        }
      }
      return builder.build();
    }

    @Override
    Builder self() {
      return this;
    }

    public Builder addClasspathClass(DexClasspathClass clazz) {
      pendingClasspathClasses.add(clazz);
      return self();
    }

    public Builder addClasspathClasses(Collection<DexClasspathClass> classes) {
      pendingClasspathClasses.addAll(classes);
      return self();
    }

    private void commitPendingClasspathClasses() {
      if (!pendingClasspathClasses.isEmpty()) {
        classpathClasses =
            ImmutableList.<DexClasspathClass>builder()
                .addAll(classpathClasses)
                .addAll(pendingClasspathClasses)
                .build();
        pendingClasspathClasses.clear();
      }
    }

    public List<DexClasspathClass> getClasspathClasses() {
      commitPendingClasspathClasses();
      return classpathClasses;
    }

    public Builder replaceClasspathClasses(Collection<DexClasspathClass> newClasspathClasses) {
      assert newClasspathClasses != null;
      classpathClasses = ImmutableList.copyOf(newClasspathClasses);
      pendingClasspathClasses.clear();
      return self();
    }

    public Builder replaceLibraryClasses(Collection<DexLibraryClass> libraryClasses) {
      this.libraryClasses = ImmutableList.copyOf(libraryClasses);
      return self();
    }

    public Builder addLibraryClasses(Collection<DexLibraryClass> classes) {
      libraryClasses =
          ImmutableList.<DexLibraryClass>builder().addAll(libraryClasses).addAll(classes).build();
      return self();
    }

    @Override
    public DirectMappedDexApplication build() {
      // Rebuild the map. This will fail if keys are not unique.
      // TODO(zerny): Consider not rebuilding the map if no program classes are added.
      Map<DexType, DexClass> allClasses =
          new IdentityHashMap<>(
              getProgramClasses().size() + getClasspathClasses().size() + libraryClasses.size());
      // Note: writing classes in reverse priority order, so a duplicate will be correctly ordered.
      // There should never be duplicates and that is asserted in the addAll subroutine.
      addAll(allClasses, libraryClasses);
      addAll(allClasses, getClasspathClasses());
      addAll(allClasses, getProgramClasses());
      return new DirectMappedDexApplication(
          proguardMap,
          flags,
          allClasses,
          ImmutableList.copyOf(getProgramClasses()),
          ImmutableList.copyOf(getClasspathClasses()),
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
