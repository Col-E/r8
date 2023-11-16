// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.MutableBidirectionalManyToOneRepresentativeMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

class CollisionDetector {

  private static final int NOT_FOUND = Integer.MIN_VALUE;

  private final DexItemFactory dexItemFactory;
  private final Collection<DexMethod> invokes;
  private final MutableBidirectionalManyToOneRepresentativeMap<DexType, DexType> mergedClasses;

  private final DexType source;
  private final Reference2IntMap<DexProto> sourceProtoCache;

  private final DexType target;
  private final Reference2IntMap<DexProto> targetProtoCache;

  private final Map<DexString, Int2IntMap> seenPositions = new IdentityHashMap<>();

  CollisionDetector(
      AppView<AppInfoWithLiveness> appView,
      Collection<DexMethod> invokes,
      MutableBidirectionalManyToOneRepresentativeMap<DexType, DexType> mergedClasses,
      DexType source,
      DexType target) {
    this.dexItemFactory = appView.dexItemFactory();
    this.invokes = invokes;
    this.mergedClasses = mergedClasses;
    this.source = source;
    this.sourceProtoCache = new Reference2IntOpenHashMap<>(invokes.size() / 2);
    this.sourceProtoCache.defaultReturnValue(NOT_FOUND);
    this.target = target;
    this.targetProtoCache = new Reference2IntOpenHashMap<>(invokes.size() / 2);
    this.targetProtoCache.defaultReturnValue(NOT_FOUND);
  }

  boolean mayCollide(Timing timing) {
    timing.begin("collision detection");
    fillSeenPositions();
    boolean result = false;
    // If the type is not used in methods at all, there cannot be any conflict.
    if (!seenPositions.isEmpty()) {
      for (DexMethod method : invokes) {
        Int2IntMap positionsMap = seenPositions.get(method.getName());
        if (positionsMap != null) {
          int arity = method.getArity();
          int previous = positionsMap.get(arity);
          if (previous != NOT_FOUND) {
            assert previous != 0;
            int positions = computePositionsFor(method.getProto(), source, sourceProtoCache);
            if ((positions & previous) != 0) {
              result = true;
              break;
            }
          }
        }
      }
    }
    timing.end();
    return result;
  }

  private void fillSeenPositions() {
    for (DexMethod method : invokes) {
      int arity = method.getArity();
      int positions = computePositionsFor(method.getProto(), target, targetProtoCache);
      if (positions != 0) {
        Int2IntMap positionsMap =
            seenPositions.computeIfAbsent(
                method.getName(),
                k -> {
                  Int2IntMap result = new Int2IntOpenHashMap();
                  result.defaultReturnValue(NOT_FOUND);
                  return result;
                });
        int value = 0;
        int previous = positionsMap.get(arity);
        if (previous != NOT_FOUND) {
          value = previous;
        }
        value |= positions;
        positionsMap.put(arity, value);
      }
    }
  }

  // Given a method signature and a type, this method computes a bit vector that denotes the
  // positions at which the given type is used in the method signature.
  private int computePositionsFor(DexProto proto, DexType type, Reference2IntMap<DexProto> cache) {
    int result = cache.getInt(proto);
    if (result != NOT_FOUND) {
      return result;
    }
    result = 0;
    int bitsUsed = 0;
    int accumulator = 0;
    for (DexType parameterBaseType : proto.getParameterBaseTypes(dexItemFactory)) {
      // Substitute the type with the already merged class to estimate what it will look like.
      DexType mappedType = mergedClasses.getOrDefault(parameterBaseType, parameterBaseType);
      accumulator <<= 1;
      bitsUsed++;
      if (mappedType.isIdenticalTo(type)) {
        accumulator |= 1;
      }
      // Handle overflow on 31 bit boundary.
      if (bitsUsed == Integer.SIZE - 1) {
        result |= accumulator;
        accumulator = 0;
        bitsUsed = 0;
      }
    }
    // We also take the return type into account for potential conflicts.
    DexType returnBaseType = proto.getReturnType().toBaseType(dexItemFactory);
    DexType mappedReturnType = mergedClasses.getOrDefault(returnBaseType, returnBaseType);
    accumulator <<= 1;
    if (mappedReturnType.isIdenticalTo(type)) {
      accumulator |= 1;
    }
    result |= accumulator;
    cache.put(proto, result);
    return result;
  }
}
