// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens.NestedGraphLens;
import com.android.tools.r8.ir.conversion.ExtraParameter;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneHashMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeHashMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToManyRepresentativeHashMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToManyRepresentativeMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalManyToOneRepresentativeMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HorizontalClassMergerGraphLens extends NestedGraphLens {

  private final Map<DexMethod, List<ExtraParameter>> methodExtraParameters;
  private final HorizontallyMergedClasses mergedClasses;

  private HorizontalClassMergerGraphLens(
      AppView<?> appView,
      HorizontallyMergedClasses mergedClasses,
      Map<DexMethod, List<ExtraParameter>> methodExtraParameters,
      BidirectionalManyToOneRepresentativeMap<DexField, DexField> fieldMap,
      Map<DexMethod, DexMethod> methodMap,
      BidirectionalOneToManyRepresentativeMap<DexMethod, DexMethod> originalMethodSignatures) {
    super(
        mergedClasses.getForwardMap(),
        methodMap,
        fieldMap,
        originalMethodSignatures,
        appView.graphLens(),
        appView.dexItemFactory());
    this.methodExtraParameters = methodExtraParameters;
    this.mergedClasses = mergedClasses;
  }

  @Override
  protected Iterable<DexType> internalGetOriginalTypes(DexType previous) {
    return IterableUtils.prependSingleton(previous, mergedClasses.getSourcesFor(previous));
  }

  /**
   * If an overloaded constructor is requested, add the constructor id as a parameter to the
   * constructor. Otherwise return the lookup on the underlying graph lens.
   */
  @Override
  public MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context) {
    List<ExtraParameter> extraParameters = methodExtraParameters.get(previous.getReference());
    MethodLookupResult lookup = super.internalDescribeLookupMethod(previous, context);
    if (extraParameters == null) {
      return lookup;
    }
    return MethodLookupResult.builder(this)
        .setReference(lookup.getReference())
        .setPrototypeChanges(lookup.getPrototypeChanges().withExtraParameters(extraParameters))
        .setType(lookup.getType())
        .build();
  }

  public static class Builder {

    private final MutableBidirectionalManyToOneRepresentativeMap<DexField, DexField> fieldMap =
        new BidirectionalManyToOneRepresentativeHashMap<>();
    private final BidirectionalManyToOneHashMap<DexMethod, DexMethod> methodMap =
        new BidirectionalManyToOneHashMap<>();
    private final BidirectionalOneToManyRepresentativeHashMap<DexMethod, DexMethod>
        originalMethodSignatures = new BidirectionalOneToManyRepresentativeHashMap<>();
    private final Map<DexMethod, List<ExtraParameter>> methodExtraParameters =
        new IdentityHashMap<>();

    private final BidirectionalManyToOneHashMap<DexMethod, DexMethod> pendingMethodMapUpdates =
        new BidirectionalManyToOneHashMap<>();
    private final BidirectionalOneToManyRepresentativeHashMap<DexMethod, DexMethod>
        pendingOriginalMethodSignatureUpdates = new BidirectionalOneToManyRepresentativeHashMap<>();

    Builder() {}

    HorizontalClassMergerGraphLens build(
        AppView<?> appView, HorizontallyMergedClasses mergedClasses) {
      assert pendingMethodMapUpdates.isEmpty();
      assert pendingOriginalMethodSignatureUpdates.isEmpty();
      return new HorizontalClassMergerGraphLens(
          appView,
          mergedClasses,
          methodExtraParameters,
          fieldMap,
          methodMap.getForwardMap(),
          originalMethodSignatures);
    }

    void recordNewFieldSignature(DexField oldFieldSignature, DexField newFieldSignature) {
      fieldMap.put(oldFieldSignature, newFieldSignature);
    }

    void recordNewFieldSignature(
        Iterable<DexField> oldFieldSignatures,
        DexField newFieldSignature,
        DexField representative) {
      assert Streams.stream(oldFieldSignatures)
          .anyMatch(oldFieldSignature -> oldFieldSignature != newFieldSignature);
      assert Streams.stream(oldFieldSignatures).noneMatch(fieldMap::containsValue);
      assert Iterables.contains(oldFieldSignatures, representative);
      for (DexField oldFieldSignature : oldFieldSignatures) {
        recordNewFieldSignature(oldFieldSignature, newFieldSignature);
      }
      fieldMap.setRepresentative(newFieldSignature, representative);
    }

    void fixupField(DexField oldFieldSignature, DexField newFieldSignature) {
      Set<DexField> originalFieldSignatures = fieldMap.removeValue(oldFieldSignature);
      if (originalFieldSignatures.isEmpty()) {
        fieldMap.put(oldFieldSignature, newFieldSignature);
      } else if (originalFieldSignatures.size() == 1) {
        fieldMap.put(originalFieldSignatures.iterator().next(), newFieldSignature);
      } else {
        for (DexField originalFieldSignature : originalFieldSignatures) {
          fieldMap.put(originalFieldSignature, newFieldSignature);
        }
        DexField representative = fieldMap.removeRepresentativeFor(oldFieldSignature);
        assert representative != null;
        fieldMap.setRepresentative(newFieldSignature, representative);
      }
    }

    void mapMethod(DexMethod oldMethodSignature, DexMethod newMethodSignature) {
      methodMap.put(oldMethodSignature, newMethodSignature);
    }

    void moveMethod(DexMethod from, DexMethod to) {
      mapMethod(from, to);
      recordNewMethodSignature(from, to);
    }

    void recordNewMethodSignature(DexMethod oldMethodSignature, DexMethod newMethodSignature) {
      originalMethodSignatures.put(newMethodSignature, oldMethodSignature);
    }

    void fixupMethod(DexMethod oldMethodSignature, DexMethod newMethodSignature) {
      fixupMethodMap(oldMethodSignature, newMethodSignature);
      fixupOriginalMethodSignatures(oldMethodSignature, newMethodSignature);
    }

    private void fixupMethodMap(DexMethod oldMethodSignature, DexMethod newMethodSignature) {
      Set<DexMethod> originalMethodSignatures = methodMap.getKeys(oldMethodSignature);
      if (originalMethodSignatures.isEmpty()) {
        pendingMethodMapUpdates.put(oldMethodSignature, newMethodSignature);
      } else {
        for (DexMethod originalMethodSignature : originalMethodSignatures) {
          pendingMethodMapUpdates.put(originalMethodSignature, newMethodSignature);
        }
      }
    }

    private void fixupOriginalMethodSignatures(
        DexMethod oldMethodSignature, DexMethod newMethodSignature) {
      Set<DexMethod> oldMethodSignatures = originalMethodSignatures.getValues(oldMethodSignature);
      if (oldMethodSignatures.isEmpty()) {
        pendingOriginalMethodSignatureUpdates.put(newMethodSignature, oldMethodSignature);
      } else {
        for (DexMethod originalMethodSignature : oldMethodSignatures) {
          pendingOriginalMethodSignatureUpdates.put(newMethodSignature, originalMethodSignature);
        }
      }
    }

    void commitPendingUpdates() {
      // Commit pending method map updates.
      methodMap.removeAll(pendingMethodMapUpdates.keySet());
      pendingMethodMapUpdates.forEachManyToOneMapping(methodMap::put);
      pendingMethodMapUpdates.clear();

      // Commit pending original method signatures updates.
      originalMethodSignatures.removeAll(pendingOriginalMethodSignatureUpdates.keySet());
      pendingOriginalMethodSignatureUpdates.forEachOneToManyMapping(originalMethodSignatures::put);
      pendingOriginalMethodSignatureUpdates.clear();
    }

    /**
     * One way mapping from one constructor to another. This is used for synthesized constructors,
     * where many constructors are merged into a single constructor. The synthesized constructor
     * therefore does not have a unique reverse constructor.
     */
    void mapMergedConstructor(DexMethod from, DexMethod to, List<ExtraParameter> extraParameters) {
      mapMethod(from, to);
      if (extraParameters.size() > 0) {
        methodExtraParameters.put(from, extraParameters);
      }
    }

    void addExtraParameters(DexMethod methodSignature, List<ExtraParameter> extraParameters) {
      Set<DexMethod> originalMethodSignatures = methodMap.getKeys(methodSignature);
      if (originalMethodSignatures.isEmpty()) {
        methodExtraParameters
            .computeIfAbsent(methodSignature, ignore -> new ArrayList<>(extraParameters.size()))
            .addAll(extraParameters);
      } else {
        for (DexMethod originalMethodSignature : originalMethodSignatures) {
          methodExtraParameters
              .computeIfAbsent(
                  originalMethodSignature, ignore -> new ArrayList<>(extraParameters.size()))
              .addAll(extraParameters);
        }
      }
    }
  }
}
