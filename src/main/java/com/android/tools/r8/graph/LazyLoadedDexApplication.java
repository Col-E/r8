// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.dex.ApplicationReader.ProgramClassConflictResolver;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.utils.ClasspathClassCollection;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.LibraryClassCollection;
import com.android.tools.r8.utils.ProgramClassCollection;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import javax.annotation.Nonnull;

import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class LazyLoadedDexApplication extends DexApplication {

  private final ProgramClassCollection programClasses;
  private final ClasspathClassCollection classpathClasses;
  private final LibraryClassCollection libraryClasses;

  /** Constructor should only be invoked by the DexApplication.Builder. */
  private LazyLoadedDexApplication(
      ClassNameMapper proguardMap,
      DexApplicationReadFlags flags,
      ProgramClassCollection programClasses,
      ImmutableList<DataResourceProvider> dataResourceProviders,
      ClasspathClassCollection classpathClasses,
      LibraryClassCollection libraryClasses,
      InternalOptions options,
      Timing timing) {
    super(proguardMap, flags, dataResourceProviders, options, timing);
    this.programClasses = programClasses;
    this.classpathClasses = classpathClasses;
    this.libraryClasses = libraryClasses;
  }

  @Nonnull
  @Override
  public LazyLoadedDexApplication copy() {
    ProgramClassConflictResolver resolver =
            options.programClassConflictResolver == null
                    ? ProgramClassCollection.defaultConflictResolver(options.reporter)
                    : options.programClassConflictResolver;
    ProgramClassCollection programClassesCopy = ProgramClassCollection.create(programClasses.getAllClasses().stream()
            .map(DexProgramClass::copy)
            .collect(Collectors.toList()), resolver);
    return new LazyLoadedDexApplication(getProguardMap(), getFlags(),
            programClassesCopy, dataResourceProviders, classpathClasses, libraryClasses,
            options, timing);
  }

  @Override
  public List<DexProgramClass> programClasses() {
    programClasses.forceLoad(t -> true);
    return programClasses.getAllClasses();
  }

  @Override
  public void forEachProgramType(Consumer<DexType> consumer) {
    programClasses.getAllTypes().forEach(consumer);
  }

  @Override
  public void forEachLibraryType(Consumer<DexType> consumer) {
    libraryClasses.getAllClassProviderTypes().forEach(consumer);
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
    DexClass clazz = programClasses.get(type);
    if (clazz == null && classpathClasses != null) {
      clazz = classpathClasses.get(type);
    }
    if (clazz == null && libraryClasses != null) {
      clazz = libraryClasses.get(type);
    }
    return clazz;
  }

  @Override
  public DexProgramClass programDefinitionFor(DexType type) {
    assert type.isClassType() : "Cannot lookup definition for type: " + type;
    return programClasses.get(type);
  }

  @Override
  public DexLibraryClass libraryDefinitionFor(DexType type) {
    assert type.isClassType() : "Cannot lookup library definition for type: " + type;
    return libraryClasses.get(type);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof LazyLoadedDexApplication) {
      LazyLoadedDexApplication that = (LazyLoadedDexApplication) o;
      return Objects.equals(programClasses, that.programClasses) &&
              Objects.equals(classpathClasses, that.classpathClasses) &&
              Objects.equals(libraryClasses, that.libraryClasses);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(programClasses) +
            31 * Objects.hashCode(classpathClasses) +
            31 * Objects.hashCode(libraryClasses);
  }

  static class AllClasses {

    // Mapping of all types to their definitions.
    // Collections of the three different types for iteration.
    private final ImmutableMap<DexType, DexProgramClass> programClasses;
    private final ImmutableMap<DexType, DexClasspathClass> classpathClasses;
    private final ImmutableMap<DexType, DexLibraryClass> libraryClasses;

    AllClasses(
        LibraryClassCollection libraryClassesLoader,
        ClasspathClassCollection classpathClassesLoader,
        ProgramClassCollection programClassesLoader,
        InternalOptions options) {

      // When desugaring VarHandle do not read the VarHandle and MethodHandles$Lookup classes
      // from the library as they will be synthesized during desugaring.
      Predicate<DexType> forceLoadPredicate =
          type ->
              !(options.shouldDesugarVarHandle()
                  && (type == options.dexItemFactory().varHandleType
                      || type == options.dexItemFactory().lookupType));

      // Force-load library classes.
      ImmutableMap<DexType, DexLibraryClass> allLibraryClasses;
      if (libraryClassesLoader != null) {
        libraryClassesLoader.forceLoad(forceLoadPredicate);
        allLibraryClasses = libraryClassesLoader.getAllClassesInMap();
      } else {
        allLibraryClasses = ImmutableMap.of();
      }

      // Program classes should be fully loaded.
      assert programClassesLoader != null;
      assert programClassesLoader.isFullyLoaded();
      programClassesLoader.forceLoad(type -> true);
      ImmutableMap<DexType, DexProgramClass> allProgramClasses =
          programClassesLoader.getAllClassesInMap();

      // Force-load classpath classes.
      ImmutableMap<DexType, DexClasspathClass> allClasspathClasses;
      if (classpathClassesLoader != null) {
        classpathClassesLoader.forceLoad(type -> true);
        allClasspathClasses = classpathClassesLoader.getAllClassesInMap();
      } else {
        allClasspathClasses = ImmutableMap.of();
      }

      // Collect loaded classes in the precedence order library classes, program classes and
      // class path classes or program classes, classpath classes and library classes depending
      // on the configured lookup order.
      if (options.loadAllClassDefinitions) {
        libraryClasses = allLibraryClasses;
        programClasses = allProgramClasses;
        classpathClasses =
            fillPrioritizedClasses(allClasspathClasses, programClasses::get, options);
      } else {
        programClasses = fillPrioritizedClasses(allProgramClasses, type -> null, options);
        classpathClasses =
            fillPrioritizedClasses(allClasspathClasses, programClasses::get, options);
        libraryClasses =
            fillPrioritizedClasses(
                allLibraryClasses,
                type -> {
                  DexProgramClass clazz = programClasses.get(type);
                  if (clazz != null) {
                    options.recordLibraryAndProgramDuplicate(
                        type, clazz, allLibraryClasses.get(type));
                    return clazz;
                  }
                  return classpathClasses.get(type);
                },
                options);
      }
    }

    public ImmutableMap<DexType, DexProgramClass> getProgramClasses() {
      return programClasses;
    }

    public ImmutableMap<DexType, DexClasspathClass> getClasspathClasses() {
      return classpathClasses;
    }

    public ImmutableMap<DexType, DexLibraryClass> getLibraryClasses() {
      return libraryClasses;
    }
  }

  private static <T extends DexClass> ImmutableMap<DexType, T> fillPrioritizedClasses(
      Map<DexType, T> classCollection,
      Function<DexType, DexClass> getExisting,
      InternalOptions options) {
    if (classCollection != null) {
      Set<DexType> javaLibraryOverride = Sets.newIdentityHashSet();
      ImmutableMap.Builder<DexType, T> builder = ImmutableMap.builder();
      classCollection.forEach(
          (type, clazz) -> {
            DexClass other = getExisting.apply(type);
            if (other == null) {
              builder.put(type, clazz);
            } else if (type.getPackageName().startsWith("java.")
                && (clazz.isLibraryClass() || other.isLibraryClass())) {
              javaLibraryOverride.add(type);
            }
          });
      if (!javaLibraryOverride.isEmpty()) {
        warnJavaLibraryOverride(options, javaLibraryOverride);
      }
      return builder.build();
    } else {
      return ImmutableMap.of();
    }
  }

  private static void warnJavaLibraryOverride(
      InternalOptions options, Set<DexType> javaLibraryOverride) {
    if (options.ignoreJavaLibraryOverride) {
      return;
    }
    String joined =
        javaLibraryOverride.stream()
            .sorted()
            .map(DexType::toString)
            .collect(Collectors.joining(", "));
    String message =
        "The following library types, prefixed by java.,"
            + " are present both as library and non library classes: "
            + joined
            + ". Library classes will be ignored.";
    options.reporter.warning(message);
  }

  /**
   * Force load all classes and return type -> class map containing all the classes.
   */
  public AllClasses loadAllClasses() {
    return new AllClasses(libraryClasses, classpathClasses, programClasses, options);
  }

  public static class Builder extends DexApplication.Builder<Builder> {

    private ClasspathClassCollection classpathClasses;
    private LibraryClassCollection libraryClasses;

    Builder(InternalOptions options, Timing timing) {
      super(options, timing);
      this.classpathClasses = ClasspathClassCollection.empty();
      this.libraryClasses = LibraryClassCollection.empty();
    }

    private Builder(LazyLoadedDexApplication application) {
      super(application);
      this.classpathClasses = application.classpathClasses;
      this.libraryClasses = application.libraryClasses;
    }

    @Override
    Builder self() {
      return this;
    }

    public Builder setClasspathClassCollection(ClasspathClassCollection classes) {
      this.classpathClasses = classes;
      return this;
    }

    public Builder setLibraryClassCollection(LibraryClassCollection classes) {
      this.libraryClasses = classes;
      return this;
    }

    @Override
    public void addProgramClassPotentiallyOverridingNonProgramClass(DexProgramClass clazz) {
      addProgramClass(clazz);
      classpathClasses.clearType(clazz.type);
      libraryClasses.clearType(clazz.type);
    }

    @Override
    public LazyLoadedDexApplication build() {
      ProgramClassConflictResolver resolver =
          options.programClassConflictResolver == null
              ? ProgramClassCollection.defaultConflictResolver(options.reporter)
              : options.programClassConflictResolver;
      return new LazyLoadedDexApplication(
          proguardMap,
          flags,
          ProgramClassCollection.create(getProgramClasses(), resolver),
          ImmutableList.copyOf(dataResourceProviders),
          classpathClasses,
          libraryClasses,
          options,
          timing);
    }
  }

  @Override
  public Builder builder() {
    return new Builder(this);
  }

  @Override
  public DirectMappedDexApplication toDirect() {
    return new DirectMappedDexApplication.Builder(this).build().asDirect();
  }

  @Override
  public boolean isDirect() {
    return false;
  }

  @Override
  public String toString() {
    return "Application (" + programClasses + "; " + classpathClasses + "; " + libraryClasses
        + ")";
  }
}
