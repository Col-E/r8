// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.shaking.GraphReporter;
import com.android.tools.r8.shaking.InstantiationReason;
import com.android.tools.r8.shaking.KeepReason;
import com.android.tools.r8.utils.LensUtils;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Stores the set of instantiated classes along with their allocation sites. */
public class ObjectAllocationInfoCollectionImpl implements ObjectAllocationInfoCollection {

  private final Map<DexProgramClass, Set<DexEncodedMethod>> classesWithAllocationSiteTracking;
  private final Set<DexProgramClass> classesWithoutAllocationSiteTracking;

  private ObjectAllocationInfoCollectionImpl(
      Map<DexProgramClass, Set<DexEncodedMethod>> classesWithAllocationSiteTracking,
      Set<DexProgramClass> classesWithoutAllocationSiteTracking) {
    this.classesWithAllocationSiteTracking = classesWithAllocationSiteTracking;
    this.classesWithoutAllocationSiteTracking = classesWithoutAllocationSiteTracking;
  }

  public static Builder builder(boolean trackAllocationSites, GraphReporter reporter) {
    return new Builder(trackAllocationSites, reporter);
  }

  public void markNoLongerInstantiated(DexProgramClass clazz) {
    classesWithAllocationSiteTracking.remove(clazz);
    classesWithoutAllocationSiteTracking.remove(clazz);
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
  public boolean isInstantiatedDirectly(DexProgramClass clazz) {
    if (classesWithAllocationSiteTracking.containsKey(clazz)) {
      assert !classesWithAllocationSiteTracking.get(clazz).isEmpty();
      return true;
    }
    return classesWithoutAllocationSiteTracking.contains(clazz);
  }

  @Override
  public ObjectAllocationInfoCollectionImpl rewrittenWithLens(
      DexDefinitionSupplier definitions, GraphLense lens) {
    return builder(true, null).rewrittenWithLens(this, definitions, lens).build();
  }

  public static class Builder {

    private final boolean trackAllocationSites;

    /** Instantiated classes with the contexts of the instantiations. */
    private final Map<DexProgramClass, Set<DexEncodedMethod>> classesWithAllocationSiteTracking =
        new IdentityHashMap<>();

    /** Instantiated classes without contexts. */
    private final Set<DexProgramClass> classesWithoutAllocationSiteTracking =
        Sets.newIdentityHashSet();

    /** Set of types directly implemented by a lambda. */
    private final Map<DexType, List<LambdaDescriptor>> instantiatedLambdas =
        new IdentityHashMap<>();

    /**
     * Hierarchy for instantiated types mapping a type to the set of immediate subtypes for which
     * some subtype is either instantiated or is implemented by an instantiated lambda.
     */
    private final Map<DexType, Set<DexClass>> instantiatedHierarchy = new IdentityHashMap<>();

    /**
     * Set of interface types for which there may be instantiations, such as lambda expressions or
     * explicit keep rules.
     */
    private final Set<DexProgramClass> instantiatedInterfaceTypes = Sets.newIdentityHashSet();

    /** Subset of the above that are marked instantiated by usages that are not lambdas. */
    public final Set<DexProgramClass> unknownInstantiatedInterfaceTypes = Sets.newIdentityHashSet();

    private GraphReporter reporter;

    private Builder(boolean trackAllocationSites, GraphReporter reporter) {
      this.trackAllocationSites = trackAllocationSites;
      this.reporter = reporter;
    }

    private boolean shouldTrackAllocationSitesForClass(
        DexProgramClass clazz, InstantiationReason instantiationReason) {
      if (!trackAllocationSites) {
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
      // We currently only use allocation site information for instance field value propagation.
      return !clazz.instanceFields().isEmpty();
    }

    public boolean isInstantiatedDirectlyOrIsInstantiationLeaf(DexProgramClass clazz) {
      if (clazz.isInterface()) {
        return instantiatedInterfaceTypes.contains(clazz);
      }
      return isInstantiatedDirectly(clazz);
    }

    public boolean isInstantiatedDirectly(DexProgramClass clazz) {
      assert !clazz.isInterface();
      if (classesWithAllocationSiteTracking.containsKey(clazz)) {
        assert !classesWithAllocationSiteTracking.get(clazz).isEmpty();
        return true;
      }
      return classesWithoutAllocationSiteTracking.contains(clazz);
    }

    public boolean isInstantiatedDirectlyOrHasInstantiatedSubtype(DexProgramClass clazz) {
      return isInstantiatedDirectlyOrIsInstantiationLeaf(clazz)
          || instantiatedHierarchy.containsKey(clazz.type);
    }

    public void forEachInstantiatedSubType(
        DexType type,
        Consumer<DexProgramClass> onClass,
        Consumer<LambdaDescriptor> onLambda,
        AppInfo appInfo) {
      internalForEachInstantiatedSubType(
          type,
          onClass,
          onLambda,
          instantiatedHierarchy,
          instantiatedLambdas,
          this::isInstantiatedDirectlyOrIsInstantiationLeaf,
          appInfo);
    }

    public Set<DexClass> getImmediateSubtypesInInstantiatedHierarchy(DexType type) {
      return instantiatedHierarchy.get(type);
    }

    /**
     * Records that {@param clazz} is instantiated in {@param context}.
     *
     * @return true if {@param clazz} was not instantiated before.
     */
    public boolean recordDirectAllocationSite(
        DexProgramClass clazz,
        DexEncodedMethod context,
        InstantiationReason instantiationReason,
        KeepReason keepReason,
        AppInfo appInfo) {
      assert !clazz.isInterface();
      if (reporter != null) {
        reporter.registerClass(clazz, keepReason);
      }
      populateInstantiatedHierarchy(appInfo, clazz);
      if (shouldTrackAllocationSitesForClass(clazz, instantiationReason)) {
        assert context != null;
        Set<DexEncodedMethod> allocationSitesForClass =
            classesWithAllocationSiteTracking.computeIfAbsent(
                clazz, ignore -> Sets.newIdentityHashSet());
        allocationSitesForClass.add(context);
        return allocationSitesForClass.size() == 1;
      }
      if (classesWithoutAllocationSiteTracking.add(clazz)) {
        Set<DexEncodedMethod> allocationSitesForClass =
            classesWithAllocationSiteTracking.remove(clazz);
        return allocationSitesForClass == null;
      }
      return false;
    }

    public boolean recordInstantiatedInterface(DexProgramClass iface) {
      assert iface.isInterface();
      assert !iface.isAnnotation();
      unknownInstantiatedInterfaceTypes.add(iface);
      return instantiatedInterfaceTypes.add(iface);
    }

    public void recordInstantiatedLambdaInterface(
        DexType iface, LambdaDescriptor lambda, AppInfo appInfo) {
      instantiatedLambdas.computeIfAbsent(iface, key -> new ArrayList<>()).add(lambda);
      populateInstantiatedHierarchy(appInfo, iface);
    }

    private void populateInstantiatedHierarchy(AppInfo appInfo, DexType type) {
      DexClass clazz = appInfo.definitionFor(type);
      if (clazz != null) {
        populateInstantiatedHierarchy(appInfo, clazz);
      }
    }

    private void populateInstantiatedHierarchy(AppInfo appInfo, DexClass clazz) {
      if (clazz.superType != null) {
        populateInstantiatedHierarchy(appInfo, clazz.superType, clazz);
      }
      for (DexType iface : clazz.interfaces.values) {
        populateInstantiatedHierarchy(appInfo, iface, clazz);
      }
    }

    private void populateInstantiatedHierarchy(AppInfo appInfo, DexType type, DexClass subtype) {
      if (type == appInfo.dexItemFactory().objectType) {
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
      populateInstantiatedHierarchy(appInfo, type);
    }

    Builder rewrittenWithLens(
        ObjectAllocationInfoCollectionImpl objectAllocationInfos,
        DexDefinitionSupplier definitions,
        GraphLense lens) {
      objectAllocationInfos.classesWithAllocationSiteTracking.forEach(
          (clazz, allocationSitesForClass) -> {
            DexType type = lens.lookupType(clazz.type);
            if (type.isPrimitiveType()) {
              return;
            }
            DexProgramClass rewrittenClass = asProgramClassOrNull(definitions.definitionFor(type));
            assert rewrittenClass != null;
            assert !classesWithAllocationSiteTracking.containsKey(rewrittenClass);
            classesWithAllocationSiteTracking.put(
                rewrittenClass,
                LensUtils.rewrittenWithRenamedSignature(
                    allocationSitesForClass, definitions, lens));
          });
      objectAllocationInfos.classesWithoutAllocationSiteTracking.forEach(
          clazz -> {
            DexType type = lens.lookupType(clazz.type);
            if (type.isPrimitiveType()) {
              return;
            }
            DexProgramClass rewrittenClass = asProgramClassOrNull(definitions.definitionFor(type));
            assert rewrittenClass != null;
            assert !classesWithAllocationSiteTracking.containsKey(rewrittenClass);
            assert !classesWithoutAllocationSiteTracking.contains(rewrittenClass);
            classesWithoutAllocationSiteTracking.add(rewrittenClass);
          });
      return this;
    }

    public ObjectAllocationInfoCollectionImpl build() {
      return new ObjectAllocationInfoCollectionImpl(
          classesWithAllocationSiteTracking, classesWithoutAllocationSiteTracking);
    }
  }

  private static void internalForEachInstantiatedSubType(
      DexType type,
      Consumer<DexProgramClass> subTypeConsumer,
      Consumer<LambdaDescriptor> lambdaConsumer,
      Map<DexType, Set<DexClass>> instantiatedHierarchy,
      Map<DexType, List<LambdaDescriptor>> instantiatedLambdas,
      Predicate<DexProgramClass> isInstantiatedDirectly,
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
        instantiatedLambdas.getOrDefault(type, Collections.emptyList()).forEach(lambdaConsumer);
      } else {
        worklist.addIfNotSeen(initialClass);
      }
    }

    while (worklist.hasNext()) {
      DexClass clazz = worklist.next();
      if (clazz.isProgramClass()) {
        DexProgramClass programClass = clazz.asProgramClass();
        if (isInstantiatedDirectly.test(programClass)) {
          subTypeConsumer.accept(programClass);
        }
      }
      worklist.addIfNotSeen(instantiatedHierarchy.getOrDefault(clazz.type, Collections.emptySet()));
      instantiatedLambdas.getOrDefault(clazz.type, Collections.emptyList()).forEach(lambdaConsumer);
    }
  }
}
