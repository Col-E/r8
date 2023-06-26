// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.ClassResolutionResult.NoResolutionResult.noResult;

import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.graph.LazyLoadedDexApplication.AllClasses;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class DirectMappedDexApplication extends DexApplication {

  // Mapping from code objects to their encoded-method owner. Used for asserting unique ownership
  // and debugging purposes.
  private final Map<Code, DexEncodedMethod> codeOwners = new IdentityHashMap<>();

  // Unmodifiable mapping of all types to their definitions.
  private final ImmutableMap<DexType, ProgramOrClasspathClass> programOrClasspathClasses;
  private final ImmutableMap<DexType, DexLibraryClass> libraryClasses;

  // Collections of different types for iteration.
  private final ImmutableCollection<DexProgramClass> programClasses;
  private final ImmutableCollection<DexClasspathClass> classpathClasses;

  private DirectMappedDexApplication(
      ClassNameMapper proguardMap,
      DexApplicationReadFlags flags,
      ImmutableMap<DexType, ProgramOrClasspathClass> programOrClasspathClasses,
      ImmutableMap<DexType, DexLibraryClass> libraryClasses,
      ImmutableCollection<DexProgramClass> programClasses,
      ImmutableCollection<DexClasspathClass> classpathClasses,
      ImmutableList<DataResourceProvider> dataResourceProviders,
      InternalOptions options,
      Timing timing) {
    super(proguardMap, flags, dataResourceProviders, options, timing);
    this.programOrClasspathClasses = programOrClasspathClasses;
    this.libraryClasses = libraryClasses;
    this.programClasses = programClasses;
    this.classpathClasses = classpathClasses;
  }

  public Collection<DexClasspathClass> classpathClasses() {
    return classpathClasses;
  }

  @Override
  Collection<DexProgramClass> programClasses() {
    return programClasses;
  }

  @Override
  public void forEachProgramType(Consumer<DexType> consumer) {
    programClasses.forEach(clazz -> consumer.accept(clazz.type));
  }

  @Override
  public void forEachLibraryType(Consumer<DexType> consumer) {
    libraryClasses.forEach((type, clazz) -> consumer.accept(type));
  }

  public Collection<DexLibraryClass> libraryClasses() {
    return libraryClasses.values();
  }

  @Override
  public ClassResolutionResult contextIndependentDefinitionForWithResolutionResult(DexType type) {
    assert type.isClassType() : "Cannot lookup definition for type: " + type;
    DexLibraryClass libraryClass = libraryClasses.get(type);
    ProgramOrClasspathClass programOrClasspathClass = programOrClasspathClasses.get(type);
    if (libraryClass == null && programOrClasspathClass == null) {
      return noResult();
    } else if (libraryClass != null && programOrClasspathClass == null) {
      return libraryClass;
    } else if (libraryClass == null) {
      return programOrClasspathClass.asDexClass();
    } else {
      return ClassResolutionResult.builder().add(libraryClass).add(programOrClasspathClass).build();
    }
  }

  @Override
  public DexClass definitionFor(DexType type) {
    assert type.isClassType() : "Cannot lookup definition for type: " + type;
    if (options.lookupLibraryBeforeProgram) {
      DexLibraryClass libraryClass = libraryClasses.get(type);
      if (libraryClass != null) {
        return libraryClass;
      }
      ProgramOrClasspathClass programOrClasspathClass = programOrClasspathClasses.get(type);
      return programOrClasspathClass != null ? programOrClasspathClass.asDexClass() : null;
    } else {
      ProgramOrClasspathClass programOrClasspathClass = programOrClasspathClasses.get(type);
      if (programOrClasspathClass != null && programOrClasspathClass.isProgramClass()) {
        return programOrClasspathClass.asDexClass();
      }
      DexLibraryClass libraryClass = libraryClasses.get(type);
      return libraryClass != null
          ? libraryClass
          : (programOrClasspathClass == null ? null : programOrClasspathClass.asDexClass());
    }
  }

  @Override
  public DexProgramClass programDefinitionFor(DexType type) {
    ProgramOrClasspathClass programOrClasspathClass = programOrClasspathClasses.get(type);
    return programOrClasspathClass == null ? null : programOrClasspathClass.asProgramClass();
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

    private ImmutableCollection<DexClasspathClass> classpathClasses;
    private Map<DexType, DexLibraryClass> libraryClasses;

    private final List<DexClasspathClass> pendingClasspathClasses = new ArrayList<>();
    private final Set<DexType> pendingClasspathRemovalIfPresent = Sets.newIdentityHashSet();

    Builder(LazyLoadedDexApplication application) {
      super(application);
      // As a side-effect, this will force-load all classes.
      AllClasses allClasses = application.loadAllClasses();
      classpathClasses = allClasses.getClasspathClasses().values();
      libraryClasses = allClasses.getLibraryClasses();
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
      pendingClasspathRemovalIfPresent.add(clazz.type);
      if (libraryClasses.containsKey(clazz.type)) {
        ensureMutableLibraryClassesMap();
        libraryClasses.remove(clazz.type);
      }
    }

    private void ensureMutableLibraryClassesMap() {
      if (!(libraryClasses instanceof IdentityHashMap)) {
        libraryClasses = new IdentityHashMap<>(libraryClasses);
      }
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
        classpathClasses =
            ImmutableList.<DexClasspathClass>builder()
                .addAll(classpathClasses)
                .addAll(pendingClasspathClasses)
                .build();
        pendingClasspathClasses.clear();
      }
    }

    public Builder replaceClasspathClasses(Collection<DexClasspathClass> newClasspathClasses) {
      assert newClasspathClasses != null;
      classpathClasses = ImmutableList.copyOf(newClasspathClasses);
      pendingClasspathClasses.clear();
      return self();
    }

    public Builder replaceLibraryClasses(Collection<DexLibraryClass> libraryClasses) {
      ImmutableMap.Builder<DexType, DexLibraryClass> builder = ImmutableMap.builder();
      libraryClasses.forEach(clazz -> builder.put(clazz.type, clazz));
      this.libraryClasses = builder.build();
      return self();
    }

    @Override
    public DirectMappedDexApplication build() {
      // Rebuild the map. This will fail if keys are not unique.
      // TODO(zerny): Consider not rebuilding the map if no program classes are added.
      commitPendingClasspathClasses();
      Map<DexType, ProgramOrClasspathClass> programAndClasspathClasses =
          new IdentityHashMap<>(getProgramClasses().size() + classpathClasses.size());
      // Note: writing classes in reverse priority order, so a duplicate will be correctly ordered.
      // There should not be duplicates between program and classpath and that is asserted in the
      // addAll subroutine.
      ImmutableCollection<DexClasspathClass> newClasspathClasses = classpathClasses;
      if (addAll(programAndClasspathClasses, classpathClasses)) {
        ImmutableList.Builder<DexClasspathClass> builder = ImmutableList.builder();
        for (DexClasspathClass classpathClass : classpathClasses) {
          if (!pendingClasspathRemovalIfPresent.contains(classpathClass.getType())) {
            builder.add(classpathClass);
          }
        }
        newClasspathClasses = builder.build();
      }
      addAll(programAndClasspathClasses, getProgramClasses());
      return new DirectMappedDexApplication(
          proguardMap,
          flags,
          ImmutableMap.copyOf(programAndClasspathClasses),
          getLibraryClassesAsImmutableMap(),
          ImmutableList.copyOf(getProgramClasses()),
          newClasspathClasses,
          ImmutableList.copyOf(dataResourceProviders),
          options,
          timing);
    }

    private <T extends ProgramOrClasspathClass> boolean addAll(
        Map<DexType, ProgramOrClasspathClass> allClasses, Iterable<T> toAdd) {
      boolean seenRemoved = false;
      for (T clazz : toAdd) {
        if (clazz.isProgramClass() || !pendingClasspathRemovalIfPresent.contains(clazz.getType())) {
          ProgramOrClasspathClass old = allClasses.put(clazz.getType(), clazz);
          assert old == null : "Class " + old.getType().toString() + " was already present.";
        } else {
          seenRemoved = true;
        }
      }
      return seenRemoved;
    }

    private ImmutableMap<DexType, DexLibraryClass> getLibraryClassesAsImmutableMap() {
      if (libraryClasses instanceof ImmutableMap) {
        return (ImmutableMap<DexType, DexLibraryClass>) libraryClasses;
      } else {
        return ImmutableMap.copyOf(libraryClasses);
      }
    }
  }
}
