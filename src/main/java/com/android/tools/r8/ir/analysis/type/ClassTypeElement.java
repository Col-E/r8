// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ClassTypeElement extends ReferenceTypeElement {

  // Least upper bound of interfaces that this class type is implementing.
  // Lazily computed on demand via DexItemFactory, where the canonicalized set will be maintained.
  private Set<DexType> lazyInterfaces;
  private AppView<? extends AppInfoWithClassHierarchy> appView;
  // On-demand link between other nullability-variants.
  private final NullabilityVariants<ClassTypeElement> variants;
  private final DexType type;

  public static ClassTypeElement create(
      DexType classType, Nullability nullability, Set<DexType> interfaces) {
    assert interfaces != null;
    return NullabilityVariants.create(
        nullability,
        (variants) -> new ClassTypeElement(classType, nullability, interfaces, variants, null));
  }

  public static ClassTypeElement create(
      DexType classType,
      Nullability nullability,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    assert appView != null;
    return NullabilityVariants.create(
        nullability,
        (variants) -> new ClassTypeElement(classType, nullability, null, variants, appView));
  }

  private ClassTypeElement(
      DexType classType,
      Nullability nullability,
      Set<DexType> interfaces,
      NullabilityVariants<ClassTypeElement> variants,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    super(nullability);
    assert classType.isClassType();
    assert interfaces != null || appView != null;
    type = classType;
    this.appView = appView;
    lazyInterfaces = interfaces;
    this.variants = variants;
  }

  public DexType getClassType() {
    return type;
  }

  public Set<DexType> getInterfaces() {
    if (lazyInterfaces == null) {
      assert appView != null;
      lazyInterfaces =
          appView.dexItemFactory()
              .getOrComputeLeastUpperBoundOfImplementedInterfaces(type, appView);
    }
    assert lazyInterfaces != null;
    return lazyInterfaces;
  }

  private ClassTypeElement createVariant(
      Nullability nullability, NullabilityVariants<ClassTypeElement> variants) {
    assert this.nullability != nullability;
    return new ClassTypeElement(type, nullability, lazyInterfaces, variants, appView);
  }

  public boolean isRelatedTo(ClassTypeElement other, AppView<?> appView) {
    return lessThanOrEqualUpToNullability(other, appView)
        || other.lessThanOrEqualUpToNullability(this, appView);
  }

  @Override
  public ClassTypeElement getOrCreateVariant(Nullability nullability) {
    ClassTypeElement variant = variants.get(nullability);
    if (variant != null) {
      return variant;
    }
    return variants.getOrCreateElement(nullability, this::createVariant);
  }

  @Override
  public boolean isBasedOnMissingClass(AppView<? extends AppInfoWithClassHierarchy> appView) {
    return appView.appInfo().isMissingOrHasMissingSuperType(getClassType())
        || getInterfaces().stream()
            .anyMatch(type -> appView.appInfo().isMissingOrHasMissingSuperType(type));
  }

  @Override
  public boolean isClassType() {
    return true;
  }

  @Override
  public ClassTypeElement asClassType() {
    return this;
  }

  @Override
  public ClassTypeElement asMeetWithNotNull() {
    return getOrCreateVariant(nullability.meet(Nullability.definitelyNotNull()));
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(nullability);
    builder.append(" ");
    builder.append(type);
    builder.append(" {");
    Set<DexType> interfaces = getInterfaces();
    if (interfaces != null) {
      List<DexType> sortedInterfaces = new ArrayList<>(interfaces);
      sortedInterfaces.sort(DexType::slowCompareTo);
      builder.append(
          sortedInterfaces.stream().map(DexType::toString).collect(Collectors.joining(", ")));
    }
    builder.append("}");
    return builder.toString();
  }

  @Override
  public int hashCode() {
    // The interfaces of a type do not contribute to its hashCode as they are lazily computed.
    return Objects.hash(nullability, type);
  }

  @Override
  public TypeElement fixupClassTypeReferences(
      Function<DexType, DexType> mapping, AppView<? extends AppInfoWithClassHierarchy> appView) {
    DexType mappedType = mapping.apply(type);
    if (mappedType.isPrimitiveType()) {
      return PrimitiveTypeElement.fromDexType(mappedType, false);
    }
    if (mappedType != type) {
      return create(mappedType, nullability, appView);
    }
    // If the mapped type is not object and no computation of interfaces, we can return early.
    if (mappedType != appView.dexItemFactory().objectType && lazyInterfaces == null) {
      return this;
    }

    // For most types there will not have been a change thus we iterate without allocating a new
    // set for holding modified interfaces.
    boolean hasChangedInterfaces = false;
    DexClass interfaceToClassChange = null;
    for (DexType iface : getInterfaces()) {
      DexType substitutedType = mapping.apply(iface);
      if (iface != substitutedType) {
        hasChangedInterfaces = true;
        DexClass mappedClass = appView.definitionFor(substitutedType);
        if (!mappedClass.isInterface()) {
          if (interfaceToClassChange != null && mappedClass != interfaceToClassChange) {
            throw new CompilationError(
                "More than one interface has changed to a class: "
                    + interfaceToClassChange
                    + " and "
                    + mappedClass);
          }
          interfaceToClassChange = mappedClass;
        }
      }
    }
    if (hasChangedInterfaces) {
      if (interfaceToClassChange != null) {
        assert !interfaceToClassChange.isInterface();
        assert type == appView.dexItemFactory().objectType;
        return create(interfaceToClassChange.type, nullability, appView);
      } else {
        Set<DexType> newInterfaces = new HashSet<>();
        for (DexType iface : lazyInterfaces) {
          newInterfaces.add(mapping.apply(iface));
        }
        return create(mappedType, nullability, newInterfaces);
      }
    }
    return this;
  }

  ClassTypeElement join(ClassTypeElement other, AppView<?> appView) {
    Nullability nullability = nullability().join(other.nullability());
    if (!appView.enableWholeProgramOptimizations()) {
      assert lazyInterfaces != null && lazyInterfaces.isEmpty();
      assert other.lazyInterfaces != null && other.lazyInterfaces.isEmpty();
      return ClassTypeElement.create(
          getClassType() == other.getClassType()
              ? getClassType()
              : appView.dexItemFactory().objectType,
          nullability,
          Collections.emptySet());
    }
    DexType lubType =
        computeLeastUpperBoundOfClasses(
            appView.appInfo().withClassHierarchy(), getClassType(), other.getClassType());
    Set<DexType> c1lubItfs = getInterfaces();
    Set<DexType> c2lubItfs = other.getInterfaces();
    Set<DexType> lubItfs = null;
    if (c1lubItfs.size() == c2lubItfs.size() && c1lubItfs.containsAll(c2lubItfs)) {
      lubItfs = c1lubItfs;
    }
    if (lubItfs == null) {
      lubItfs =
          computeLeastUpperBoundOfInterfaces(appView.withClassHierarchy(), c1lubItfs, c2lubItfs);
    }
    return ClassTypeElement.create(lubType, nullability, lubItfs);
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

  public static DexType computeLeastUpperBoundOfClasses(
      AppInfoWithClassHierarchy appInfo, DexType type1, DexType type2) {
    // Compiling R8 with R8, this hits more than 1/3 of cases.
    if (type1 == type2) {
      return type1;
    }
    // Compiling R8 with R8, this hits more than 1/3 of cases.
    DexType objectType = appInfo.dexItemFactory().objectType;
    if (type1 == objectType || type2 == objectType) {
      return objectType;
    }
    // Compiling R8 with R8, there are no hierarchies above height 10.
    // The overhead of a hash map likely outweighs the speed of scanning an array.
    Collection<DexType> types = new ArrayList<>(10);
    DexType type = type1;
    while (true) {
      if (type == type2) {
        return type;
      }
      types.add(type);
      DexClass clazz = appInfo.definitionFor(type);
      if (clazz == null || clazz.superType == null || clazz.superType == objectType) {
        break;
      }
      type = clazz.superType;
    }
    // In pathological cases, realloc to a set if large.
    if (types.size() > 20) {
      types = SetUtils.newIdentityHashSet(types);
    }
    type = type2;
    while (true) {
      if (types.contains(type)) {
        return type;
      }
      DexClass clazz = appInfo.definitionFor(type);
      if (clazz == null || clazz.superType == null || clazz.superType == objectType) {
        break;
      }
      type = clazz.superType;
    }
    return objectType;
  }

  public static Set<DexType> computeLeastUpperBoundOfInterfaces(
      AppView<? extends AppInfoWithClassHierarchy> appView, Set<DexType> s1, Set<DexType> s2) {
    if (s1.isEmpty() || s2.isEmpty()) {
      return Collections.emptySet();
    }
    Set<DexType> cached = appView.dexItemFactory().leastUpperBoundOfInterfacesTable.get(s1, s2);
    if (cached != null) {
      return cached;
    }
    cached = appView.dexItemFactory().leastUpperBoundOfInterfacesTable.get(s2, s1);
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
      DexClass itfClass = appView.definitionFor(itf);
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
        if (appView.appInfo().isStrictSubtypeOf(other, itf)) {
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
      synchronized (appView.dexItemFactory().leastUpperBoundOfInterfacesTable) {
        appView.dexItemFactory().leastUpperBoundOfInterfacesTable.put(s1, s2, lub);
      }
    }
    return lub;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ClassTypeElement)) {
      return false;
    }
    ClassTypeElement other = (ClassTypeElement) o;
    if (nullability() != other.nullability()) {
      return false;
    }
    if (!type.equals(other.type)) {
      return false;
    }
    Set<DexType> thisInterfaces = getInterfaces();
    Set<DexType> otherInterfaces = other.getInterfaces();
    if (thisInterfaces == otherInterfaces) {
      return true;
    }
    if (thisInterfaces.size() != otherInterfaces.size()) {
      return false;
    }
    return thisInterfaces.containsAll(otherInterfaces);
  }
}
