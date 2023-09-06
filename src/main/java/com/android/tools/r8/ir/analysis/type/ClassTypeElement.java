// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.InterfaceCollection.Builder;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.SetUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
  private InterfaceCollection lazyInterfaces;
  private AppView<? extends AppInfoWithClassHierarchy> appView;
  // On-demand link between other nullability-variants.
  private final NullabilityVariants<ClassTypeElement> variants;
  private final DexType type;

  public static ClassTypeElement create(
      DexType classType,
      Nullability nullability,
      AppView<? extends AppInfoWithClassHierarchy> appView,
      InterfaceCollection interfaces) {
    assert appView != null;
    assert appView.enableWholeProgramOptimizations();
    assert interfaces != null;
    return NullabilityVariants.create(
        nullability,
        variants -> new ClassTypeElement(classType, nullability, interfaces, variants, appView));
  }

  public static ClassTypeElement create(
      DexType classType,
      Nullability nullability,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    assert appView != null;
    assert appView.enableWholeProgramOptimizations();
    return NullabilityVariants.create(
        nullability,
        variants -> new ClassTypeElement(classType, nullability, null, variants, appView));
  }

  public static ClassTypeElement createForD8(DexType classType, Nullability nullability) {
    return NullabilityVariants.create(
        nullability,
        variants ->
            new ClassTypeElement(
                classType, nullability, InterfaceCollection.empty(), variants, null));
  }

  private ClassTypeElement(
      DexType classType,
      Nullability nullability,
      InterfaceCollection interfaces,
      NullabilityVariants<ClassTypeElement> variants,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    super(nullability);
    assert appView != null
        ? appView.enableWholeProgramOptimizations()
        : (interfaces != null && interfaces.isEmpty());
    assert classType.isClassType();
    this.type = classType;
    this.appView = appView;
    this.lazyInterfaces = interfaces;
    this.variants = variants;
  }

  public DexType getClassType() {
    return type;
  }

  public InterfaceCollection getInterfaces() {
    if (lazyInterfaces == null) {
      // Class type interfaces are cached on DexItemFactory.
      assert appView != null;
      assert appView.enableWholeProgramOptimizations();
      return appView
          .dexItemFactory()
          .getOrComputeLeastUpperBoundOfImplementedInterfaces(type, appView);
    }
    return lazyInterfaces;
  }

  private ClassTypeElement createVariant(
      Nullability nullability, NullabilityVariants<ClassTypeElement> variants) {
    assert this.nullability != nullability;
    return new ClassTypeElement(type, nullability, lazyInterfaces, variants, appView);
  }

  @Override
  public ClassTypeElement getOrCreateVariant(Nullability nullability) {
    return nullability.equals(nullability())
        ? this
        : variants.getOrCreateElement(nullability, this::createVariant);
  }

  @Override
  public boolean isBasedOnMissingClass(AppView<? extends AppInfoWithClassHierarchy> appView) {
    return appView.appInfo().isMissingOrHasMissingSuperType(getClassType())
        || getInterfaces()
            .anyMatch((iface, isKnown) -> appView.appInfo().isMissingOrHasMissingSuperType(iface));
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
  public ClassTypeElement asDefinitelyNotNull() {
    return getOrCreateVariant(Nullability.definitelyNotNull());
  }

  @Override
  public ClassTypeElement asMeetWithNotNull() {
    return getOrCreateVariant(nullability.meet(Nullability.definitelyNotNull()));
  }

  @Override
  public ClassTypeElement joinNullability(Nullability nullability) {
    return getOrCreateVariant(nullability().join(nullability));
  }

  @Override
  public ClassTypeElement meetNullability(Nullability nullability) {
    return getOrCreateVariant(nullability().meet(nullability));
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public DexType toDexType(DexItemFactory dexItemFactory) {
    if (type == dexItemFactory.objectType) {
      DexType singleKnownInterface = getInterfaces().getSingleKnownInterface();
      if (singleKnownInterface != null) {
        return singleKnownInterface;
      }
    }
    return type;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(nullability);
    builder.append(" ");
    builder.append(type);
    builder.append(" {");
    InterfaceCollection interfaces = getInterfaces();
    List<Pair<DexType, Boolean>> sortedInterfaces = interfaces.getInterfaceList();
    sortedInterfaces.sort(Comparator.comparing(Pair::getFirst));
    builder.append(
        sortedInterfaces.stream()
            .map(
                pair ->
                    pair.getSecond()
                        ? pair.getFirst().toString()
                        : ("maybe(" + pair.getFirst() + ")"))
            .collect(Collectors.joining(", ")));
    builder.append("}");
    return builder.toString();
  }

  @Override
  public int hashCode() {
    // The interfaces of a type do not contribute to its hashCode as they are lazily computed.
    return Objects.hash(nullability, type);
  }

  @Override
  @SuppressWarnings({"BadImport", "ReferenceEquality"})
  public TypeElement fixupClassTypeReferences(
      AppView<? extends AppInfoWithClassHierarchy> ignore,
      Function<DexType, DexType> mapping,
      Set<DexType> prunedTypes) {
    assert appView != null;
    assert appView.enableWholeProgramOptimizations();

    DexType mappedType = mapping.apply(type);
    if (mappedType.isPrimitiveType()) {
      return PrimitiveTypeElement.fromDexType(mappedType, false);
    }

    // If there are no interfaces, then just return the mapped type element. Otherwise, the list of
    // interfaces require rewriting.
    if (lazyInterfaces == null || lazyInterfaces.isEmpty()) {
      return mappedType == type ? this : create(mappedType, nullability, appView);
    }

    // For most types there will not have been a change thus we iterate without allocating a new
    // set for holding modified interfaces.
    BooleanBox hasChangedInterfaces = new BooleanBox();
    Box<DexClass> interfaceToClassChange = new Box<>();
    getInterfaces()
        .forEach(
            (iface, isKnown) -> {
              if (prunedTypes.contains(iface)) {
                return;
              }
              DexType substitutedType = mapping.apply(iface);
              if (iface != substitutedType) {
                hasChangedInterfaces.set();
                DexClass mappedClass = appView.definitionFor(substitutedType);
                if (!mappedClass.isInterface()) {
                  if (interfaceToClassChange.isSet()
                      && mappedClass != interfaceToClassChange.get()) {
                    throw new CompilationError(
                        "More than one interface has changed to a class: "
                            + interfaceToClassChange.get()
                            + " and "
                            + mappedClass);
                  }
                  interfaceToClassChange.set(mappedClass);
                }
              }
            });
    if (hasChangedInterfaces.get()) {
      if (interfaceToClassChange.isSet()) {
        assert !interfaceToClassChange.get().isInterface();
        assert mappedType == appView.dexItemFactory().objectType;
        return create(interfaceToClassChange.get().type, nullability, appView);
      } else {
        Builder builder = InterfaceCollection.builder();
        lazyInterfaces.forEach(
            (iface, isKnown) -> {
              DexType rewritten = mapping.apply(iface);
              assert iface == rewritten || isKnown : "Rewritten implies program types thus known.";
              builder.addInterface(rewritten, isKnown);
            });
        return create(mappedType, nullability, appView, builder.build());
      }
    }
    return mappedType == type ? this : create(mappedType, nullability, appView, getInterfaces());
  }

  ClassTypeElement join(ClassTypeElement other, AppView<?> appView) {
    if (appView.enableWholeProgramOptimizations()) {
      return joinWithClassHierarchy(other);
    } else {
      return joinWithoutClassHierarchy(other.getClassType(), other.nullability(), appView);
    }
  }

  ReferenceTypeElement join(ArrayTypeElement other, AppView<?> appView) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    if (appView.enableWholeProgramOptimizations()) {
      DexType lubType = appView.dexItemFactory().objectType;
      return joinWithClassHierarchy(
          lubType,
          InterfaceCollection.builder()
              .addKnownInterface(dexItemFactory.cloneableType)
              .addKnownInterface(dexItemFactory.serializableType)
              .build(),
          other.nullability());
    } else {
      return joinWithoutClassHierarchy(dexItemFactory.objectType, other.nullability(), appView);
    }
  }

  @Override
  public ReferenceTypeElement join(ReferenceTypeElement other, AppView<?> appView) {
    if (other.isArrayType()) {
      return join(other.asArrayType(), appView);
    }
    if (other.isClassType()) {
      return join(other.asClassType(), appView);
    }
    assert other.isNullType();
    return joinNullability(other.nullability());
  }

  private ClassTypeElement joinWithClassHierarchy(ClassTypeElement other) {
    assert appView != null;
    assert appView.enableWholeProgramOptimizations();
    DexType lubType =
        computeLeastUpperBoundOfClasses(appView.appInfo(), getClassType(), other.getClassType());
    return joinWithClassHierarchy(lubType, other.getInterfaces(), other.nullability());
  }

  @SuppressWarnings("ReferenceEquality")
  private ClassTypeElement joinWithClassHierarchy(
      DexType lubType, InterfaceCollection otherInterfaces, Nullability nullability) {
    assert appView != null;
    assert appView.enableWholeProgramOptimizations();
    InterfaceCollection interfaces = getInterfaces();
    InterfaceCollection lubInterfaces =
        interfaces.equals(otherInterfaces)
            ? interfaces
            : computeLeastUpperBoundOfInterfaces(appView, interfaces, otherInterfaces);
    InterfaceCollection lubInterfacesDefault =
        appView
            .dexItemFactory()
            .getOrComputeLeastUpperBoundOfImplementedInterfaces(lubType, appView);
    Nullability lubNullability = nullability().join(nullability);

    // If the computed interfaces are identical to the interfaces of `lubType`, then do not include
    // the interfaces in the ClassTypeElement. This canonicalization of interfaces reduces memory,
    // but also reduces the amount of work involved in lens rewriting class type elements (the null
    // element does not require any rewriting).
    //
    // From a correctness point of view, both solutions should work.
    return lubInterfaces.equals(lubInterfacesDefault)
        ? create(lubType, lubNullability, appView)
        : create(lubType, lubNullability, appView, lubInterfaces);
  }

  @SuppressWarnings("ReferenceEquality")
  ClassTypeElement joinWithoutClassHierarchy(
      DexType other, Nullability nullability, AppView<?> appView) {
    assert !appView.enableWholeProgramOptimizations();
    assert lazyInterfaces != null;
    assert lazyInterfaces.isEmpty();
    return createForD8(
        getClassType() == other ? getClassType() : appView.dexItemFactory().objectType,
        nullability().join(nullability));
  }

  /**
   * Internal marker for finding the LUB between sets of interfaces.
   *
   * <p>The marker is used both as the identification of which side the traversal is on and if that
   * item is known to always be present. That use denotes a immutable use fo the marker and reuses
   * the static constants defined below. When traversing the interface super chains each point is
   * mapped to a mutable marking that keeps track of what paths have reached it. The mutable use is
   * allocated with 'createEmpty' and updated with 'merge'.
   */
  private static class InterfaceMarker {

    // Each side is tracked with a three-valued marking.
    // Note that the value FALSE is not part of the possible three values, only:
    //   FALSE: not marked / not present.
    //   TRUE: marked and known to be present.
    //   UNKNOWN: marked and unknown if actually present.
    private OptionalBool left;
    private OptionalBool right;

    static final InterfaceMarker LEFT_KNOWN =
        new InterfaceMarker(OptionalBool.TRUE, OptionalBool.FALSE);
    static final InterfaceMarker LEFT_UNKNOWN =
        new InterfaceMarker(OptionalBool.UNKNOWN, OptionalBool.FALSE);
    static final InterfaceMarker RIGHT_KNOWN =
        new InterfaceMarker(OptionalBool.FALSE, OptionalBool.TRUE);
    static final InterfaceMarker RIGHT_UNKNOWN =
        new InterfaceMarker(OptionalBool.FALSE, OptionalBool.UNKNOWN);

    static InterfaceMarker forLeft(boolean isKnown) {
      return isKnown ? LEFT_KNOWN : LEFT_UNKNOWN;
    }

    static InterfaceMarker forRight(boolean isKnown) {
      return isKnown ? RIGHT_KNOWN : RIGHT_UNKNOWN;
    }

    static InterfaceMarker createUnmarked() {
      return new InterfaceMarker(OptionalBool.FALSE, OptionalBool.FALSE);
    }

    public InterfaceMarker(OptionalBool left, OptionalBool right) {
      this.left = left;
      this.right = right;
      assert !isMarkedOnBothSides();
    }

    boolean isMarked() {
      return left.isPossiblyTrue() || right.isPossiblyTrue();
    }

    boolean isMarkedOnBothSides() {
      return left.isPossiblyTrue() && right.isPossiblyTrue();
    }

    static OptionalBool knownIfAnyIsKnown(OptionalBool v1, OptionalBool v2) {
      assert v1.isPossiblyTrue() || v2.isPossiblyTrue();
      return v1.isTrue() || v2.isTrue() ? OptionalBool.TRUE : OptionalBool.UNKNOWN;
    }

    boolean knownIfBothAreKnown() {
      assert isMarkedOnBothSides();
      return left.isTrue() && right.isTrue();
    }

    @SuppressWarnings("ReferenceEquality")
    boolean merge(InterfaceMarker marker) {
      assert marker.isMarked();
      assert !marker.isMarkedOnBothSides();
      if (marker.left.isPossiblyTrue()) {
        OptionalBool oldLeft = left;
        left = knownIfAnyIsKnown(left, marker.left);
        // Only continue if the other side is absent and this side changed.
        return right.isFalse() && left != oldLeft;
      } else {
        OptionalBool oldRight = right;
        right = knownIfAnyIsKnown(right, marker.right);
        // Only continue if the other side is absent and this side changed.
        return left.isFalse() && right != oldRight;
      }
    }
  }

  private static class InterfaceWithMarker {
    final DexType itf;
    final InterfaceMarker marker;

    InterfaceWithMarker(DexType itf, InterfaceMarker marker) {
      this.itf = itf;
      this.marker = marker;
    }
  }

  @SuppressWarnings("ReferenceEquality")
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

  @SuppressWarnings("BadImport")
  public static InterfaceCollection computeLeastUpperBoundOfInterfaces(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      InterfaceCollection s1,
      InterfaceCollection s2) {
    if (s1.isEmpty() || s2.isEmpty()) {
      return InterfaceCollection.empty();
    }
    // Synchronization is required, see b/242286733.
    synchronized (appView.dexItemFactory().leastUpperBoundOfInterfacesTable) {
      InterfaceCollection cached =
          appView.dexItemFactory().leastUpperBoundOfInterfacesTable.get(s1, s2);
      if (cached != null) {
        return cached;
      }
      cached = appView.dexItemFactory().leastUpperBoundOfInterfacesTable.get(s2, s1);
      if (cached != null) {
        return cached;
      }
    }
    Map<DexType, InterfaceMarker> seen = new IdentityHashMap<>();
    Queue<InterfaceWithMarker> worklist = new ArrayDeque<>();
    s1.forEach(
        (itf1, isKnown) ->
            worklist.add(new InterfaceWithMarker(itf1, InterfaceMarker.forLeft(isKnown))));
    s2.forEach(
        (itf2, isKnown) ->
            worklist.add(new InterfaceWithMarker(itf2, InterfaceMarker.forRight(isKnown))));

    while (!worklist.isEmpty()) {
      InterfaceWithMarker item = worklist.poll();
      DexType itf = item.itf;
      InterfaceMarker marker = item.marker;
      InterfaceMarker marking = seen.computeIfAbsent(itf, k -> InterfaceMarker.createUnmarked());
      if (marking.merge(marker)) {
        // Put super interfaces into the worklist.
        DexClass itfClass = appView.definitionFor(itf);
        if (itfClass != null) {
          for (DexType superItf : itfClass.interfaces.values) {
            worklist.add(new InterfaceWithMarker(superItf, marker));
          }
        }
      }
    }

    List<Pair<DexType, Boolean>> commonlyVisited = new ArrayList<>(seen.size());
    seen.forEach(
        (itf, marking) -> {
          // Keep commonly visited interfaces only
          if (marking.isMarkedOnBothSides()) {
            commonlyVisited.add(new Pair<>(itf, marking.knownIfBothAreKnown()));
          }
        });

    Builder lubBuilder = InterfaceCollection.builder();
    for (Pair<DexType, Boolean> entry : commonlyVisited) {
      // If there is a strict sub interface of this interface, it is not the least element.
      boolean notTheLeast = false;
      for (Pair<DexType, Boolean> other : commonlyVisited) {
        if (appView.appInfo().isStrictSubtypeOf(other.getFirst(), entry.getFirst())) {
          notTheLeast = true;
          break;
        }
      }
      if (notTheLeast) {
        continue;
      }
      lubBuilder.addInterface(entry.getFirst(), entry.getSecond());
    }
    InterfaceCollection lub = lubBuilder.build();
    // Cache the computation result only if the given two sets of interfaces are different.
    if (!s1.equals(s2)) {
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
    return getInterfaces().equals(other.getInterfaces());
  }
}
