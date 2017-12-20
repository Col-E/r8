// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.ir.code.Invoke.Type;
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
  private Set<DexType> missingClasses = Sets.newIdentityHashSet();
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

  public DexEncodedMethod lookup(Type type, DexMethod target, DexType invocationContext) {
    DexType holder = target.getHolder();
    if (!holder.isClassType()) {
      return null;
    }
    switch (type) {
      case VIRTUAL:
        return lookupSingleVirtualTarget(target);
      case INTERFACE:
        return lookupSingleInterfaceTarget(target);
      case DIRECT:
        return lookupDirectTarget(target);
      case STATIC:
        return lookupStaticTarget(target);
      case SUPER:
        return lookupSuperTarget(target, invocationContext);
      default:
        return null;
    }
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

  /**
   * For mapping invoke virtual instruction to single target method.
   */
  public DexEncodedMethod lookupSingleVirtualTarget(DexMethod method) {
    assert method != null;
    DexClass holder = definitionFor(method.holder);
    if ((holder == null) || holder.isLibraryClass() || holder.isInterface()) {
      return null;
    }
    if (method.isSingleVirtualMethodCached()) {
      return method.getSingleVirtualMethodCache();
    }
    // First add the target for receiver type method.type.
    ResolutionResult topMethod = resolveMethod(method.holder, method);
    // We might hit none or multiple targets. Both make this fail at runtime.
    if (!topMethod.hasSingleTarget() || !topMethod.asSingleTarget().isVirtualMethod()) {
      method.setSingleVirtualMethodCache(null);
      return null;
    }
    DexEncodedMethod result = topMethod.asSingleTarget();
    // Search for matching target in subtype hierarchy.
    Set<DexType> set = subtypes(method.holder);
    if (set != null) {
      for (DexType type : set) {
        DexClass clazz = definitionFor(type);
        if (!clazz.isInterface()) {
          if (clazz.lookupMethod(method) != null) {
            method.setSingleVirtualMethodCache(null);
            return null;  // We have more than one target method.
          }
        }
      }
    }
    method.setSingleVirtualMethodCache(result);
    return result;
  }

  private boolean holderIsAbstract(Descriptor<?,?> desc) {
    DexClass holder = definitionFor(desc.getHolder());
    return holder.accessFlags.isAbstract();
  }

  private boolean holderIsInterface(Descriptor<?, ?> desc) {
    DexClass holder = definitionFor(desc.getHolder());
    return holder == null || holder.accessFlags.isInterface();
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

  public DexEncodedMethod lookupSingleInterfaceTarget(DexMethod method) {
    DexClass holder = definitionFor(method.holder);
    if ((holder == null) || holder.isLibraryClass() || !holder.accessFlags.isInterface()) {
      return null;
    }
    // First check that there is a target for this invoke-interface to hit. If there is none,
    // this will fail at runtime.
    ResolutionResult topTarget = resolveMethodOnInterface(method.holder, method);
    if (topTarget.asResultOfResolve() == null) {
      return null;
    }
    DexEncodedMethod result = null;
    Set<DexType> set = subtypes(method.holder);
    if (set != null) {
      for (DexType type : set) {
        DexClass clazz = definitionFor(type);
        // Default methods are looked up when looking at a specific subtype that does not
        // override them, so we ignore interfaces here. Otherwise, we would look up default methods
        // that are factually never used.
        if (!clazz.isInterface()) {
          ResolutionResult resolutionResult = resolveMethodOnClass(type, method);
          if (resolutionResult.hasSingleTarget()) {
            if ((result != null) && (result != resolutionResult.asSingleTarget())) {
              return null;
            } else {
              result = resolutionResult.asSingleTarget();
            }
          }
        }
      }
    }
    return result == null || !result.isVirtualMethod() ? null : result;
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
