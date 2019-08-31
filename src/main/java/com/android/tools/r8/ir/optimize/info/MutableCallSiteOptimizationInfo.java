// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MutableCallSiteOptimizationInfo extends CallSiteOptimizationInfo {

  // inValues() size == DexMethod.arity + (isStatic ? 0 : 1) // receiver
  // That is, this information takes into account the receiver as well.
  private final int size;
  // Mappings from the calling context to argument collection. Note that, even in the same context,
  // the corresponding method can be invoked multiple times with different arguments, hence *set* of
  // argument collections.
  private final Map<DexEncodedMethod, Set<ArgumentCollection>> callSiteInfos =
      new ConcurrentHashMap<>();
  private ArgumentCollection cachedRepresentative = null;

  static class ArgumentCollection {

    // TODO(b/139246447): extend it to TypeLattice as well as constants/ranges.
    Nullability[] nullabilities;

    private static final ArgumentCollection BOTTOM = new ArgumentCollection() {
      @Override
      Nullability getNullability(int index) {
        return Nullability.maybeNull();
      }

      @Override
      public int hashCode() {
        return System.identityHashCode(this);
      }

      @Override
      public String toString() {
        return "(BOTTOM)";
      }
    };

    private ArgumentCollection() {}

    ArgumentCollection(int size) {
      this.nullabilities = new Nullability[size];
      for (int i = 0; i < size; i++) {
        this.nullabilities[i] = Nullability.maybeNull();
      }
    }

    Nullability getNullability(int index) {
      assert nullabilities != null;
      assert 0 <= index && index < nullabilities.length;
      return nullabilities[index];
    }

    ArgumentCollection copy() {
      ArgumentCollection copy = new ArgumentCollection();
      copy.nullabilities = new Nullability[this.nullabilities.length];
      System.arraycopy(this.nullabilities, 0, copy.nullabilities, 0, this.nullabilities.length);
      return copy;
    }

    ArgumentCollection join(ArgumentCollection other) {
      if (other == BOTTOM) {
        return this;
      }
      if (this == BOTTOM) {
        return other;
      }
      assert this.nullabilities.length == other.nullabilities.length;
      ArgumentCollection result = this.copy();
      for (int i = 0; i < result.nullabilities.length; i++) {
        result.nullabilities[i] = result.nullabilities[i].join(other.nullabilities[i]);
      }
      return result;
    }

    static ArgumentCollection join(Collection<ArgumentCollection> collections) {
      return collections.stream().reduce(BOTTOM, ArgumentCollection::join);
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof ArgumentCollection)) {
        return false;
      }
      ArgumentCollection otherCollection = (ArgumentCollection) other;
      if (this == BOTTOM || otherCollection == BOTTOM) {
        return this == BOTTOM && otherCollection == BOTTOM;
      }
      if (this.nullabilities.length != otherCollection.nullabilities.length) {
        return false;
      }
      for (int i = 0; i < this.nullabilities.length; i++) {
        if (!this.nullabilities[i].equals(otherCollection.nullabilities[i])) {
          return false;
        }
      }
      return true;
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(nullabilities);
    }

    @Override
    public String toString() {
      return "(" + StringUtils.join(Arrays.asList(nullabilities), ", ") + ")";
    }
  }

  public MutableCallSiteOptimizationInfo(DexEncodedMethod encodedMethod) {
    assert encodedMethod.method.getArity() > 0;
    this.size = encodedMethod.method.getArity() + (encodedMethod.isStatic() ? 0 : 1);
  }

  private void computeCachedRepresentativeIfNecessary() {
    if (cachedRepresentative == null && !callSiteInfos.isEmpty()) {
      synchronized (callSiteInfos) {
        // Make sure collected information is not flushed out by other threads.
        if (!callSiteInfos.isEmpty()) {
          cachedRepresentative =
              callSiteInfos.values().stream()
                  .reduce(
                      ArgumentCollection.BOTTOM,
                      (prev, collections) -> prev.join(ArgumentCollection.join(collections)),
                      ArgumentCollection::join);
          // After creating a cached representative, flush out the collected information.
          callSiteInfos.clear();
        } else {
          // If collected information is gone while waiting for the lock, make sure it's used to
          // compute the cached representative.
          assert cachedRepresentative != null;
        }
      }
    }
  }

  @Override
  public boolean hasUsefulOptimizationInfo() {
    computeCachedRepresentativeIfNecessary();
    for (int i = 0; i < size; i++) {
      Nullability nullability = getNullability(i);
      if (nullability.isDefinitelyNull() || nullability.isDefinitelyNotNull()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Nullability getNullability(int argIndex) {
    assert 0 <= argIndex && argIndex < size;
    if (cachedRepresentative == null) {
      return Nullability.maybeNull();
    }
    return cachedRepresentative.getNullability(argIndex);
  }

  public static boolean hasArgumentsToRecord(List<Value> inValues) {
    // TODO(b/69963623): allow primitive types with compile-time constants.
    for (Value v : inValues) {
      if (v.getTypeLattice().isReference()) {
        return true;
      }
    }
    return false;
  }

  public void recordArguments(DexEncodedMethod callingContext, List<Value> inValues) {
    assert cachedRepresentative == null;
    assert size == inValues.size();
    Set<ArgumentCollection> collections =
        callSiteInfos.computeIfAbsent(callingContext, ignore -> new HashSet<>());
    ArgumentCollection newCallSiteInfo = new ArgumentCollection(size);
    for (int i = 0; i < size; i++) {
      TypeLatticeElement typeLatticeElement = inValues.get(i).getTypeLattice();
      if (typeLatticeElement.isReference()) {
        newCallSiteInfo.nullabilities[i] = typeLatticeElement.nullability();
      }
    }
    collections.add(newCallSiteInfo);
  }

  @Override
  public boolean isMutableCallSiteOptimizationInfo() {
    return true;
  }

  @Override
  public MutableCallSiteOptimizationInfo asMutableCallSiteOptimizationInfo() {
    return this;
  }

  @Override
  public String toString() {
    if (cachedRepresentative != null) {
      return cachedRepresentative.toString();
    }
    StringBuilder builder = new StringBuilder();
    for (Map.Entry<DexEncodedMethod, Set<ArgumentCollection>> entry : callSiteInfos.entrySet()) {
      builder.append(entry.getKey().toSourceString());
      builder.append(" -> {");
      StringUtils.join(entry.getValue(), ", ");
      builder.append("}");
      builder.append(System.lineSeparator());
    }
    return builder.toString();
  }
}
