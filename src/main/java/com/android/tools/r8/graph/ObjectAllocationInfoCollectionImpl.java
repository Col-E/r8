// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.shaking.GraphReporter;
import com.android.tools.r8.shaking.InstantiationReason;
import com.android.tools.r8.shaking.KeepReason;
import com.android.tools.r8.shaking.MissingClasses;
import com.android.tools.r8.utils.LensUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Provides information about all possibly instantiated classes and lambdas, their allocation sites,
 * if known, as well as the full subtyping hierarchy of types above them.
 */
public abstract class ObjectAllocationInfoCollectionImpl implements ObjectAllocationInfoCollection {

  /** Instantiated classes with the contexts of the instantiations. */
  final Map<DexProgramClass, Set<DexEncodedMethod>> classesWithAllocationSiteTracking =
      new IdentityHashMap<>();

  /** Instantiated classes without contexts. */
  final Set<DexProgramClass> classesWithoutAllocationSiteTracking = Sets.newIdentityHashSet();

  /** Set of annotation types for which the subtype hierarchy is unknown from that type. */
  final Set<DexProgramClass> annotationsWithUnknownSubtypeHierarchy = Sets.newIdentityHashSet();

  /**
   * Set of interface types for which the subtype hierarchy is unknown from that type.
   *
   * <p>E.g., the type is kept thus there could be instantiations of subtypes.
   *
   * <p>TODO(b/145344105): Generalize this to typesWithUnknownSubtypeHierarchy.
   */
  final Set<DexProgramClass> interfacesWithUnknownSubtypeHierarchy = Sets.newIdentityHashSet();

  /** Map of types directly implemented by lambdas to those types. */
  final Map<DexType, List<LambdaDescriptor>> instantiatedLambdas = new IdentityHashMap<>();

  /**
   * Hierarchy for instantiated types mapping a type to the set of immediate subtypes for which some
   * subtype is either an instantiated class, kept interface or is implemented by an instantiated
   * lambda.
   */
  Map<DexType, Set<DexClass>> instantiatedHierarchy = new IdentityHashMap<>();

  private ObjectAllocationInfoCollectionImpl() {
    // Only builder can allocate an instance.
  }

  public static Builder builder(boolean trackAllocationSites, GraphReporter reporter) {
    return new Builder(trackAllocationSites, reporter);
  }

  public abstract void mutate(Consumer<Builder> mutator, AppInfo appInfo);

  /**
   * True if a class type might be instantiated directly at the given type.
   *
   * <p>Should not be called on interface types.
   *
   * <p>TODO(b/145344105): Extend this to not be called on any abstract types.
   */
  @Override
  public boolean isInstantiatedDirectly(DexProgramClass clazz) {
    if (clazz.isInterface()) {
      return false;
    }
    if (classesWithAllocationSiteTracking.containsKey(clazz)) {
      assert !classesWithAllocationSiteTracking.get(clazz).isEmpty();
      return true;
    }
    return classesWithoutAllocationSiteTracking.contains(clazz);
  }

  /** True if the type or subtype of it might be instantiated. */
  @Override
  public boolean isInstantiatedDirectlyOrHasInstantiatedSubtype(DexProgramClass clazz) {
    return (!clazz.isInterface() && isInstantiatedDirectly(clazz))
        || hasInstantiatedStrictSubtype(clazz);
  }

  /** True if there might exist an instantiated (strict) subtype of the given type. */
  @Override
  public boolean hasInstantiatedStrictSubtype(DexProgramClass clazz) {
    if (instantiatedHierarchy.get(clazz.type) != null) {
      return true;
    }
    if (!clazz.isInterface()) {
      return false;
    }
    return annotationsWithUnknownSubtypeHierarchy.contains(clazz)
        || interfacesWithUnknownSubtypeHierarchy.contains(clazz)
        || isImmediateInterfaceOfInstantiatedLambda(clazz);
  }

  /** True if the type is an interface that has unknown instantiations, eg, by being kept. */
  @Override
  public boolean isInterfaceWithUnknownSubtypeHierarchy(DexProgramClass clazz) {
    return clazz.isInterface() && interfacesWithUnknownSubtypeHierarchy.contains(clazz);
  }

  /** Returns true if the type is an immediate interface of an instantiated lambda. */
  @Override
  public boolean isImmediateInterfaceOfInstantiatedLambda(DexProgramClass iface) {
    return iface.isInterface() && instantiatedLambdas.get(iface.type) != null;
  }

  public Set<DexClass> getImmediateSubtypesInInstantiatedHierarchy(DexType type) {
    return instantiatedHierarchy.get(type);
  }

  @Override
  public void forEachClassWithKnownAllocationSites(
      BiConsumer<DexProgramClass, Set<DexEncodedMethod>> consumer) {
    classesWithAllocationSiteTracking.forEach(consumer);
  }

  @Override
  public boolean isAllocationSitesKnown(DexProgramClass clazz) {
    return classesWithAllocationSiteTracking.containsKey(clazz);
  }

  @Override
  public ObjectAllocationInfoCollectionImpl rewrittenWithLens(
      DexDefinitionSupplier definitions, GraphLens lens, Timing timing) {
    return timing.time(
        "Rewrite ObjectAllocationInfoCollectionImpl", () -> rewrittenWithLens(definitions, lens));
  }

  private ObjectAllocationInfoCollectionImpl rewrittenWithLens(
      DexDefinitionSupplier definitions, GraphLens lens) {
    return builder(true, null).rewrittenWithLens(this, definitions, lens).build(definitions);
  }

  public ObjectAllocationInfoCollectionImpl withoutPrunedItems(PrunedItems prunedItems) {
    if (prunedItems.hasRemovedMethods()) {
      Iterator<Entry<DexProgramClass, Set<DexEncodedMethod>>> iterator =
          classesWithAllocationSiteTracking.entrySet().iterator();
      while (iterator.hasNext()) {
        Entry<DexProgramClass, Set<DexEncodedMethod>> entry = iterator.next();
        Set<DexEncodedMethod> allocationSites = entry.getValue();
        allocationSites.removeIf(
            allocationSite ->
                prunedItems.getRemovedMethods().contains(allocationSite.getReference()));
        if (allocationSites.isEmpty()) {
          classesWithoutAllocationSiteTracking.add(entry.getKey());
          iterator.remove();
        }
      }
    }
    return this;
  }

  public void forEachInstantiatedSubType(
      DexType type,
      Consumer<DexProgramClass> onClass,
      Consumer<LambdaDescriptor> onLambda,
      AppInfo appInfo) {
    traverseInstantiatedSubtypes(
        type,
        clazz -> {
          onClass.accept(clazz);
          return TraversalContinuation.doContinue();
        },
        lambda -> {
          onLambda.accept(lambda);
          return TraversalContinuation.doContinue();
        },
        appInfo);
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public TraversalContinuation<?, ?> traverseInstantiatedSubtypes(
      DexType type,
      Function<DexProgramClass, TraversalContinuation<?, ?>> onClass,
      Function<LambdaDescriptor, TraversalContinuation<?, ?>> onLambda,
      AppInfo appInfo) {
    WorkList<DexClass> worklist = WorkList.newIdentityWorkList();
    if (type == appInfo.dexItemFactory().objectType) {
      // All types are below java.lang.Object, but we don't maintain an entry for it.
      instantiatedHierarchy.forEach(
          (key, subtypes) -> {
            DexClass clazz = appInfo.definitionFor(key);
            if (clazz != null) {
              worklist.addIfNotSeen(clazz);
            }
            worklist.addIfNotSeen(subtypes);
          });
    } else {
      DexClass initialClass = appInfo.definitionFor(type);
      if (initialClass == null) {
        // If no definition for the type is found, populate the worklist with any
        // instantiated subtypes and callback with any lambda instance.
        worklist.addIfNotSeen(instantiatedHierarchy.getOrDefault(type, Collections.emptySet()));
        for (LambdaDescriptor lambda :
            instantiatedLambdas.getOrDefault(type, Collections.emptyList())) {
          if (onLambda.apply(lambda).shouldBreak()) {
            return TraversalContinuation.doBreak();
          }
        }
      } else {
        worklist.addIfNotSeen(initialClass);
      }
    }

    return worklist.run(
        clazz -> {
          if (clazz.isProgramClass()) {
            DexProgramClass programClass = clazz.asProgramClass();
            if (isInstantiatedDirectly(programClass)
                || isInterfaceWithUnknownSubtypeHierarchy(programClass)) {
              if (onClass.apply(programClass).shouldBreak()) {
                return TraversalContinuation.doBreak();
              }
            }
          }
          worklist.addIfNotSeen(
              instantiatedHierarchy.getOrDefault(clazz.type, Collections.emptySet()));
          for (LambdaDescriptor lambda :
              instantiatedLambdas.getOrDefault(clazz.type, Collections.emptyList())) {
            if (onLambda.apply(lambda).shouldBreak()) {
              return TraversalContinuation.doBreak();
            }
          }
          return TraversalContinuation.doContinue();
        });
  }

  public Set<DexType> getInstantiatedLambdaInterfaces() {
    return instantiatedLambdas.keySet();
  }

  @Override
  public void forEachInstantiatedLambdaInterfaces(Consumer<DexType> consumer) {
    getInstantiatedLambdaInterfaces().forEach(consumer);
  }

  public void removeAllocationsForPrunedItems(PrunedItems prunedItems) {
    Set<DexType> removedClasses = prunedItems.getRemovedClasses();
    if (removedClasses.isEmpty()) {
      return;
    }
    classesWithAllocationSiteTracking
        .entrySet()
        .removeIf(entry -> removedClasses.contains(entry.getKey().getType()));
    classesWithoutAllocationSiteTracking.removeIf(
        clazz -> removedClasses.contains(clazz.getType()));
    annotationsWithUnknownSubtypeHierarchy.removeIf(
        annotation -> removedClasses.contains(annotation.getType()));
    boolean removed =
        interfacesWithUnknownSubtypeHierarchy.removeIf(
            iface -> removedClasses.contains(iface.getType()));
    assert !removed : "Unexpected removal of an interface marking an unknown hierarchy.";
    removedClasses.forEach(instantiatedLambdas::remove);
  }

  public boolean verifyAllocatedTypesAreLive(
      Set<DexType> liveTypes, MissingClasses missingClasses, DexDefinitionSupplier definitions) {
    for (DexProgramClass clazz : classesWithAllocationSiteTracking.keySet()) {
      assert liveTypes.contains(clazz.getType());
    }
    for (DexProgramClass clazz : classesWithoutAllocationSiteTracking) {
      assert liveTypes.contains(clazz.getType());
    }
    for (DexProgramClass annotation : annotationsWithUnknownSubtypeHierarchy) {
      assert liveTypes.contains(annotation.getType());
    }
    for (DexProgramClass iface : interfacesWithUnknownSubtypeHierarchy) {
      assert liveTypes.contains(iface.getType());
    }
    for (DexType iface : instantiatedLambdas.keySet()) {
      assert missingClasses.contains(iface)
          || definitions.definitionFor(iface).isNotProgramClass()
          || liveTypes.contains(iface);
    }
    return true;
  }

  public static class Builder extends ObjectAllocationInfoCollectionImpl {

    private static class Data {

      private final boolean trackAllocationSites;
      private final GraphReporter reporter;

      private Data(boolean trackAllocationSites, GraphReporter reporter) {
        this.trackAllocationSites = trackAllocationSites;
        this.reporter = reporter;
      }
    }

    // Pointer to data valid during the duration of the builder.
    private Data data;

    private Builder(boolean trackAllocationSites, GraphReporter reporter) {
      data = new Data(trackAllocationSites, reporter);
    }

    public ObjectAllocationInfoCollectionImpl build(DexDefinitionSupplier definitions) {
      assert data != null;
      if (instantiatedHierarchy == null) {
        repopulateInstantiatedHierarchy(definitions);
      }
      assert validate(definitions);
      data = null;
      return this;
    }

    // Consider a mutation interface that has just the mutation methods.
    @Override
    public void mutate(Consumer<Builder> mutator, AppInfo appInfo) {
      mutator.accept(this);
      repopulateInstantiatedHierarchy(appInfo);
    }

    private boolean shouldTrackAllocationSitesForClass(
        DexProgramClass clazz, InstantiationReason instantiationReason) {
      if (!data.trackAllocationSites) {
        return false;
      }
      if (instantiationReason != InstantiationReason.NEW_INSTANCE_INSTRUCTION) {
        // There is an allocation site which is not a new-instance instruction.
        return false;
      }
      if (classesWithoutAllocationSiteTracking.contains(clazz)) {
        // We already gave up on tracking the allocation sites for `clazz` previously.
        return false;
      }
      return true;
    }

    /**
     * Records that {@param clazz} is instantiated in {@param context}.
     *
     * @return true if {@param clazz} was not instantiated before.
     */
    public boolean recordDirectAllocationSite(
        DexProgramClass clazz,
        ProgramMethod context,
        InstantiationReason instantiationReason,
        KeepReason keepReason,
        AppInfo appInfo) {
      assert !clazz.isInterface();
      if (data.reporter != null) {
        data.reporter.registerClass(clazz, keepReason);
      }
      populateInstantiatedHierarchy(appInfo, clazz);
      if (shouldTrackAllocationSitesForClass(clazz, instantiationReason)) {
        assert context != null;
        Set<DexEncodedMethod> allocationSitesForClass =
            classesWithAllocationSiteTracking.computeIfAbsent(
                clazz, ignore -> Sets.newIdentityHashSet());
        allocationSitesForClass.add(context.getDefinition());
        return allocationSitesForClass.size() == 1;
      }
      if (classesWithoutAllocationSiteTracking.add(clazz)) {
        Set<DexEncodedMethod> allocationSitesForClass =
            classesWithAllocationSiteTracking.remove(clazz);
        return allocationSitesForClass == null;
      }
      return false;
    }

    public boolean recordInstantiatedAnnotation(DexProgramClass annotation, AppInfo appInfo) {
      assert annotation.isInterface();
      assert annotation.isAnnotation();
      if (annotationsWithUnknownSubtypeHierarchy.add(annotation)) {
        populateInstantiatedHierarchy(appInfo, annotation);
        return true;
      }
      return false;
    }

    public boolean recordInstantiatedInterface(DexProgramClass iface, AppInfo appInfo) {
      assert iface.isInterface();
      assert !iface.isAnnotation();
      if (interfacesWithUnknownSubtypeHierarchy.add(iface)) {
        populateInstantiatedHierarchy(appInfo, iface);
        return true;
      }
      return false;
    }

    public void recordInstantiatedLambdaInterface(
        DexType iface, LambdaDescriptor lambda, AppInfo appInfo) {
      instantiatedLambdas.computeIfAbsent(iface, key -> new ArrayList<>()).add(lambda);
      populateInstantiatedHierarchy(appInfo, iface);
    }

    private void repopulateInstantiatedHierarchy(DexDefinitionSupplier definitions) {
      instantiatedHierarchy = new IdentityHashMap<>();
      classesWithAllocationSiteTracking
          .keySet()
          .forEach(clazz -> populateInstantiatedHierarchy(definitions, clazz));
      classesWithoutAllocationSiteTracking.forEach(
          clazz -> populateInstantiatedHierarchy(definitions, clazz));
      interfacesWithUnknownSubtypeHierarchy.forEach(
          clazz -> populateInstantiatedHierarchy(definitions, clazz));
      instantiatedLambdas
          .keySet()
          .forEach(type -> populateInstantiatedHierarchy(definitions, type));
    }

    private void populateInstantiatedHierarchy(DexDefinitionSupplier definitions, DexType type) {
      DexClass clazz = definitions.definitionFor(type);
      if (clazz != null) {
        populateInstantiatedHierarchy(definitions, clazz);
      }
    }

    public void injectInterfaces(
        DexDefinitionSupplier definitions, DexProgramClass clazz, Set<DexClass> newInterfaces) {
      for (DexClass newInterface : newInterfaces) {
        populateInstantiatedHierarchy(definitions, newInterface.type, clazz);
      }
    }

    private void populateInstantiatedHierarchy(DexDefinitionSupplier definitions, DexClass clazz) {
      if (clazz.superType != null) {
        populateInstantiatedHierarchy(definitions, clazz.superType, clazz);
      }
      for (DexType iface : clazz.interfaces.values) {
        populateInstantiatedHierarchy(definitions, iface, clazz);
      }
    }

    @SuppressWarnings("ReferenceEquality")
    private void populateInstantiatedHierarchy(
        DexDefinitionSupplier definitions, DexType type, DexClass subtype) {
      if (type == definitions.dexItemFactory().objectType) {
        return;
      }
      Set<DexClass> subtypes = instantiatedHierarchy.get(type);
      if (subtypes != null) {
        subtypes.add(subtype);
        return;
      }
      // This is the first time an instantiation appears below 'type', recursively populate.
      subtypes = Sets.newIdentityHashSet();
      subtypes.add(subtype);
      instantiatedHierarchy.put(type, subtypes);
      populateInstantiatedHierarchy(definitions, type);
    }

    public void markNoLongerInstantiated(DexProgramClass clazz) {
      classesWithAllocationSiteTracking.remove(clazz);
      classesWithoutAllocationSiteTracking.remove(clazz);
      instantiatedHierarchy = null;
    }

    Builder rewrittenWithLens(
        ObjectAllocationInfoCollectionImpl objectAllocationInfos,
        DexDefinitionSupplier definitions,
        GraphLens lens) {
      instantiatedHierarchy = null;
      objectAllocationInfos.classesWithoutAllocationSiteTracking.forEach(
          clazz -> {
            DexType type = lens.lookupType(clazz.type);
            if (type.isPrimitiveType()) {
              return;
            }
            DexProgramClass rewrittenClass = asProgramClassOrNull(definitions.definitionFor(type));
            assert rewrittenClass != null;
            classesWithoutAllocationSiteTracking.add(rewrittenClass);
          });
      objectAllocationInfos.classesWithAllocationSiteTracking.forEach(
          (clazz, allocationSitesForClass) -> {
            DexType type = lens.lookupType(clazz.type);
            if (type.isPrimitiveType()) {
              assert clazz.isEnum();
              return;
            }
            DexProgramClass rewrittenClass = asProgramClassOrNull(definitions.definitionFor(type));
            assert rewrittenClass != null;
            if (classesWithoutAllocationSiteTracking.contains(rewrittenClass)) {
              // Either this class was merged into another class without allocation site tracking,
              // or a class without allocation site tracking was merged into this class.
              return;
            }
            classesWithAllocationSiteTracking
                .computeIfAbsent(rewrittenClass, ignore -> Sets.newIdentityHashSet())
                .addAll(
                    LensUtils.rewrittenWithRenamedSignature(
                        allocationSitesForClass, definitions, lens));
          });
      for (DexProgramClass abstractType :
          objectAllocationInfos.interfacesWithUnknownSubtypeHierarchy) {
        DexType type = lens.lookupType(abstractType.type);
        if (type.isPrimitiveType()) {
          assert false;
          continue;
        }
        DexProgramClass rewrittenClass = asProgramClassOrNull(definitions.definitionFor(type));
        assert rewrittenClass != null;
        assert !interfacesWithUnknownSubtypeHierarchy.contains(rewrittenClass);
        interfacesWithUnknownSubtypeHierarchy.add(rewrittenClass);
      }
      objectAllocationInfos.instantiatedLambdas.forEach(
          (iface, lambdas) -> {
            DexType type = lens.lookupType(iface);
            if (type.isPrimitiveType()) {
              assert false;
              return;
            }
            // TODO(b/150277553): Rewrite lambda descriptor.
            instantiatedLambdas.computeIfAbsent(type, ignoreKey(ArrayList::new)).addAll(lambdas);
          });
      return this;
    }

    // Validation that all types are linked in the instantiated hierarchy map.
    boolean validate(DexDefinitionSupplier definitions) {
      classesWithAllocationSiteTracking.forEach(
          (clazz, contexts) -> {
            assert !clazz.isInterface();
            assert !classesWithoutAllocationSiteTracking.contains(clazz);
            assert verifyAllSuperTypesAreInHierarchy(definitions, clazz.allImmediateSupertypes());
          });
      classesWithoutAllocationSiteTracking.forEach(
          clazz -> {
            assert !clazz.isInterface();
            assert !classesWithAllocationSiteTracking.containsKey(clazz);
            assert verifyAllSuperTypesAreInHierarchy(definitions, clazz.allImmediateSupertypes());
          });
      instantiatedLambdas.forEach(
          (iface, lambdas) -> {
            assert !lambdas.isEmpty();
            DexClass definition = definitions.definitionFor(iface);
            if (definition != null) {
              assert definition.isInterface();
              assert verifyAllSuperTypesAreInHierarchy(
                  definitions, definition.allImmediateSupertypes());
            }
          });
      for (DexProgramClass iface : interfacesWithUnknownSubtypeHierarchy) {
        verifyAllSuperTypesAreInHierarchy(definitions, iface.allImmediateSupertypes());
      }
      instantiatedHierarchy.forEach(
          (type, subtypes) -> {
            assert !subtypes.isEmpty();
            for (DexClass subtype : subtypes) {
              assert isImmediateSuperType(type, subtype);
            }
          });
      return true;
    }

    private boolean verifyAllSuperTypesAreInHierarchy(
        DexDefinitionSupplier definitions, Iterable<DexType> dexTypes) {
      for (DexType supertype : dexTypes) {
        assert typeIsInHierarchy(definitions, supertype)
            : "Type not found in hierarchy: " + supertype;
      }
      return true;
    }

    @SuppressWarnings("ReferenceEquality")
    private boolean typeIsInHierarchy(DexDefinitionSupplier definitions, DexType type) {
      return type == definitions.dexItemFactory().objectType
          || instantiatedHierarchy.containsKey(type);
    }

    @SuppressWarnings("ReferenceEquality")
    private boolean isImmediateSuperType(DexType type, DexClass subtype) {
      for (DexType supertype : subtype.allImmediateSupertypes()) {
        if (type == supertype) {
          return true;
        }
      }
      return false;
    }
  }
}
