// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.graph.LazyLoadedDexApplication.AllClasses;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class DirectMappedDexApplication extends DexApplication {

  // Mapping from code objects to their encoded-method owner. Used for asserting unique ownership
  // and debugging purposes.
  private final Map<Code, DexEncodedMethod> codeOwners = new IdentityHashMap<>();

  // Collections of the three different types for iteration.
  private final ImmutableSortedMap<DexType, DexProgramClass> programClasses;
  private final ImmutableSortedMap<DexType, DexClasspathClass> classpathClasses;
  private final ImmutableSortedMap<DexType, DexLibraryClass> libraryClasses;

  private DirectMappedDexApplication(
      ClassNameMapper proguardMap,
      DexApplicationReadFlags flags,
      ImmutableSortedMap<DexType, DexProgramClass> programClasses,
      ImmutableSortedMap<DexType, DexClasspathClass> classpathClasses,
      ImmutableSortedMap<DexType, DexLibraryClass> libraryClasses,
      ImmutableList<DataResourceProvider> dataResourceProviders,
      InternalOptions options,
      DexString highestSortingString,
      Timing timing) {
    super(proguardMap, flags, dataResourceProviders, options, highestSortingString, timing);
    this.programClasses = programClasses;
    this.classpathClasses = classpathClasses;
    this.libraryClasses = libraryClasses;
  }

  public Collection<DexClasspathClass> classpathClasses() {
    return classpathClasses.values();
  }

  @Override
  Collection<DexProgramClass> programClasses() {
    return programClasses.values();
  }

  public Collection<DexLibraryClass> libraryClasses() {
    return libraryClasses.values();
  }

  @Override
  public Collection<DexProgramClass> classesWithDeterministicOrder() {
    return programClasses.values();
  }

  @Override
  public ClassResolutionResult contextIndependentDefinitionForWithResolutionResult(DexType type) {
    ClassResolutionResult.Builder builder = ClassResolutionResult.builder();
    if (libraryClasses != null) {
      addClassToBuilderIfNotNull(libraryClasses.get(type), builder::add);
    }
    if (programClasses == null
        || !addClassToBuilderIfNotNull(programClasses.get(type), builder::add)) {
      // When looking up a type that exists both on program path and classpath, we assume the
      // program class is taken and only if not present will look at classpath.
      if (classpathClasses != null) {
        addClassToBuilderIfNotNull(classpathClasses.get(type), builder::add);
      }
    }
    return builder.build();
  }

  private <T extends DexClass> boolean addClassToBuilderIfNotNull(T clazz, Consumer<T> adder) {
    if (clazz != null) {
      adder.accept(clazz);
      return true;
    } else {
      return false;
    }
  }

  @Override
  public DexClass definitionFor(DexType type) {
    assert type.isClassType() : "Cannot lookup definition for type: " + type;
    DexClass clazz = null;
    if (options.lookupLibraryBeforeProgram) {
      if (libraryClasses != null) {
        clazz = libraryClasses.get(type);
      }
      if (clazz == null) {
        clazz = programClasses.get(type);
      }
      if (clazz == null && classpathClasses != null) {
        clazz = classpathClasses.get(type);
      }
    } else {
      clazz = programClasses.get(type);
      if (clazz == null && classpathClasses != null) {
        clazz = classpathClasses.get(type);
      }
      if (clazz == null && libraryClasses != null) {
        clazz = libraryClasses.get(type);
      }
    }
    return clazz;
  }

  @Override
  public DexProgramClass programDefinitionFor(DexType type) {
    return programClasses.get(type);
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

  private boolean mappingIsValid(
      Collection<DexProgramClass> classesBeforeLensApplication, GraphLens lens) {
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
    for (DexProgramClass clazz : programClasses()) {
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

    private Map<DexType, DexClasspathClass> classpathClasses;
    private Map<DexType, DexLibraryClass> libraryClasses;

    private final List<DexClasspathClass> pendingClasspathClasses = new ArrayList<>();
    private final List<DexProgramClass> pendingNonProgramRemovals = new ArrayList<>();

    Builder(LazyLoadedDexApplication application) {
      super(application);
      // As a side-effect, this will force-load all classes.
      AllClasses allClasses = application.loadAllClasses();
      classpathClasses = new IdentityHashMap<>(allClasses.getClasspathClasses());
      libraryClasses = new IdentityHashMap<>(allClasses.getLibraryClasses());
      replaceProgramClasses(allClasses.getProgramClasses().values());
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
      pendingNonProgramRemovals.add(clazz);
    }

    @Override
    Builder self() {
      return this;
    }

    public Builder addClasspathClass(DexClasspathClass clazz) {
      pendingClasspathClasses.add(clazz);
      return self();
    }

    private void commitPendingClasspathClasses() {
      if (!pendingClasspathClasses.isEmpty()) {
        pendingClasspathClasses.forEach(
            clazz -> {
              DexClasspathClass old = classpathClasses.put(clazz.type, clazz);
              assert old == null;
            });
        pendingClasspathClasses.clear();
      }
    }

    public Builder replaceClasspathClasses(Collection<DexClasspathClass> newClasspathClasses) {
      classpathClasses = new IdentityHashMap<>();
      newClasspathClasses.forEach(clazz -> classpathClasses.put(clazz.type, clazz));
      pendingClasspathClasses.clear();
      return self();
    }

    public Builder replaceLibraryClasses(Collection<DexLibraryClass> newLibraryClasses) {
      libraryClasses = new IdentityHashMap<>();
      newLibraryClasses.forEach(clazz -> libraryClasses.put(clazz.type, clazz));
      return self();
    }

    @Override
    public DirectMappedDexApplication build() {
      // If there are pending non-program removals or pending class path classes, create a new map
      // to ensure not modifying an immutable collection.
      if (!pendingNonProgramRemovals.isEmpty()) {
        libraryClasses = new IdentityHashMap<>(libraryClasses);
        classpathClasses = new IdentityHashMap<>(classpathClasses);
      } else if (!pendingClasspathClasses.isEmpty()) {
        classpathClasses = new IdentityHashMap<>(classpathClasses);
      }
      commitPendingClasspathClasses();
      pendingNonProgramRemovals.forEach(
          clazz -> {
            libraryClasses.remove(clazz.type);
            classpathClasses.remove(clazz.type);
          });
      pendingNonProgramRemovals.clear();
      ImmutableSortedMap.Builder<DexType, DexProgramClass> programClassMap =
          new ImmutableSortedMap.Builder<>(DexType::compareTo);
      getProgramClasses().forEach(clazz -> programClassMap.put(clazz.type, clazz));
      return new DirectMappedDexApplication(
          proguardMap,
          flags,
          programClassMap.build(),
          getImmutableMap(classpathClasses),
          getImmutableMap(libraryClasses),
          ImmutableList.copyOf(dataResourceProviders),
          options,
          highestSortingString,
          timing);
    }

    private <T extends DexClass> ImmutableSortedMap<DexType, T> getImmutableMap(
        Map<DexType, T> map) {
      if (map instanceof ImmutableSortedMap) {
        return (ImmutableSortedMap<DexType, T>) map;
      } else {
        return ImmutableSortedMap.copyOf(map);
      }
    }
  }
}
