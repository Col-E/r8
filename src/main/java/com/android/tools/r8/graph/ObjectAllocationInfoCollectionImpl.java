// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.shaking.GraphReporter;
import com.android.tools.r8.shaking.InstantiationReason;
import com.android.tools.r8.shaking.KeepReason;
import com.android.tools.r8.utils.LensUtils;
import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

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

    private final Map<DexProgramClass, Set<DexEncodedMethod>> classesWithAllocationSiteTracking =
        new IdentityHashMap<>();
    private final Set<DexProgramClass> classesWithoutAllocationSiteTracking =
        Sets.newIdentityHashSet();

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

    public boolean isInstantiatedDirectly(DexProgramClass clazz) {
      if (classesWithAllocationSiteTracking.containsKey(clazz)) {
        assert !classesWithAllocationSiteTracking.get(clazz).isEmpty();
        return true;
      }
      return classesWithoutAllocationSiteTracking.contains(clazz);
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
        KeepReason keepReason) {
      assert !clazz.isInterface();
      if (reporter != null) {
        reporter.registerClass(clazz, keepReason);
      }
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
}
