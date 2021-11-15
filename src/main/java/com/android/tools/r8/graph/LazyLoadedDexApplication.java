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
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
      DexString highestSortingString,
      Timing timing) {
    super(proguardMap, flags, dataResourceProviders, options, highestSortingString, timing);
    this.programClasses = programClasses;
    this.classpathClasses = classpathClasses;
    this.libraryClasses = libraryClasses;
  }

  @Override
  List<DexProgramClass> programClasses() {
    programClasses.forceLoad(t -> true);
    return programClasses.getAllClasses();
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
    assert type.isClassType() : "Cannot lookup definition for type: " + type;
    return programClasses.get(type);
  }

  public DexLibraryClass libraryDefinitionFor(DexType type) {
    assert type.isClassType() : "Cannot lookup library definition for type: " + type;
    return libraryClasses.get(type);
  }

  static class AllClasses {

    // Mapping of all types to their definitions.
    private final Map<DexType, DexClass> allClasses;
    // Collections of the three different types for iteration.
    private final ImmutableList<DexProgramClass> programClasses;
    private final ImmutableList<DexClasspathClass> classpathClasses;
    private final ImmutableList<DexLibraryClass> libraryClasses;

    AllClasses(
        LibraryClassCollection libraryClassesLoader,
        ClasspathClassCollection classpathClassesLoader,
        ProgramClassCollection programClassesLoader,
        InternalOptions options) {
      int expectedMaxSize = 0;

      // Force-load library classes.
      Map<DexType, DexLibraryClass> allLibraryClasses = null;
      if (libraryClassesLoader != null) {
        libraryClassesLoader.forceLoad(type -> true);
        allLibraryClasses = libraryClassesLoader.getAllClassesInMap();
        expectedMaxSize += allLibraryClasses.size();
      }

      // Program classes should be fully loaded.
      assert programClassesLoader != null;
      assert programClassesLoader.isFullyLoaded();
      programClassesLoader.forceLoad(type -> true);
      Map<DexType, DexProgramClass> allProgramClasses = programClassesLoader.getAllClassesInMap();
      expectedMaxSize += allProgramClasses.size();

      // Force-load classpath classes.
      Map<DexType, DexClasspathClass> allClasspathClasses = null;
      if (classpathClassesLoader != null) {
        classpathClassesLoader.forceLoad(type -> true);
        allClasspathClasses = classpathClassesLoader.getAllClassesInMap();
        expectedMaxSize += allClasspathClasses.size();
      }

      // Collect loaded classes in the precedence order library classes, program classes and
      // class path classes or program classes, classpath classes and library classes depending
      // on the configured lookup order.
      Map<DexType, DexClass> prioritizedClasses = new IdentityHashMap<>(expectedMaxSize);
      if (options.lookupLibraryBeforeProgram) {
        libraryClasses = fillPrioritizedClasses(allLibraryClasses, prioritizedClasses, options);
        programClasses = fillPrioritizedClasses(allProgramClasses, prioritizedClasses, options);
        classpathClasses = fillPrioritizedClasses(allClasspathClasses, prioritizedClasses, options);
      } else {
        programClasses = fillPrioritizedClasses(allProgramClasses, prioritizedClasses, options);
        classpathClasses = fillPrioritizedClasses(allClasspathClasses, prioritizedClasses, options);
        libraryClasses = fillPrioritizedClasses(allLibraryClasses, prioritizedClasses, options);
      }

      allClasses = Collections.unmodifiableMap(prioritizedClasses);

      assert prioritizedClasses.size()
          == libraryClasses.size() + classpathClasses.size() + programClasses.size();
    }

    public Map<DexType, DexClass> getAllClasses() {
      return allClasses;
    }

    public ImmutableList<DexProgramClass> getProgramClasses() {
      return programClasses;
    }

    public ImmutableList<DexClasspathClass> getClasspathClasses() {
      return classpathClasses;
    }

    public ImmutableList<DexLibraryClass> getLibraryClasses() {
      return libraryClasses;
    }
  }

  private static <T extends DexClass> ImmutableList<T> fillPrioritizedClasses(
      Map<DexType, T> classCollection,
      Map<DexType, DexClass> prioritizedClasses,
      InternalOptions options) {
    if (classCollection != null) {
      Set<DexType> javaLibraryOverride = Sets.newIdentityHashSet();
      ImmutableList.Builder<T> builder = ImmutableList.builder();
      classCollection.forEach(
          (type, clazz) -> {
            DexClass other = prioritizedClasses.get(type);
            if (other == null) {
              prioritizedClasses.put(type, clazz);
              builder.add(clazz);
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
      return ImmutableList.of();
    }
  }

  private static void warnJavaLibraryOverride(
      InternalOptions options, Set<DexType> javaLibraryOverride) {
    String joined =
        javaLibraryOverride.stream()
            .sorted()
            .map(DexType::toString)
            .collect(Collectors.joining(", "));
    String message =
        "The following library types, prefixed by java.,"
            + " are present both as library and non library classes: "
            + joined
            + ". "
            + (options.lookupLibraryBeforeProgram ? "Non library" : "Library")
            + " classes will be ignored.";
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
    private final ProgramClassConflictResolver resolver;

    Builder(ProgramClassConflictResolver resolver, InternalOptions options, Timing timing) {
      super(options, timing);
      this.resolver = resolver;
      this.classpathClasses = ClasspathClassCollection.empty();
      this.libraryClasses = null;
    }

    private Builder(LazyLoadedDexApplication application) {
      super(application);
      this.resolver = ProgramClassCollection.defaultConflictResolver(application.options.reporter);
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
      return new LazyLoadedDexApplication(
          proguardMap,
          flags,
          ProgramClassCollection.create(getProgramClasses(), resolver),
          ImmutableList.copyOf(dataResourceProviders),
          classpathClasses,
          libraryClasses,
          options,
          highestSortingString,
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
