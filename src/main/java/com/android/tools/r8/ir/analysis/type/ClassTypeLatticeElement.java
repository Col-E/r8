// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexType;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

public class ClassTypeLatticeElement extends ReferenceTypeLatticeElement {

  private Set<DexType> lazyInterfaces;
  private DexDefinitionSupplier definitionsForLazyInterfacesComputation;

  public ClassTypeLatticeElement(
      DexType classType, Nullability nullability, Set<DexType> interfaces) {
    this(classType, nullability, interfaces, null);
  }

  public ClassTypeLatticeElement(
      DexType classType, Nullability nullability, DexDefinitionSupplier definitions) {
    this(classType, nullability, null, definitions);
  }

  private ClassTypeLatticeElement(
      DexType classType,
      Nullability nullability,
      Set<DexType> interfaces,
      DexDefinitionSupplier definitions) {
    super(nullability, classType);
    assert classType.isClassType();
    definitionsForLazyInterfacesComputation = definitions;
    lazyInterfaces = interfaces;
  }

  public DexType getClassType() {
    return type;
  }

  @Override
  public Set<DexType> getInterfaces() {
    if (lazyInterfaces != null) {
      return lazyInterfaces;
    }
    synchronized (this) {
      if (lazyInterfaces == null) {
        Set<DexType> itfs = type.implementedInterfaces(definitionsForLazyInterfacesComputation);
        lazyInterfaces =
            computeLeastUpperBoundOfInterfaces(definitionsForLazyInterfacesComputation, itfs, itfs);
        definitionsForLazyInterfacesComputation = null;
      }
    }
    return lazyInterfaces;
  }

  @Override
  ReferenceTypeLatticeElement createVariant(Nullability nullability) {
    if (this.nullability == nullability) {
      return this;
    }
    return new ClassTypeLatticeElement(
        type, nullability, lazyInterfaces, definitionsForLazyInterfacesComputation);
  }

  @Override
  public TypeLatticeElement asNullable() {
    return nullability.isNullable() ? this : getOrCreateVariant(maybeNull());
  }

  @Override
  public TypeLatticeElement asNonNullable() {
    return nullability.isDefinitelyNotNull() ? this : getOrCreateVariant(definitelyNotNull());
  }

  @Override
  public boolean isBasedOnMissingClass(DexDefinitionSupplier definitions) {
    return getClassType().isMissingOrHasMissingSuperType(definitions)
        || getInterfaces().stream()
            .anyMatch(type -> type.isMissingOrHasMissingSuperType(definitions));
  }

  @Override
  public boolean isClassType() {
    return true;
  }

  @Override
  public ClassTypeLatticeElement asClassTypeLatticeElement() {
    return this;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(super.toString());
    builder.append(" {");
    builder.append(
        getInterfaces().stream().map(DexType::toString).collect(Collectors.joining(", ")));
    builder.append("}");
    return builder.toString();
  }

  @Override
  public int hashCode() {
    // The interfaces of a type do not contribute to its hashCode as they are lazily computed.
    return (isNullable() ? 1 : -1) * type.hashCode();
  }

  ClassTypeLatticeElement join(ClassTypeLatticeElement other, DexDefinitionSupplier definitions) {
    DexType lubType =
        getClassType().computeLeastUpperBoundOfClasses(definitions, other.getClassType());
    Set<DexType> c1lubItfs = getInterfaces();
    Set<DexType> c2lubItfs = other.getInterfaces();
    Set<DexType> lubItfs = null;
    if (c1lubItfs.size() == c2lubItfs.size() && c1lubItfs.containsAll(c2lubItfs)) {
      lubItfs = c1lubItfs;
    }
    if (lubItfs == null) {
      lubItfs = computeLeastUpperBoundOfInterfaces(definitions, c1lubItfs, c2lubItfs);
    }
    Nullability nullability = nullability().join(other.nullability());
    return new ClassTypeLatticeElement(lubType, nullability, lubItfs);
  }

  private enum InterfaceMarker {
    LEFT,
    RIGHT
  }

  private static class InterfaceWithMarker {
    final DexType itf;
    final InterfaceMarker marker;

    InterfaceWithMarker(DexType itf, InterfaceMarker marker) {
      this.itf = itf;
      this.marker = marker;
    }
  }

  static Set<DexType> computeLeastUpperBoundOfInterfaces(
      DexDefinitionSupplier definitions, Set<DexType> s1, Set<DexType> s2) {
    Set<DexType> cached = definitions.dexItemFactory().leastUpperBoundOfInterfacesTable.get(s1, s2);
    if (cached != null) {
      return cached;
    }
    cached = definitions.dexItemFactory().leastUpperBoundOfInterfacesTable.get(s2, s1);
    if (cached != null) {
      return cached;
    }
    Map<DexType, Set<InterfaceMarker>> seen = new IdentityHashMap<>();
    Queue<InterfaceWithMarker> worklist = new ArrayDeque<>();
    for (DexType itf1 : s1) {
      worklist.add(new InterfaceWithMarker(itf1, InterfaceMarker.LEFT));
    }
    for (DexType itf2 : s2) {
      worklist.add(new InterfaceWithMarker(itf2, InterfaceMarker.RIGHT));
    }
    while (!worklist.isEmpty()) {
      InterfaceWithMarker item = worklist.poll();
      DexType itf = item.itf;
      InterfaceMarker marker = item.marker;
      Set<InterfaceMarker> markers = seen.computeIfAbsent(itf, k -> new HashSet<>());
      // If this interface is a lower one in this set, skip.
      if (markers.contains(marker)) {
        continue;
      }
      // If this interface is already visited by the other set, add marker for this set and skip.
      if (markers.size() == 1) {
        markers.add(marker);
        continue;
      }
      // Otherwise, this type is freshly visited.
      markers.add(marker);
      // Put super interfaces into the worklist.
      DexClass itfClass = definitions.definitionFor(itf);
      if (itfClass != null) {
        for (DexType superItf : itfClass.interfaces.values) {
          markers = seen.computeIfAbsent(superItf, k -> new HashSet<>());
          if (!markers.contains(marker)) {
            worklist.add(new InterfaceWithMarker(superItf, marker));
          }
        }
      }
    }

    ImmutableSet.Builder<DexType> commonBuilder = ImmutableSet.builder();
    for (Map.Entry<DexType, Set<InterfaceMarker>> entry : seen.entrySet()) {
      // Keep commonly visited interfaces only
      if (entry.getValue().size() < 2) {
        continue;
      }
      commonBuilder.add(entry.getKey());
    }
    Set<DexType> commonlyVisited = commonBuilder.build();

    ImmutableSet.Builder<DexType> lubBuilder = ImmutableSet.builder();
    for (DexType itf : commonlyVisited) {
      // If there is a strict sub interface of this interface, it is not the least element.
      boolean notTheLeast = false;
      for (DexType other : commonlyVisited) {
        if (other.isStrictSubtypeOf(itf, definitions)) {
          notTheLeast = true;
          break;
        }
      }
      if (notTheLeast) {
        continue;
      }
      lubBuilder.add(itf);
    }
    Set<DexType> lub = lubBuilder.build();
    // Cache the computation result only if the given two sets of interfaces are different.
    if (s1.size() != s2.size() || !s1.containsAll(s2)) {
      synchronized (definitions.dexItemFactory().leastUpperBoundOfInterfacesTable) {
        definitions.dexItemFactory().leastUpperBoundOfInterfacesTable.put(s1, s2, lub);
      }
    }
    return lub;
  }
}
