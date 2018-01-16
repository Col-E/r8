// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.origin.Origin;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class AppInfoWithSubtyping extends AppInfo {

  // Set of missing classes, discovered during subtypeMap computation.
  private final Set<DexType> missingClasses = Sets.newIdentityHashSet();
  // Map from types to their subtypes.
  private final Map<DexType, ImmutableSet<DexType>> subtypeMap = new IdentityHashMap<>();

  public AppInfoWithSubtyping(DexApplication application) {
    super(application);
    populateSubtypeMap(application.asDirect(), application.dexItemFactory);
  }

  protected AppInfoWithSubtyping(AppInfoWithSubtyping previous) {
    super(previous);
    missingClasses.addAll(previous.missingClasses);
    subtypeMap.putAll(previous.subtypeMap);
    assert app instanceof DirectMappedDexApplication;
  }

  protected AppInfoWithSubtyping(DirectMappedDexApplication application, GraphLense lense) {
    super(application, lense);
    // Recompute subtype map if we have modified the graph.
    populateSubtypeMap(application, dexItemFactory);
  }

  private DirectMappedDexApplication getDirectApplication() {
    // TODO(herhut): Remove need for cast.
    return (DirectMappedDexApplication) app;
  }

  public Iterable<DexLibraryClass> libraryClasses() {
    return getDirectApplication().libraryClasses();
  }

  public Set<DexType> getMissingClasses() {
    return Collections.unmodifiableSet(missingClasses);
  }

  public ImmutableSet<DexType> subtypes(DexType type) {
    assert type.isClassType();
    ImmutableSet<DexType> subtypes = subtypeMap.get(type);
    return subtypes == null ? ImmutableSet.of() : subtypes;
  }

  private void populateSuperType(Map<DexType, Set<DexType>> map, DexType superType,
      DexClass baseClass, Function<DexType, DexClass> definitions) {
    if (superType != null) {
      Set<DexType> set = map.computeIfAbsent(superType, ignore -> new HashSet<>());
      if (set.add(baseClass.type)) {
        // Only continue recursion if type has been added to set.
        populateAllSuperTypes(map, superType, baseClass, definitions);
      }
    }
  }

  private void populateAllSuperTypes(Map<DexType, Set<DexType>> map, DexType holder,
      DexClass baseClass, Function<DexType, DexClass> definitions) {
    DexClass holderClass = definitions.apply(holder);
    // Skip if no corresponding class is found.
    if (holderClass != null) {
      populateSuperType(map, holderClass.superType, baseClass, definitions);
      if (holderClass.superType != null) {
        holderClass.superType.addDirectSubtype(holder);
      } else {
        // We found java.lang.Object
        assert dexItemFactory.objectType == holder;
      }
      for (DexType inter : holderClass.interfaces.values) {
        populateSuperType(map, inter, baseClass, definitions);
        inter.addInterfaceSubtype(holder);
      }
      if (holderClass.isInterface()) {
        holder.tagAsInteface();
      }
    } else {
      if (!baseClass.isLibraryClass()) {
        missingClasses.add(holder);
      }
      // The subtype chain is broken, at least make this type a subtype of Object.
      if (holder != dexItemFactory.objectType) {
        dexItemFactory.objectType.addDirectSubtype(holder);
      }
    }
  }

  private void populateSubtypeMap(DirectMappedDexApplication app, DexItemFactory dexItemFactory) {
    dexItemFactory.clearSubtypeInformation();
    dexItemFactory.objectType.tagAsSubtypeRoot();
    Map<DexType, Set<DexType>> map = new IdentityHashMap<>();
    for (DexClass clazz : Iterables.<DexClass>concat(app.classes(), app.libraryClasses())) {
      populateAllSuperTypes(map, clazz.type, clazz, app::definitionFor);
    }
    for (Map.Entry<DexType, Set<DexType>> entry : map.entrySet()) {
      subtypeMap.put(entry.getKey(), ImmutableSet.copyOf(entry.getValue()));
    }
    assert DexType.validateLevelsAreCorrect(app::definitionFor, dexItemFactory);
  }

  // For mapping invoke virtual instruction to target methods.
  public Set<DexEncodedMethod> lookupVirtualTargets(DexMethod method) {
    Set<DexEncodedMethod> result = new HashSet<>();
    // First add the target for receiver type method.type.
    DexClass root = definitionFor(method.holder);
    if (root == null) {
      // type specified in method does not have a materialized class.
      return null;
    }
    ResolutionResult topTargets = resolveMethodOnClass(method.holder, method);
    if (topTargets.asResultOfResolve() == null) {
      // This will fail at runtime.
      return null;
    }
    topTargets.forEachTarget(result::add);
    // Add all matching targets from the subclass hierarchy.
    Set<DexType> set = subtypes(method.holder);
    if (set != null) {
      for (DexType type : set) {
        DexClass clazz = definitionFor(type);
        if (!clazz.isInterface()) {
          ResolutionResult methods = resolveMethodOnClass(type, method);
          methods.forEachTarget(result::add);
        }
      }
    }
    return result;
  }

  private boolean holderIsAbstract(Descriptor<?, ?> desc) {
    DexClass holder = definitionFor(desc.getHolder());
    return holder.accessFlags.isAbstract();
  }

  private boolean holderIsInterface(Descriptor<?, ?> desc) {
    DexClass holder = definitionFor(desc.getHolder());
    return holder == null || holder.accessFlags.isInterface();
  }

  /**
   * Lookup super method following the super chain from the holder of {@code method}.
   * <p>
   * This method will resolve the method on the holder of {@code method} and only return a non-null
   * value if the result of resolution was an instance (i.e. non-static) method.
   * <p>
   * Additionally, this will also verify that the invoke super is valid, i.e., it is on the same
   * type or a super type of the current context. See comment in {@link
   * com.android.tools.r8.ir.conversion.JarSourceCode#invokeType}.
   *
   * @param method the method to lookup
   * @param invocationContext the class the invoke is contained in, i.e., the holder of the caller.
   * @return The actual target for {@code method} or {@code null} if none found.
   */
  @Override
  public DexEncodedMethod lookupSuperTarget(DexMethod method, DexType invocationContext) {
    if (!invocationContext.isSubtypeOf(method.holder, this)) {
      DexClass contextClass = definitionFor(invocationContext);
      throw new CompilationError(
          "Illegal invoke-super to " + method.toSourceString() + " from class " + invocationContext,
          contextClass != null ? contextClass.getOrigin() : Origin.unknown());
    }
    return super.lookupSuperTarget(method, invocationContext);
  }

  // For mapping invoke interface instruction to target methods.
  public Set<DexEncodedMethod> lookupInterfaceTargets(DexMethod method) {
    // First check that there is a target for this invoke-interface to hit. If there is none,
    // this will fail at runtime.
    ResolutionResult topTarget = resolveMethodOnInterface(method.holder, method);
    if (topTarget.asResultOfResolve() == null) {
      return null;
    }
    Set<DexType> set = subtypes(method.holder);
    if (set == null) {
      return Collections.emptySet();
    }
    Set<DexEncodedMethod> result = new HashSet<>();
    for (DexType type : set) {
      DexClass clazz = definitionFor(type);
      // Default methods are looked up when looking at a specific subtype that does not
      // override them, so we ignore interfaces here. Otherwise, we would look up default methods
      // that are factually never used.
      if (!clazz.isInterface()) {
        ResolutionResult targetMethods = resolveMethodOnClass(type, method);
        targetMethods.forEachTarget(result::add);
      }
    }
    return result;
  }

  @Override
  public void registerNewType(DexType newType, DexType superType) {
    // Register the relationship between this type and its superType.
    superType.addDirectSubtype(newType);
  }

  @Override
  public boolean hasSubtyping() {
    return true;
  }

  @Override
  public AppInfoWithSubtyping withSubtyping() {
    return this;
  }
}
