// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.ir.desugar.LambdaRewriter.LAMBDA_GROUP_CLASS_NAME_PREFIX;

import com.android.tools.r8.ir.analysis.type.ClassTypeLatticeElement;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.WorkList;
import com.google.common.annotations.VisibleForTesting;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Consumer;
import java.util.function.Function;

public class AppInfoWithSubtyping extends AppInfoWithClassHierarchy
    implements InstantiatedSubTypeInfo {

  private static final int ROOT_LEVEL = 0;
  private static final int UNKNOWN_LEVEL = -1;
  private static final int INTERFACE_LEVEL = -2;
  // Since most Java types has no sub-types, we can just share an empty immutable set until we need
  // to add to it.
  private static final Set<DexType> NO_DIRECT_SUBTYPE = ImmutableSet.of();

  @Override
  public void forEachInstantiatedSubType(
      DexType type,
      Consumer<DexProgramClass> subTypeConsumer,
      Consumer<LambdaDescriptor> lambdaConsumer) {
    WorkList<DexType> workList = WorkList.newIdentityWorkList();
    workList.addIfNotSeen(type);
    while (workList.hasNext()) {
      DexType subType = workList.next();
      DexProgramClass clazz = definitionForProgramType(subType);
      if (clazz != null) {
        subTypeConsumer.accept(clazz);
      }
      workList.addIfNotSeen(allImmediateSubtypes(subType));
    }
    // TODO(b/148769279): Change this when we have information about callsites.
    //  This should effectively disappear once AppInfoWithLiveness implements support.
  }

  private static class TypeInfo {

    private final DexType type;

    int hierarchyLevel = UNKNOWN_LEVEL;
    /**
     * Set of direct subtypes. This set has to remain sorted to ensure determinism. The actual
     * sorting is not important but {@link DexType#slowCompareTo(DexType)} works well.
     */
    Set<DexType> directSubtypes = NO_DIRECT_SUBTYPE;

    // Caching what interfaces this type is implementing. This includes super-interface hierarchy.
    Set<DexType> implementedInterfaces = null;

    TypeInfo(DexType type) {
      this.type = type;
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

  // Set of missing classes, discovered during subtypeMap computation.
  private final Set<DexType> missingClasses = Sets.newIdentityHashSet();

  // Map from types to their subtypes.
  private final Map<DexType, ImmutableSet<DexType>> subtypeMap = new IdentityHashMap<>();

  // Map from synthesized classes to their supertypes.
  private final Map<DexType, ImmutableSet<DexType>> supertypesForSynthesizedClasses =
      new ConcurrentHashMap<>();

  // Map from types to their subtyping information.
  private final Map<DexType, TypeInfo> typeInfo;

  // Caches which static types that may store an object that has a non-default finalize() method.
  // E.g., `java.lang.Object -> TRUE` if there is a subtype of Object that overrides finalize().
  private final Map<DexType, Boolean> mayHaveFinalizeMethodDirectlyOrIndirectlyCache =
      new ConcurrentHashMap<>();

  public AppInfoWithSubtyping(DirectMappedDexApplication application) {
    this(application, application.allClasses());
  }

  public AppInfoWithSubtyping(
      DirectMappedDexApplication application, Collection<DexClass> classes) {
    super(application);
    typeInfo = new ConcurrentHashMap<>();
    // Recompute subtype map if we have modified the graph.
    populateSubtypeMap(classes, application::definitionFor, application.dexItemFactory);
  }

  protected AppInfoWithSubtyping(AppInfoWithSubtyping previous) {
    super(previous);
    missingClasses.addAll(previous.missingClasses);
    subtypeMap.putAll(previous.subtypeMap);
    supertypesForSynthesizedClasses.putAll(previous.supertypesForSynthesizedClasses);
    typeInfo = new ConcurrentHashMap<>(previous.typeInfo);
    assert app() instanceof DirectMappedDexApplication;
  }

  @Override
  public void addSynthesizedClass(DexProgramClass synthesizedClass) {
    super.addSynthesizedClass(synthesizedClass);
    // Register synthesized type, which has two side effects:
    //   1) Set the hierarchy level of synthesized type based on that of its super type,
    //   2) Register the synthesized type as a subtype of the supertype.
    //
    // The first one makes method resolutions on that synthesized class free from assertion errors
    // about unknown hierarchy level.
    //
    // For the second one, note that such addition is synchronized, but the retrieval of direct
    // subtypes isn't. Thus, there is a chance of race conditions: utils that check/iterate direct
    // subtypes, e.g., allImmediateSubtypes, hasSubTypes, etc., may not be able to see this new
    // synthesized class. However, in practice, this would be okay because, in most cases,
    // synthesized class's super type is Object, which in general has other subtypes in any way.
    // Also, iterating all subtypes of Object usually happens before/after IR processing, i.e., as
    // part of structural changes, such as bottom-up traversal to collect all method signatures,
    // which are free from such race conditions. Another exceptional case is synthesized classes
    // whose synthesis is isolated from IR processing. For example, lambda group class that merges
    // lambdas with the same interface are synthesized/finalized even after post processing of IRs.
    assert synthesizedClass.superType == dexItemFactory().objectType
            || synthesizedClass.type.toString().contains(LAMBDA_GROUP_CLASS_NAME_PREFIX)
        : "Make sure retrieval and iteration of sub types of `" + synthesizedClass.superType
            + "` is guaranteed to be thread safe and able to see `" + synthesizedClass + "`";
    registerNewType(synthesizedClass.type, synthesizedClass.superType);

    // TODO(b/129458850): Remove when we no longer synthesize classes on-the-fly.
    Set<DexType> visited = SetUtils.newIdentityHashSet(synthesizedClass.allImmediateSupertypes());
    Deque<DexType> worklist = new ArrayDeque<>(visited);
    while (!worklist.isEmpty()) {
      DexType type = worklist.removeFirst();
      assert visited.contains(type);

      DexClass clazz = definitionFor(type);
      if (clazz == null) {
        continue;
      }

      for (DexType supertype : clazz.allImmediateSupertypes()) {
        if (visited.add(supertype)) {
          worklist.addLast(supertype);
        }
      }
    }
    if (!visited.isEmpty()) {
      supertypesForSynthesizedClasses.put(synthesizedClass.type, ImmutableSet.copyOf(visited));
    }
  }

  private boolean isSynthesizedClassStrictSubtypeOf(DexType synthesizedClass, DexType supertype) {
    Set<DexType> supertypesOfSynthesizedClass =
        supertypesForSynthesizedClasses.get(synthesizedClass);
    return supertypesOfSynthesizedClass != null && supertypesOfSynthesizedClass.contains(supertype);
  }

  private DirectMappedDexApplication getDirectApplication() {
    // TODO(herhut): Remove need for cast.
    return (DirectMappedDexApplication) app();
  }

  public Iterable<DexLibraryClass> libraryClasses() {
    assert checkIfObsolete();
    return getDirectApplication().libraryClasses();
  }

  public Set<DexType> getMissingClasses() {
    assert checkIfObsolete();
    return Collections.unmodifiableSet(missingClasses);
  }

  public Set<DexType> subtypes(DexType type) {
    assert checkIfObsolete();
    assert type.isClassType();
    ImmutableSet<DexType> subtypes = subtypeMap.get(type);
    return subtypes == null ? ImmutableSet.of() : subtypes;
  }

  private void populateSuperType(Map<DexType, Set<DexType>> map, DexType superType,
      DexClass baseClass, Function<DexType, DexClass> definitions) {
    if (superType != null) {
      Set<DexType> set = map.computeIfAbsent(superType, ignore -> new HashSet<>());
      if (set.add(baseClass.type)) {
        // Only continue recursion if type has been added to set.
        populateAllSuperTypes(map, superType, baseClass, definitions);
      }
    }
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

  public boolean hasAnyInstantiatedLambdas(DexProgramClass clazz) {
    assert checkIfObsolete();
    return true; // Don't know, there might be.
  }

  public boolean methodDefinedInInterfaces(DexEncodedMethod method, DexType implementingClass) {
    DexClass holder = definitionFor(implementingClass);
    if (holder == null) {
      return false;
    }
    for (DexType iface : holder.interfaces.values) {
      if (methodDefinedInInterface(method, iface)) {
        return true;
      }
    }
    return false;
  }

  public boolean methodDefinedInInterface(DexEncodedMethod method, DexType iface) {
    DexClass potentialHolder = definitionFor(iface);
    if (potentialHolder == null) {
      return false;
    }
    assert potentialHolder.isInterface();
    for (DexEncodedMethod virtualMethod : potentialHolder.virtualMethods) {
      if (virtualMethod.method.hasSameProtoAndName(method.method)
          && virtualMethod.accessFlags.isSameVisibility(method.accessFlags)) {
        return true;
      }
    }
    for (DexType parentInterface : potentialHolder.interfaces.values) {
      if (methodDefinedInInterface(method, parentInterface)) {
        return true;
      }
    }
    return false;
  }

  public boolean isStringConcat(DexMethodHandle bootstrapMethod) {
    assert checkIfObsolete();
    return bootstrapMethod.type.isInvokeStatic()
        && (bootstrapMethod.asMethod() == dexItemFactory().stringConcatWithConstantsMethod
            || bootstrapMethod.asMethod() == dexItemFactory().stringConcatMethod);
  }

  private void registerNewType(DexType newType, DexType superType) {
    assert checkIfObsolete();
    // Register the relationship between this type and its superType.
    getTypeInfo(superType).addDirectSubtype(getTypeInfo(newType));
  }

  @VisibleForTesting
  public void registerNewTypeForTesting(DexType newType, DexType superType) {
    registerNewType(newType, superType);
  }

  @Override
  public boolean hasSubtyping() {
    assert checkIfObsolete();
    return true;
  }

  @Override
  public AppInfoWithSubtyping withSubtyping() {
    assert checkIfObsolete();
    return this;
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

  // TODO(b/139464956): Remove this method.
  public Iterable<DexType> allImmediateExtendsSubtypes_(DexType type) {
    TypeInfo info = getTypeInfo(type);
    assert info.hierarchyLevel != UNKNOWN_LEVEL;
    if (info.hierarchyLevel == INTERFACE_LEVEL) {
      return Iterables.filter(info.directSubtypes, t -> getTypeInfo(t).isInterface());
    } else if (info.hierarchyLevel == ROOT_LEVEL) {
      // This is the object type. Filter out interfaces
      return Iterables.filter(info.directSubtypes, t -> !getTypeInfo(t).isInterface());
    } else {
      return info.directSubtypes;
    }
  }

  // TODO(b/139464956): Remove this method.
  public Iterable<DexType> allImmediateImplementsSubtypes_(DexType type) {
    TypeInfo info = getTypeInfo(type);
    if (info.hierarchyLevel == INTERFACE_LEVEL) {
      return Iterables.filter(info.directSubtypes, subtype -> !getTypeInfo(subtype).isInterface());
    }
    return ImmutableList.of();
  }

  public boolean isMissingOrHasMissingSuperType(DexType type) {
    DexClass clazz = definitionFor(type);
    return clazz == null || clazz.hasMissingSuperType(this);
  }

  // TODO(b/139464956): Remove this method.
  public DexType getSingleSubtype_(DexType type) {
    TypeInfo info = getTypeInfo(type);
    assert info.hierarchyLevel != UNKNOWN_LEVEL;
    if (info.directSubtypes.size() == 1) {
      return Iterables.getFirst(info.directSubtypes, null);
    } else {
      return null;
    }
  }

  public boolean inDifferentHierarchy(DexType type1, DexType type2) {
    return !isSubtype(type1, type2) && !isSubtype(type2, type1);
  }

  public boolean mayHaveFinalizeMethodDirectlyOrIndirectly(ClassTypeLatticeElement type) {
    if (type.getClassType() == dexItemFactory().objectType && !type.getInterfaces().isEmpty()) {
      for (DexType interfaceType : type.getInterfaces()) {
        if (computeMayHaveFinalizeMethodDirectlyOrIndirectlyIfAbsent(interfaceType, false)) {
          return true;
        }
      }
      return false;
    }
    return computeMayHaveFinalizeMethodDirectlyOrIndirectlyIfAbsent(type.getClassType(), true);
  }

  private boolean computeMayHaveFinalizeMethodDirectlyOrIndirectlyIfAbsent(
      DexType type, boolean lookUpwards) {
    assert type.isClassType();
    Boolean cache = mayHaveFinalizeMethodDirectlyOrIndirectlyCache.get(type);
    if (cache != null) {
      return cache;
    }
    DexClass clazz = definitionFor(type);
    if (clazz == null) {
      // This is strictly not conservative but is needed to avoid that we treat Object as having
      // a subtype that has a non-default finalize() implementation.
      mayHaveFinalizeMethodDirectlyOrIndirectlyCache.put(type, false);
      return false;
    }
    if (clazz.isProgramClass()) {
      if (lookUpwards) {
        DexEncodedMethod resolutionResult =
            resolveMethod(type, dexItemFactory().objectMembers.finalize).getSingleTarget();
        if (resolutionResult != null && resolutionResult.isProgramMethod(this)) {
          mayHaveFinalizeMethodDirectlyOrIndirectlyCache.put(type, true);
          return true;
        }
      } else {
        if (clazz.lookupVirtualMethod(dexItemFactory().objectMembers.finalize) != null) {
          mayHaveFinalizeMethodDirectlyOrIndirectlyCache.put(type, true);
          return true;
        }
      }
    }
    for (DexType subtype : allImmediateSubtypes(type)) {
      if (computeMayHaveFinalizeMethodDirectlyOrIndirectlyIfAbsent(subtype, false)) {
        mayHaveFinalizeMethodDirectlyOrIndirectlyCache.put(type, true);
        return true;
      }
    }
    mayHaveFinalizeMethodDirectlyOrIndirectlyCache.put(type, false);
    return false;
  }
}
