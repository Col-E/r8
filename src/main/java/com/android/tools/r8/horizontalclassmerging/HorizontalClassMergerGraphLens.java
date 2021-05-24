// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.NestedGraphLens;
import com.android.tools.r8.ir.conversion.ExtraParameter;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneHashMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeHashMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeMap;
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
      BidirectionalManyToOneRepresentativeMap<DexMethod, DexMethod> newMethodSignatures) {
    super(appView, fieldMap, methodMap, mergedClasses.getForwardMap(), newMethodSignatures);
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

  @Override
  protected FieldLookupResult internalDescribeLookupField(FieldLookupResult previous) {
    FieldLookupResult lookup = super.internalDescribeLookupField(previous);
    if (lookup.getReference() == previous.getReference()) {
      return lookup;
    }
    return FieldLookupResult.builder(this)
        .setReference(lookup.getReference())
        .setReboundReference(lookup.getReboundReference())
        .setCastType(
            lookup.getReference().getType() != previous.getReference().getType()
                ? lookupType(previous.getReference().getType())
                : null)
        .build();
  }

  public static class Builder {

    private final MutableBidirectionalManyToOneRepresentativeMap<DexField, DexField> fieldMap =
        BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();
    private final BidirectionalManyToOneHashMap<DexMethod, DexMethod> methodMap =
        BidirectionalManyToOneHashMap.newIdentityHashMap();
    private final BidirectionalManyToOneRepresentativeHashMap<DexMethod, DexMethod>
        newMethodSignatures = BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();
    private final Map<DexMethod, List<ExtraParameter>> methodExtraParameters =
        new IdentityHashMap<>();

    private final BidirectionalManyToOneHashMap<DexMethod, DexMethod> pendingMethodMapUpdates =
        BidirectionalManyToOneHashMap.newIdentityHashMap();
    private final BidirectionalManyToOneRepresentativeHashMap<DexMethod, DexMethod>
        pendingNewMethodSignatureUpdates =
            BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();

    Builder() {}

    HorizontalClassMergerGraphLens build(
        AppView<?> appView, HorizontallyMergedClasses mergedClasses) {
      assert pendingMethodMapUpdates.isEmpty();
      assert pendingNewMethodSignatureUpdates.isEmpty();
      return new HorizontalClassMergerGraphLens(
          appView,
          mergedClasses,
          methodExtraParameters,
          fieldMap,
          methodMap.getForwardMap(),
          newMethodSignatures);
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
      DexField representative = fieldMap.removeRepresentativeFor(oldFieldSignature);
      Set<DexField> originalFieldSignatures = fieldMap.removeValue(oldFieldSignature);
      if (originalFieldSignatures.isEmpty()) {
        fieldMap.put(oldFieldSignature, newFieldSignature);
      } else if (originalFieldSignatures.size() == 1) {
        fieldMap.put(originalFieldSignatures, newFieldSignature);
      } else {
        assert representative != null;
        fieldMap.put(originalFieldSignatures, newFieldSignature);
        fieldMap.setRepresentative(newFieldSignature, representative);
      }
    }

    void mapMethod(DexMethod oldMethodSignature, DexMethod newMethodSignature) {
      methodMap.put(oldMethodSignature, newMethodSignature);
    }

    void moveMethod(DexMethod from, DexMethod to) {
      moveMethod(from, to, false);
    }

    void moveMethod(DexMethod from, DexMethod to, boolean isRepresentative) {
      mapMethod(from, to);
      recordNewMethodSignature(from, to, isRepresentative);
    }

    void recordNewMethodSignature(DexMethod oldMethodSignature, DexMethod newMethodSignature) {
      recordNewMethodSignature(oldMethodSignature, newMethodSignature, false);
    }

    void recordNewMethodSignature(
        DexMethod oldMethodSignature, DexMethod newMethodSignature, boolean isRepresentative) {
      newMethodSignatures.put(oldMethodSignature, newMethodSignature);
      if (isRepresentative) {
        newMethodSignatures.setRepresentative(newMethodSignature, oldMethodSignature);
      }
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
      Set<DexMethod> oldMethodSignatures = newMethodSignatures.getKeys(oldMethodSignature);
      if (oldMethodSignatures.isEmpty()) {
        pendingNewMethodSignatureUpdates.put(oldMethodSignature, newMethodSignature);
      } else {
        for (DexMethod originalMethodSignature : oldMethodSignatures) {
          pendingNewMethodSignatureUpdates.put(originalMethodSignature, newMethodSignature);
        }
      }
    }

    void commitPendingUpdates() {
      // Commit pending method map updates.
      methodMap.removeAll(pendingMethodMapUpdates.keySet());
      pendingMethodMapUpdates.forEachManyToOneMapping(methodMap::put);
      pendingMethodMapUpdates.clear();

      // Commit pending original method signatures updates.
      newMethodSignatures.removeAll(pendingNewMethodSignatureUpdates.keySet());
      pendingNewMethodSignatureUpdates.forEachManyToOneMapping(newMethodSignatures::put);
      pendingNewMethodSignatureUpdates.clear();
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
