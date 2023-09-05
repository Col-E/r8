// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.DexApplication.classesWithDeterministicOrder;

import com.android.tools.r8.utils.structural.StructuralItem;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Consumer;

public class SubtypingInfo {

  private static final int ROOT_LEVEL = 0;
  private static final int UNKNOWN_LEVEL = -1;
  private static final int INTERFACE_LEVEL = -2;
  // Since most Java types has no sub-types, we can just share an empty immutable set until we
  // need to add to it.
  private static final Set<DexType> NO_DIRECT_SUBTYPE = ImmutableSet.of();
  // Map from types to their subtypes.
  private final Map<DexType, Set<DexType>> subtypeMap;

  private final Map<DexType, TypeInfo> typeInfo;

  private final DexDefinitionSupplier definitionSupplier;
  private final DexItemFactory factory;

  private SubtypingInfo(
      Map<DexType, TypeInfo> typeInfo,
      Map<DexType, Set<DexType>> subtypeMap,
      DexDefinitionSupplier definitionSupplier) {
    this.typeInfo = typeInfo;
    this.subtypeMap = subtypeMap;
    this.definitionSupplier = definitionSupplier;
    factory = definitionSupplier.dexItemFactory();
  }

  public static SubtypingInfo create(AppView<? extends AppInfoWithClassHierarchy> appView) {
    return create(appView.appInfo());
  }

  public static SubtypingInfo create(AppInfoWithClassHierarchy appInfo) {
    DirectMappedDexApplication directApp = appInfo.app().asDirect();
    return create(
        Iterables.concat(
            directApp.programClasses(), directApp.classpathClasses(), directApp.libraryClasses()),
        appInfo);
  }

  public static SubtypingInfo create(
      Iterable<? extends DexClass> classes, DexDefinitionSupplier definitions) {
    Map<DexType, TypeInfo> typeInfo = new ConcurrentHashMap<>();
    Map<DexType, Set<DexType>> subtypeMap = new IdentityHashMap<>();
    populateSubtypeMap(classes, subtypeMap, typeInfo, definitions);
    return new SubtypingInfo(typeInfo, subtypeMap, definitions);
  }

  private static void populateSuperType(
      Map<DexType, Set<DexType>> map,
      Map<DexType, TypeInfo> typeInfo,
      DexType superType,
      DexClass baseClass,
      DexDefinitionSupplier definitionSupplier) {
    if (superType != null) {
      Set<DexType> set = map.computeIfAbsent(superType, ignore -> new HashSet<>());
      if (set.add(baseClass.type)) {
        // Only continue recursion if type has been added to set.
        populateAllSuperTypes(map, typeInfo, superType, baseClass, definitionSupplier);
      }
    }
  }

  private TypeInfo getTypeInfo(DexType type) {
    return getTypeInfo(type, typeInfo);
  }

  private static TypeInfo getTypeInfo(DexType type, Map<DexType, TypeInfo> typeInfo) {
    assert type != null;
    return typeInfo.computeIfAbsent(type, TypeInfo::new);
  }

  @SuppressWarnings("ReferenceEquality")
  private static void populateAllSuperTypes(
      Map<DexType, Set<DexType>> map,
      Map<DexType, TypeInfo> typeInfo,
      DexType holder,
      DexClass baseClass,
      DexDefinitionSupplier definitionSupplier) {
    DexClass holderClass = definitionSupplier.contextIndependentDefinitionFor(holder);
    // Skip if no corresponding class is found.
    TypeInfo typeInfoHere = getTypeInfo(holder, typeInfo);
    if (holderClass != null) {
      holderClass.forEachImmediateSupertype(
          (superType, isInterface) -> {
            populateSuperType(map, typeInfo, superType, baseClass, definitionSupplier);
            TypeInfo superTypeInfo = getTypeInfo(superType, typeInfo);
            if (isInterface) {
              superTypeInfo.addInterfaceSubtype(holder);
            } else {
              superTypeInfo.addDirectSubtype(typeInfoHere);
            }
          });
      if (holderClass.isInterface()) {
        typeInfoHere.tagAsInterface();
      }
    } else {
      // The subtype chain is broken, at least make this type a subtype of Object.
      DexType objectType = definitionSupplier.dexItemFactory().objectType;
      if (holder != objectType) {
        getTypeInfo(objectType, typeInfo).addDirectSubtype(typeInfoHere);
      }
    }
  }

  private static void populateSubtypeMap(
      Iterable<? extends DexClass> classes,
      Map<DexType, Set<DexType>> map,
      Map<DexType, TypeInfo> typeInfo,
      DexDefinitionSupplier definitionSupplier) {
    getTypeInfo(definitionSupplier.dexItemFactory().objectType, typeInfo).tagAsSubtypeRoot();
    for (DexClass clazz : classes) {
      populateAllSuperTypes(map, typeInfo, clazz.type, clazz, definitionSupplier);
    }
    map.replaceAll((k, v) -> ImmutableSet.copyOf(v));
    assert validateLevelsAreCorrect(typeInfo, definitionSupplier);
  }

  @SuppressWarnings("ReferenceEquality")
  private static boolean validateLevelsAreCorrect(
      Map<DexType, TypeInfo> typeInfo, DexDefinitionSupplier definitionSupplier) {
    Set<DexType> seenTypes = Sets.newIdentityHashSet();
    Deque<DexType> worklist = new ArrayDeque<>();
    DexType objectType = definitionSupplier.dexItemFactory().objectType;
    worklist.add(objectType);
    while (!worklist.isEmpty()) {
      DexType next = worklist.pop();
      DexClass nextHolder = definitionSupplier.contextIndependentDefinitionFor(next);
      DexType superType;
      if (nextHolder == null) {
        // We might lack the definition of Object, so guard against that.
        superType = next == objectType ? null : objectType;
      } else {
        superType = nextHolder.superType;
      }
      assert !seenTypes.contains(next);
      seenTypes.add(next);
      TypeInfo nextInfo = getTypeInfo(next, typeInfo);
      if (superType == null) {
        assert nextInfo.hierarchyLevel == ROOT_LEVEL;
      } else {
        TypeInfo superInfo = getTypeInfo(superType, typeInfo);
        assert superInfo.hierarchyLevel == nextInfo.hierarchyLevel - 1
            || (superInfo.hierarchyLevel == ROOT_LEVEL
                && nextInfo.hierarchyLevel == INTERFACE_LEVEL);
        assert superInfo.directSubtypes.contains(next);
      }
      if (nextInfo.hierarchyLevel != INTERFACE_LEVEL) {
        // Only traverse the class hierarchy subtypes, not interfaces.
        worklist.addAll(nextInfo.directSubtypes);
      } else if (nextHolder != null) {
        // Test that the interfaces of this class are interfaces and have this class as subtype.
        for (DexType iface : nextHolder.interfaces.values) {
          TypeInfo ifaceInfo = getTypeInfo(iface, typeInfo);
          assert ifaceInfo.directSubtypes.contains(next);
          assert ifaceInfo.hierarchyLevel == INTERFACE_LEVEL;
        }
      }
    }
    return true;
  }

  public Set<DexType> subtypes(DexType type) {
    assert type.isClassType();
    Set<DexType> subtypes = subtypeMap.get(type);
    return subtypes == null ? ImmutableSet.of() : subtypes;
  }

  public DexType getSingleDirectSubtype(DexType type) {
    TypeInfo info = getTypeInfo(type);
    assert info.hierarchyLevel != SubtypingInfo.UNKNOWN_LEVEL;
    if (info.directSubtypes.size() == 1) {
      return Iterables.getFirst(info.directSubtypes, null);
    } else {
      return null;
    }
  }

  /**
   * Apply the given function to all classes that directly extend this class.
   *
   * <p>If this class is an interface, then this method will visit all sub-interfaces. This deviates
   * from the dex-file encoding, where subinterfaces "implement" their super interfaces. However, it
   * is consistent with the source language.
   */
  public void forAllImmediateExtendsSubtypes(DexType type, Consumer<DexType> f) {
    allImmediateExtendsSubtypes(type).forEach(f);
  }

  public Iterable<DexType> allImmediateExtendsSubtypes(DexType type) {
    TypeInfo info = getTypeInfo(type);
    assert info.hierarchyLevel != SubtypingInfo.UNKNOWN_LEVEL;
    if (info.hierarchyLevel == SubtypingInfo.INTERFACE_LEVEL) {
      return Iterables.filter(info.directSubtypes, t -> getTypeInfo(t).isInterface());
    } else if (info.hierarchyLevel == SubtypingInfo.ROOT_LEVEL) {
      // This is the object type. Filter out interfaces
      return Iterables.filter(info.directSubtypes, t -> !getTypeInfo(t).isInterface());
    } else {
      return info.directSubtypes;
    }
  }

  /**
   * Apply the given function to all classes that directly implement this interface.
   *
   * <p>The implementation does not consider how the hierarchy is encoded in the dex file, where
   * interfaces "implement" their super interfaces. Instead it takes the view of the source
   * language, where interfaces "extend" their superinterface.
   */
  public void forAllImmediateImplementsSubtypes(DexType type, Consumer<DexType> f) {
    allImmediateImplementsSubtypes(type).forEach(f);
  }

  public Iterable<DexType> allImmediateImplementsSubtypes(DexType type) {
    TypeInfo info = getTypeInfo(type);
    if (info.hierarchyLevel == SubtypingInfo.INTERFACE_LEVEL) {
      return Iterables.filter(info.directSubtypes, subtype -> !getTypeInfo(subtype).isInterface());
    }
    return ImmutableList.of();
  }

  public Set<DexType> allImmediateSubtypes(DexType type) {
    return getTypeInfo(type).directSubtypes;
  }

  public void forAllInterfaceRoots(Consumer<DexType> fn) {
    Iterables.filter(
            getTypeInfo(factory.objectType).directSubtypes,
            subtype -> getTypeInfo(subtype).isInterface())
        .forEach(fn);
  }

  public List<DexClass> computeReachableInterfacesWithDeterministicOrder() {
    List<DexClass> interfaces = new ArrayList<>();
    forAllInterfaceRoots(
        type ->
            definitionSupplier
                .contextIndependentDefinitionForWithResolutionResult(type)
                .forEachClassResolutionResult(interfaces::add));
    return classesWithDeterministicOrder(interfaces);
  }

  private static class TypeInfo {

    private final DexType type;

    private int hierarchyLevel = UNKNOWN_LEVEL;

    /**
     * Set of direct subtypes. This set has to remain sorted to ensure determinism. The actual
     * sorting is not important but {@link DexType#compareTo(StructuralItem)} works well.
     */
    private Set<DexType> directSubtypes = NO_DIRECT_SUBTYPE;

    TypeInfo(DexType type) {
      this.type = type;
    }

    @Override
    public int hashCode() {
      return Objects.hash(type, directSubtypes);
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public boolean equals(Object obj) {
      if (!(obj instanceof TypeInfo)) {
        return false;
      }
      TypeInfo other = (TypeInfo) obj;
      return other.type == type && other.directSubtypes.equals(directSubtypes);
    }

    @Override
    public String toString() {
      return "TypeInfo{" + type + ", level:" + hierarchyLevel + "}";
    }

    private void ensureDirectSubTypeSet() {
      if (directSubtypes == NO_DIRECT_SUBTYPE) {
        directSubtypes = new ConcurrentSkipListSet<>(DexType::compareTo);
      }
    }

    private void setLevel(int level) {
      if (level == hierarchyLevel) {
        return;
      }
      if (hierarchyLevel == INTERFACE_LEVEL) {
        assert level == ROOT_LEVEL + 1;
      } else if (level == INTERFACE_LEVEL) {
        assert hierarchyLevel == ROOT_LEVEL + 1 || hierarchyLevel == UNKNOWN_LEVEL;
        hierarchyLevel = INTERFACE_LEVEL;
      } else {
        assert hierarchyLevel == UNKNOWN_LEVEL;
        hierarchyLevel = level;
      }
    }

    private void addDirectSubtype(TypeInfo subtypeInfo) {
      assert hierarchyLevel != UNKNOWN_LEVEL;
      ensureDirectSubTypeSet();
      directSubtypes.add(subtypeInfo.type);
      subtypeInfo.setLevel(hierarchyLevel + 1);
    }

    private void tagAsSubtypeRoot() {
      setLevel(ROOT_LEVEL);
    }

    private void tagAsInterface() {
      setLevel(INTERFACE_LEVEL);
    }

    private boolean isInterface() {
      assert hierarchyLevel != UNKNOWN_LEVEL : "Program class missing: " + this;
      assert type.isClassType();
      return hierarchyLevel == INTERFACE_LEVEL;
    }

    private void addInterfaceSubtype(DexType type) {
      // Interfaces all inherit from java.lang.Object. However, we assign a special level to
      // identify them later on.
      setLevel(INTERFACE_LEVEL);
      ensureDirectSubTypeSet();
      directSubtypes.add(type);
    }
  }
}
