// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Consumer;
import java.util.function.Function;

public class SubtypingInfo {

  private static final int ROOT_LEVEL = 0;
  private static final int UNKNOWN_LEVEL = -1;
  private static final int INTERFACE_LEVEL = -2;
  // Since most Java types has no sub-types, we can just share an empty immutable set until we
  // need to add to it.
  private static final Set<DexType> NO_DIRECT_SUBTYPE = ImmutableSet.of();
  // Map from types to their subtyping information.
  private final DexItemFactory factory;

  private final Map<DexType, TypeInfo> typeInfo = new ConcurrentHashMap<>();

  // Map from types to their subtypes.
  private final Map<DexType, ImmutableSet<DexType>> subtypeMap = new IdentityHashMap<>();

  // TODO(b/154849103): Don't compute these here.
  // Set of missing classes, discovered during subtypeMap computation.
  private final Set<DexType> missingClasses = Sets.newIdentityHashSet();

  public SubtypingInfo(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this(appView.appInfo());
  }

  public SubtypingInfo(AppInfoWithClassHierarchy appInfo) {
    this(appInfo.app().asDirect().allClasses(), appInfo);
  }

  private SubtypingInfo(Collection<DexClass> classes, DexDefinitionSupplier definitions) {
    factory = definitions.dexItemFactory();
    // Recompute subtype map if we have modified the graph.
    populateSubtypeMap(classes, definitions::definitionFor, factory);
  }

  public boolean verifyEquals(Collection<DexClass> classes, DexDefinitionSupplier definitions) {
    SubtypingInfo subtypingInfo = new SubtypingInfo(classes, definitions);
    assert typeInfo.equals(subtypingInfo.typeInfo);
    assert subtypeMap.keySet().equals(subtypingInfo.subtypeMap.keySet());
    subtypeMap.forEach(
        (key, value) -> {
          assert subtypingInfo.subtypeMap.get(key).equals(value);
        });
    assert missingClasses.equals(subtypingInfo.missingClasses);
    return true;
  }

  private void populateSuperType(
      Map<DexType, Set<DexType>> map,
      DexType superType,
      DexClass baseClass,
      Function<DexType, DexClass> definitions) {
    if (superType != null) {
      Set<DexType> set = map.computeIfAbsent(superType, ignore -> new HashSet<>());
      if (set.add(baseClass.type)) {
        // Only continue recursion if type has been added to set.
        populateAllSuperTypes(map, superType, baseClass, definitions);
      }
    }
  }

  private DexItemFactory dexItemFactory() {
    return factory;
  }

  private TypeInfo getTypeInfo(DexType type) {
    assert type != null;
    return typeInfo.computeIfAbsent(type, TypeInfo::new);
  }

  private void populateAllSuperTypes(
      Map<DexType, Set<DexType>> map,
      DexType holder,
      DexClass baseClass,
      Function<DexType, DexClass> definitions) {
    DexClass holderClass = definitions.apply(holder);
    // Skip if no corresponding class is found.
    if (holderClass != null) {
      populateSuperType(map, holderClass.superType, baseClass, definitions);
      if (holderClass.superType != null) {
        getTypeInfo(holderClass.superType).addDirectSubtype(getTypeInfo(holder));
      } else {
        // We found java.lang.Object
        assert dexItemFactory().objectType == holder;
      }
      for (DexType inter : holderClass.interfaces.values) {
        populateSuperType(map, inter, baseClass, definitions);
        getTypeInfo(inter).addInterfaceSubtype(holder);
      }
      if (holderClass.isInterface()) {
        getTypeInfo(holder).tagAsInterface();
      }
    } else {
      if (baseClass.isProgramClass() || baseClass.isClasspathClass()) {
        missingClasses.add(holder);
      }
      // The subtype chain is broken, at least make this type a subtype of Object.
      if (holder != dexItemFactory().objectType) {
        getTypeInfo(dexItemFactory().objectType).addDirectSubtype(getTypeInfo(holder));
      }
    }
  }

  private void populateSubtypeMap(
      Collection<DexClass> classes,
      Function<DexType, DexClass> definitions,
      DexItemFactory dexItemFactory) {
    getTypeInfo(dexItemFactory.objectType).tagAsSubtypeRoot();
    Map<DexType, Set<DexType>> map = new IdentityHashMap<>();
    for (DexClass clazz : classes) {
      populateAllSuperTypes(map, clazz.type, clazz, definitions);
    }
    for (Map.Entry<DexType, Set<DexType>> entry : map.entrySet()) {
      subtypeMap.put(entry.getKey(), ImmutableSet.copyOf(entry.getValue()));
    }
    assert validateLevelsAreCorrect(definitions, dexItemFactory);
  }

  private boolean validateLevelsAreCorrect(
      Function<DexType, DexClass> definitions, DexItemFactory dexItemFactory) {
    Set<DexType> seenTypes = Sets.newIdentityHashSet();
    Deque<DexType> worklist = new ArrayDeque<>();
    DexType objectType = dexItemFactory.objectType;
    worklist.add(objectType);
    while (!worklist.isEmpty()) {
      DexType next = worklist.pop();
      DexClass nextHolder = definitions.apply(next);
      DexType superType;
      if (nextHolder == null) {
        // We might lack the definition of Object, so guard against that.
        superType = next == dexItemFactory.objectType ? null : dexItemFactory.objectType;
      } else {
        superType = nextHolder.superType;
      }
      assert !seenTypes.contains(next);
      seenTypes.add(next);
      TypeInfo nextInfo = getTypeInfo(next);
      if (superType == null) {
        assert nextInfo.hierarchyLevel == ROOT_LEVEL;
      } else {
        TypeInfo superInfo = getTypeInfo(superType);
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
          TypeInfo ifaceInfo = getTypeInfo(iface);
          assert ifaceInfo.directSubtypes.contains(next);
          assert ifaceInfo.hierarchyLevel == INTERFACE_LEVEL;
        }
      }
    }
    return true;
  }

  public Set<DexType> getMissingClasses() {
    return Collections.unmodifiableSet(missingClasses);
  }

  public Set<DexType> subtypes(DexType type) {
    assert type.isClassType();
    ImmutableSet<DexType> subtypes = subtypeMap.get(type);
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

  private static class TypeInfo {

    private final DexType type;

    int hierarchyLevel = UNKNOWN_LEVEL;
    /**
     * Set of direct subtypes. This set has to remain sorted to ensure determinism. The actual
     * sorting is not important but {@link DexType#slowCompareTo(DexType)} works well.
     */
    Set<DexType> directSubtypes = NO_DIRECT_SUBTYPE;

    TypeInfo(DexType type) {
      this.type = type;
    }

    @Override
    public int hashCode() {
      return Objects.hash(type, directSubtypes);
    }

    @Override
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
        directSubtypes = new ConcurrentSkipListSet<>(DexType::slowCompareTo);
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

    synchronized void addDirectSubtype(TypeInfo subtypeInfo) {
      assert hierarchyLevel != UNKNOWN_LEVEL;
      ensureDirectSubTypeSet();
      directSubtypes.add(subtypeInfo.type);
      subtypeInfo.setLevel(hierarchyLevel + 1);
    }

    void tagAsSubtypeRoot() {
      setLevel(ROOT_LEVEL);
    }

    void tagAsInterface() {
      setLevel(INTERFACE_LEVEL);
    }

    public boolean isInterface() {
      assert hierarchyLevel != UNKNOWN_LEVEL : "Program class missing: " + this;
      assert type.isClassType();
      return hierarchyLevel == INTERFACE_LEVEL;
    }

    public boolean isUnknown() {
      return hierarchyLevel == UNKNOWN_LEVEL;
    }

    synchronized void addInterfaceSubtype(DexType type) {
      // Interfaces all inherit from java.lang.Object. However, we assign a special level to
      // identify them later on.
      setLevel(INTERFACE_LEVEL);
      ensureDirectSubTypeSet();
      directSubtypes.add(type);
    }
  }

  public Set<DexType> allImmediateSubtypes(DexType type) {
    return getTypeInfo(type).directSubtypes;
  }

  public boolean isUnknown(DexType type) {
    return getTypeInfo(type).isUnknown();
  }

  public boolean hasSubtypes(DexType type) {
    return !getTypeInfo(type).directSubtypes.isEmpty();
  }

  void registerNewType(DexType newType, DexType superType) {
    // Register the relationship between this type and its superType.
    getTypeInfo(superType).addDirectSubtype(getTypeInfo(newType));
  }
}
