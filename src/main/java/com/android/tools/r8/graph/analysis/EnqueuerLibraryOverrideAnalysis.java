// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.analysis;

import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClasspathOrLibraryClass;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.LookupMethodTarget;
import com.android.tools.r8.graph.ObjectAllocationInfoCollectionImpl;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.utils.DepthFirstSearchWorkListBase.StatefulDepthFirstSearchWorkList;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.TraversalContinuation;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class EnqueuerLibraryOverrideAnalysis extends EnqueuerAnalysis {

  private final Set<ClasspathOrLibraryClass> setWithSingleJavaLangObject;

  private final Map<DexClass, LibraryMethodOverridesInHierarchy> libraryClassesInHierarchyCache =
      new IdentityHashMap<>();
  private final AppView<? extends AppInfoWithClassHierarchy> appView;

  public EnqueuerLibraryOverrideAnalysis(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.appView = appView;
    DexClass javaLangObjectClass = appView.definitionFor(appView.dexItemFactory().objectType);
    if (javaLangObjectClass != null && javaLangObjectClass.isNotProgramClass()) {
      setWithSingleJavaLangObject =
          ImmutableSet.of(javaLangObjectClass.asClasspathOrLibraryClass());
    } else {
      setWithSingleJavaLangObject = Collections.emptySet();
    }
  }

  @Override
  public void done(Enqueuer enqueuer) {
    Set<DexProgramClass> liveProgramTypes = enqueuer.getLiveProgramTypes();
    ObjectAllocationInfoCollectionImpl objectAllocationInfoCollection =
        enqueuer.getObjectAllocationInfoCollection();
    liveProgramTypes.forEach(
        liveProgramType -> {
          if (!objectAllocationInfoCollection.isInstantiatedDirectly(liveProgramType)) {
            return;
          }
          // Abstract classes cannot be instantiated.
          if (liveProgramType.isAbstract()) {
            return;
          }
          markLibraryOverridesForProgramClass(liveProgramType);
        });
  }

  private void markLibraryOverridesForProgramClass(DexProgramClass programClass) {
    LibraryMethodOverridesInHierarchy libraryMethodOverridesInHierarchy =
        ensureLibraryClassesPopulatedForProgramClass(programClass);
    AppInfoWithClassHierarchy appInfoWithClassHierarchy = appView.appInfo();
    libraryMethodOverridesInHierarchy.forEachOverriddenMethod(
        (libraryClass, method) ->
            appInfoWithClassHierarchy
                .resolveMethodOn(libraryClass.asDexClass(), method.getReference())
                .forEachMethodResolutionResult(
                    result -> {
                      LookupMethodTarget lookupTarget =
                          result.lookupVirtualDispatchTarget(
                              programClass,
                              appInfoWithClassHierarchy,
                              emptyConsumer(),
                              failingMethod -> {
                                DexClass holderClass =
                                    appInfoWithClassHierarchy.definitionFor(
                                        failingMethod.getContextType());
                                if (holderClass != null && holderClass.isProgramClass()) {
                                  failingMethod.setLibraryMethodOverride(OptionalBool.TRUE);
                                }
                              });
                      if (lookupTarget != null) {
                        DexEncodedMethod definition = lookupTarget.getDefinition();
                        definition.setLibraryMethodOverride(OptionalBool.TRUE);
                      }
                    }));
  }

  /***
   * Populating the library state for an instantiated program class is done by a DFS
   * algorithm where we compute all library classes in the hierarchy and then, when doing
   * backtracking, we check if a program method has a corresponding definition in one of its library
   * classes. If a program method potentially overrides a library method we keep track of it for
   * simulating virtual dispatch on it later.
   *
   * A special care has to be taken here when interfaces contributing to classes later in the
   * hierarchy than the override:
   * <pre>
   * class ProgramA {
   *   foo() {...}
   * }
   *
   * interface Lib {
   *   foo();
   * }
   *
   * class ProgramB extends ProgramA implements Lib { }
   * </pre>
   *
   * This pattern does not satisfy the DFS algorithm since ProgramB do not directly override any
   * methods in Lib. As a consequence we always consider all interface methods to be overridden. In
   * general this is almost always the case since we do not see many default library methods in the
   * library.
   *
   * @return the computed library method override state for this program class.
   */
  private LibraryMethodOverridesInHierarchy ensureLibraryClassesPopulatedForProgramClass(
      DexProgramClass clazz) {
    new StatefulDepthFirstSearchWorkList<DexClass, LibraryMethodOverridesInHierarchy, Void>() {

      @Override
      @SuppressWarnings("ReturnValueIgnored")
      protected TraversalContinuation<Void, LibraryMethodOverridesInHierarchy> process(
          DFSNodeWithState<DexClass, LibraryMethodOverridesInHierarchy> node,
          Function<DexClass, DFSNodeWithState<DexClass, LibraryMethodOverridesInHierarchy>>
              childNodeConsumer) {
        DexClass clazz = node.getNode();
        LibraryMethodOverridesInHierarchy libraryMethodOverridesInHierarchy =
            libraryClassesInHierarchyCache.get(clazz);
        if (libraryMethodOverridesInHierarchy != null) {
          node.setState(libraryMethodOverridesInHierarchy);
        } else {
          clazz.forEachImmediateSupertype(
              superType ->
                  appView
                      .contextIndependentDefinitionForWithResolutionResult(superType)
                      .forEachClassResolutionResult(childNodeConsumer::apply));
        }
        return TraversalContinuation.doContinue();
      }

      @Override
      protected TraversalContinuation<Void, LibraryMethodOverridesInHierarchy> joiner(
          DFSNodeWithState<DexClass, LibraryMethodOverridesInHierarchy> node,
          List<DFSNodeWithState<DexClass, LibraryMethodOverridesInHierarchy>> childStates) {
        DexClass clazz = node.getNode();
        if (node.getState() == null) {
          LibraryMethodOverridesInHierarchy.Builder builder =
              LibraryMethodOverridesInHierarchy.builder();
          childStates.forEach(childState -> builder.addParentState(childState.getState()));
          if (clazz.isNotProgramClass()) {
            builder.addLibraryClass(clazz.asClasspathOrLibraryClass());
          }
          builder.buildLibraryClasses(setWithSingleJavaLangObject);
          if (clazz.isProgramClass()) {
            clazz
                .asProgramClass()
                .virtualProgramMethods()
                .forEach(builder::addLibraryOverridesForMethod);
          }
          LibraryMethodOverridesInHierarchy newState = builder.build();
          node.setState(newState);
          LibraryMethodOverridesInHierarchy oldState =
              libraryClassesInHierarchyCache.put(clazz, newState);
          assert oldState == null;
        }
        return TraversalContinuation.doContinue();
      }
    }.run(clazz);

    return libraryClassesInHierarchyCache.get(clazz);
  }

  /***
   * The LibraryMethodOverridesInHierarchy keeps track of the library classes and the potential
   * overridden library methods for each class in the hierarchy of a class. The libraryClasses are
   * a flattened list of all library classes that exists in the hierarchy and the overriden library
   * methods is a map of methods that may be overridden for the current class.
   */
  private static class LibraryMethodOverridesInHierarchy {

    private final List<LibraryMethodOverridesInHierarchy> parentStates;
    private final Set<ClasspathOrLibraryClass> libraryClasses;
    private final Map<DexEncodedMethod, ClasspathOrLibraryClass> overriddenLibraryMethods;

    private LibraryMethodOverridesInHierarchy(
        List<LibraryMethodOverridesInHierarchy> parentStates,
        Set<ClasspathOrLibraryClass> libraryClasses,
        Map<DexEncodedMethod, ClasspathOrLibraryClass> overriddenLibraryMethods) {
      this.parentStates = parentStates;
      this.libraryClasses = libraryClasses;
      this.overriddenLibraryMethods = overriddenLibraryMethods;
    }

    public void forEachOverriddenMethod(
        BiConsumer<ClasspathOrLibraryClass, DexEncodedMethod> consumer) {
      forEachOverriddenMethod(consumer, Sets.newIdentityHashSet());
    }

    private void forEachOverriddenMethod(
        BiConsumer<ClasspathOrLibraryClass, DexEncodedMethod> consumer,
        Set<DexEncodedMethod> seen) {
      overriddenLibraryMethods.forEach(
          (method, clazz) -> {
            if (seen.add(method)) {
              consumer.accept(clazz, method);
            }
          });
      parentStates.forEach(parentState -> parentState.forEachOverriddenMethod(consumer, seen));
    }

    public static Builder builder() {
      return new Builder();
    }

    public static class Builder {

      private Map<DexEncodedMethod, ClasspathOrLibraryClass> overriddenLibraryMethods;
      private Set<ClasspathOrLibraryClass> libraryClasses;
      private final List<LibraryMethodOverridesInHierarchy> parentStates = new ArrayList<>();
      private boolean hasBuildLibraryClasses = false;

      public void addParentState(LibraryMethodOverridesInHierarchy state) {
        assert !hasBuildLibraryClasses;
        parentStates.add(state);
      }

      public void addLibraryClass(ClasspathOrLibraryClass libraryClass) {
        ensureLibraryClasses();
        libraryClasses.add(libraryClass);
        if (libraryClass.isInterface()) {
          ensureOverriddenLibraryMethods();
          libraryClass
              .virtualMethods()
              .forEach(
                  virtualMethod ->
                      overriddenLibraryMethods.putIfAbsent(virtualMethod, libraryClass));
        }
      }

      private void ensureLibraryClasses() {
        if (libraryClasses == null) {
          libraryClasses = new LinkedHashSet<>();
        }
      }

      private void buildLibraryClasses(Set<ClasspathOrLibraryClass> setWithSingleJavaLangObject) {
        if (libraryClasses != null) {
          parentStates.forEach(parentState -> libraryClasses.addAll(parentState.libraryClasses));
        } else if (parentStates.size() == 1) {
          libraryClasses = parentStates.get(0).libraryClasses;
        } else {
          libraryClasses = new LinkedHashSet<>();
          for (LibraryMethodOverridesInHierarchy parentState : parentStates) {
            libraryClasses.addAll(parentState.libraryClasses);
          }
          if (libraryClasses.size() == 1) {
            assert libraryClasses.equals(setWithSingleJavaLangObject);
            libraryClasses = setWithSingleJavaLangObject;
          }
        }
        hasBuildLibraryClasses = true;
      }

      private void addLibraryOverridesForMethod(ProgramMethod method) {
        assert hasBuildLibraryClasses;
        DexMethod reference = method.getReference();
        libraryClasses.forEach(
            libraryClass -> {
              if (libraryClass.isInterface()) {
                // All interface methods are already added when visiting.
                return;
              }
              DexEncodedMethod libraryMethod = libraryClass.lookupVirtualMethod(reference);
              if (libraryMethod != null) {
                addOverriddenMethod(libraryClass, libraryMethod);
              }
            });
      }

      private void addOverriddenMethod(ClasspathOrLibraryClass clazz, DexEncodedMethod method) {
        ensureOverriddenLibraryMethods();
        ClasspathOrLibraryClass existing = overriddenLibraryMethods.put(method, clazz);
        assert existing == null;
      }

      private void ensureOverriddenLibraryMethods() {
        if (overriddenLibraryMethods == null) {
          overriddenLibraryMethods = new IdentityHashMap<>();
        }
      }

      public LibraryMethodOverridesInHierarchy build() {
        return new LibraryMethodOverridesInHierarchy(
            parentStates,
            libraryClasses,
            overriddenLibraryMethods == null ? Collections.emptyMap() : overriddenLibraryMethods);
      }
    }
  }
}
