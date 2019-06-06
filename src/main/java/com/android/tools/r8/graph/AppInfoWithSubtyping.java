// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.origin.Origin;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;

public class AppInfoWithSubtyping extends AppInfo implements ClassHierarchy {

  private static final int ROOT_LEVEL = 0;
  private static final int UNKNOWN_LEVEL = -1;
  private static final int INTERFACE_LEVEL = -2;
  // Since most Java types has no sub-types, we can just share an empty immutable set until we need
  // to add to it.
  private static final Set<DexType> NO_DIRECT_SUBTYPE = ImmutableSet.of();

  private static class TypeInfo {
    final DexType type;

    int hierarchyLevel = UNKNOWN_LEVEL;
    /**
     * Set of direct subtypes. This set has to remain sorted to ensure determinism. The actual
     * sorting is not important but {@link DexType#slowCompareTo(DexType)} works well.
     */
    Set<DexType> directSubtypes = NO_DIRECT_SUBTYPE;

    // Caching what interfaces this type is implementing. This includes super-interface hierarchy.
    Set<DexType> implementedInterfaces = null;

    public TypeInfo(DexType type) {
      this.type = type;
    }

    @Override
    public String toString() {
      return "TypeInfo{" + type + ", level:" + hierarchyLevel + "}";
    }

    private void ensureDirectSubTypeSet() {
      if (directSubtypes == NO_DIRECT_SUBTYPE) {
        directSubtypes = new TreeSet<>(DexType::slowCompareTo);
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

    public synchronized void addDirectSubtype(TypeInfo subtypeInfo) {
      assert hierarchyLevel != UNKNOWN_LEVEL;
      ensureDirectSubTypeSet();
      directSubtypes.add(subtypeInfo.type);
      subtypeInfo.setLevel(hierarchyLevel + 1);
    }

    void tagAsSubtypeRoot() {
      setLevel(ROOT_LEVEL);
    }

    void tagAsInteface() {
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

  // Map from types to their subtyping information.
  private final Map<DexType, TypeInfo> typeInfo;

  public AppInfoWithSubtyping(DexApplication application) {
    super(application);
    typeInfo = Collections.synchronizedMap(new IdentityHashMap<>());
    // Recompute subtype map if we have modified the graph.
    populateSubtypeMap(application.asDirect(), application.dexItemFactory);
  }

  protected AppInfoWithSubtyping(AppInfoWithSubtyping previous) {
    super(previous);
    missingClasses.addAll(previous.missingClasses);
    subtypeMap.putAll(previous.subtypeMap);
    typeInfo = Collections.synchronizedMap(new IdentityHashMap<>(previous.typeInfo));
    assert app() instanceof DirectMappedDexApplication;
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
    return typeInfo.computeIfAbsent(type, TypeInfo::new);
  }

  private void populateAllSuperTypes(Map<DexType, Set<DexType>> map, DexType holder,
      DexClass baseClass, Function<DexType, DexClass> definitions) {
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
        getTypeInfo(holder).tagAsInteface();
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

  private void populateSubtypeMap(DirectMappedDexApplication app, DexItemFactory dexItemFactory) {
    getTypeInfo(dexItemFactory.objectType).tagAsSubtypeRoot();
    Map<DexType, Set<DexType>> map = new IdentityHashMap<>();
    for (DexClass clazz : app.allClasses()) {
      populateAllSuperTypes(map, clazz.type, clazz, app::definitionFor);
    }
    for (Map.Entry<DexType, Set<DexType>> entry : map.entrySet()) {
      subtypeMap.put(entry.getKey(), ImmutableSet.copyOf(entry.getValue()));
    }
    assert validateLevelsAreCorrect(app::definitionFor, dexItemFactory);
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
      TypeInfo superInfo = getTypeInfo(superType);
      TypeInfo nextInfo = getTypeInfo(next);
      if (superType == null) {
        assert nextInfo.hierarchyLevel == ROOT_LEVEL;
      } else {
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

  // For mapping invoke virtual instruction to target methods.
  public Set<DexEncodedMethod> lookupVirtualTargets(DexMethod method) {
    assert checkIfObsolete();
    if (method.holder.isArrayType()) {
      // For javac output this will only be clone(), but in general the methods from Object can
      // be invoked with an array type holder.
      return null;
    }
    DexClass root = definitionFor(method.holder);
    if (root == null) {
      // type specified in method does not have a materialized class.
      return null;
    }
    ResolutionResult topTargets = resolveMethodOnClass(method.holder, method);
    if (topTargets.asResultOfResolve() == null) {
      // This will fail at runtime.
      return null;
    }
    // First add the target for receiver type method.type.
    Set<DexEncodedMethod> result = new HashSet<>();
    topTargets.forEachTarget(result::add);
    // Add all matching targets from the subclass hierarchy.
    for (DexType type : subtypes(method.holder)) {
      DexClass clazz = definitionFor(type);
      if (!clazz.isInterface()) {
        ResolutionResult methods = resolveMethodOnClass(type, method);
        methods.forEachTarget(
            target -> {
              if (target.isVirtualMethod()) {
                result.add(target);
              }
            });
      }
    }
    return result;
  }

  /**
   * Lookup super method following the super chain from the holder of {@code method}.
   * <p>
   * This method will resolve the method on the holder of {@code method} and only return a non-null
   * value if the result of resolution was an instance (i.e. non-static) method.
   * <p>
   * Additionally, this will also verify that the invoke super is valid, i.e., it is on the same
   * type or a super type of the current context. See comment in {@link
   * com.android.tools.r8.ir.conversion.JarSourceCode#invokeType}.
   *
   * @param method the method to lookup
   * @param invocationContext the class the invoke is contained in, i.e., the holder of the caller.
   * @return The actual target for {@code method} or {@code null} if none found.
   */
  @Override
  public DexEncodedMethod lookupSuperTarget(DexMethod method, DexType invocationContext) {
    assert checkIfObsolete();
    if (!isSubtype(invocationContext, method.holder)) {
      DexClass contextClass = definitionFor(invocationContext);
      isSubtype(invocationContext, method.holder);
      throw new CompilationError(
          "Illegal invoke-super to " + method.toSourceString() + " from class " + invocationContext,
          contextClass != null ? contextClass.getOrigin() : Origin.unknown());
    }
    return super.lookupSuperTarget(method, invocationContext);
  }

  protected boolean hasAnyInstantiatedLambdas(DexType type) {
    assert checkIfObsolete();
    return true; // Don't know, there might be.
  }

  // For mapping invoke interface instruction to target methods.
  public Set<DexEncodedMethod> lookupInterfaceTargets(DexMethod method) {
    assert checkIfObsolete();
    // First check that there is a target for this invoke-interface to hit. If there is none,
    // this will fail at runtime.
    ResolutionResult topTarget = resolveMethodOnInterface(method.holder, method);
    if (topTarget.asResultOfResolve() == null) {
      return null;
    }

    Set<DexEncodedMethod> result = new HashSet<>();
    if (topTarget.hasSingleTarget()) {
      // Add default interface methods to the list of targets.
      //
      // This helps to make sure we take into account synthesized lambda classes
      // that we are not aware of. Like in the following example, we know that all
      // classes, XX in this case, override B::bar(), but there are also synthesized
      // classes for lambda which don't, so we still need default method to be live.
      //
      //   public static void main(String[] args) {
      //     X x = () -> {};
      //     x.bar();
      //   }
      //
      //   interface X {
      //     void foo();
      //     default void bar() { }
      //   }
      //
      //   class XX implements X {
      //     public void foo() { }
      //     public void bar() { }
      //   }
      //
      DexEncodedMethod singleTarget = topTarget.asSingleTarget();
      if (singleTarget.getCode() != null && hasAnyInstantiatedLambdas(singleTarget.method.holder)) {
        result.add(singleTarget);
      }
    }

    Consumer<DexEncodedMethod> addIfNotAbstract =
        m -> {
          if (!m.accessFlags.isAbstract()) {
            result.add(m);
          }
        };
    // Default methods are looked up when looking at a specific subtype that does not override them.
    // Otherwise, we would look up default methods that are actually never used. However, we have to
    // add bridge methods, otherwise we can remove a bridge that will be used.
    Consumer<DexEncodedMethod> addIfNotAbstractAndBridge =
        m -> {
          if (!m.accessFlags.isAbstract() && m.accessFlags.isBridge()) {
            result.add(m);
          }
        };

    Set<DexType> set = subtypes(method.holder);
    for (DexType type : set) {
      DexClass clazz = definitionFor(type);
      if (clazz.isInterface()) {
        ResolutionResult targetMethods = resolveMethodOnInterface(type, method);
        targetMethods.forEachTarget(addIfNotAbstractAndBridge);
      } else {
        ResolutionResult targetMethods = resolveMethodOnClass(type, method);
        targetMethods.forEachTarget(addIfNotAbstract);
      }
    }
    return result;
  }

  /**
   * Resolve the methods implemented by the lambda expression that created the {@code callSite}.
   *
   * <p>If {@code callSite} was not created as a result of a lambda expression (i.e. the metafactory
   * is not {@code LambdaMetafactory}), the empty set is returned.
   *
   * <p>If the metafactory is neither {@code LambdaMetafactory} nor {@code StringConcatFactory}, a
   * warning is issued.
   *
   * <p>The returned set of methods all have {@code callSite.methodName} as the method name.
   *
   * @param callSite Call site to resolve.
   * @return Methods implemented by the lambda expression that created the {@code callSite}.
   */
  public Set<DexEncodedMethod> lookupLambdaImplementedMethods(DexCallSite callSite) {
    assert checkIfObsolete();
    List<DexType> callSiteInterfaces = LambdaDescriptor.getInterfaces(callSite, this);
    if (callSiteInterfaces == null || callSiteInterfaces.isEmpty()) {
      return Collections.emptySet();
    }
    Set<DexEncodedMethod> result = new HashSet<>();
    Deque<DexType> worklist = new ArrayDeque<>(callSiteInterfaces);
    Set<DexType> visited = Sets.newIdentityHashSet();
    while (!worklist.isEmpty()) {
      DexType iface = worklist.removeFirst();
      if (getTypeInfo(iface).isUnknown()) {
        // Skip this interface. If the lambda only implements missing library interfaces and not any
        // program interfaces, then minification and tree shaking are not interested in this
        // DexCallSite anyway, so skipping this interface is harmless. On the other hand, if
        // minification is run on a program with a lambda interface that implements both a missing
        // library interface and a present program interface, then we might minify the method name
        // on the program interface even though it should be kept the same as the (missing) library
        // interface method. That is a shame, but minification is not suited for incomplete programs
        // anyway.
        continue;
      }
      if (!visited.add(iface)) {
        // Already visited previously. May happen due to "diamond shapes" in the interface
        // hierarchy.
        continue;
      }
      assert getTypeInfo(iface).isInterface();
      DexClass clazz = definitionFor(iface);
      if (clazz != null) {
        for (DexEncodedMethod method : clazz.virtualMethods()) {
          if (method.method.name == callSite.methodName && method.accessFlags.isAbstract()) {
            result.add(method);
          }
        }
        Collections.addAll(worklist, clazz.interfaces.values);
      }
    }
    return result;
  }

  public boolean isStringConcat(DexMethodHandle bootstrapMethod) {
    assert checkIfObsolete();
    return bootstrapMethod.type.isInvokeStatic()
        && (bootstrapMethod.asMethod() == dexItemFactory().stringConcatWithConstantsMethod
            || bootstrapMethod.asMethod() == dexItemFactory().stringConcatMethod);
  }

  @Override
  public void registerNewType(DexType newType, DexType superType) {
    assert checkIfObsolete();
    // Register the relationship between this type and its superType.
    getTypeInfo(superType).addDirectSubtype(getTypeInfo(newType));
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

  public boolean isMarkedAsInterface(DexType type) {
    return getTypeInfo(type).isInterface();
  }

  @Override
  public boolean hasSubtypes(DexType type) {
    return !getTypeInfo(type).directSubtypes.isEmpty();
  }

  @Override
  public boolean isSubtype(DexType subtype, DexType supertype) {
    return subtype == supertype || isStrictSubtypeOf(subtype, supertype);
  }

  public boolean isStrictSubtypeOf(DexType subtype, DexType supertype) {
    // For all erroneous cases, saying `no`---not a strict subtype---is conservative.
    return isStrictSubtypeOf(subtype, supertype, false);
  }

  // Depending on optimizations, conservative answer of subtype relation may vary.
  // Pass different `orElse` in that case.
  public boolean isStrictSubtypeOf(DexType subtype, DexType supertype, boolean orElse) {
    if (subtype == supertype) {
      return false;
    }
    // Treat the object class special as it is always the supertype, even in the case of broken
    // subtype chains.
    if (subtype == dexItemFactory().objectType) {
      return false;
    }
    if (supertype == dexItemFactory().objectType) {
      return true;
    }
    TypeInfo subInfo = getTypeInfo(subtype);
    if (subInfo.hierarchyLevel == INTERFACE_LEVEL) {
      return isInterfaceSubtypeOf(subtype, supertype);
    }
    TypeInfo superInfo = getTypeInfo(supertype);
    if (superInfo.hierarchyLevel == INTERFACE_LEVEL) {
      return superInfo.directSubtypes.stream()
          .anyMatch(superSubtype -> isSubtype(subtype, superSubtype));
    }
    return isSubtypeOfClass(subInfo, superInfo, orElse);
  }

  private boolean isInterfaceSubtypeOf(DexType candidate, DexType other) {
    if (candidate == other || other == dexItemFactory().objectType) {
      return true;
    }
    DexClass candidateHolder = definitionFor(candidate);
    if (candidateHolder == null) {
      return false;
    }
    for (DexType iface : candidateHolder.interfaces.values) {
      assert getTypeInfo(iface).hierarchyLevel == INTERFACE_LEVEL;
      if (isInterfaceSubtypeOf(iface, other)) {
        return true;
      }
    }
    return false;
  }

  private boolean isSubtypeOfClass(TypeInfo subInfo, TypeInfo superInfo, boolean orElse) {
    if (superInfo.hierarchyLevel == UNKNOWN_LEVEL) {
      // We have no definition for this class, hence it is not part of the hierarchy.
      return orElse;
    }
    while (superInfo.hierarchyLevel < subInfo.hierarchyLevel) {
      DexClass holder = definitionFor(subInfo.type);
      assert holder != null && !holder.isInterface();
      subInfo = getTypeInfo(holder.superType);
    }
    return subInfo.type == superInfo.type;
  }

  /**
   * Apply the given function to all classes that directly extend this class.
   *
   * <p>If this class is an interface, then this method will visit all sub-interfaces. This deviates
   * from the dex-file encoding, where subinterfaces "implement" their super interfaces. However, it
   * is consistent with the source language.
   */
  public void forAllExtendsSubtypes(DexType type, Consumer<DexType> f) {
    allExtendsSubtypes(type).forEach(f);
  }

  public Iterable<DexType> allExtendsSubtypes(DexType type) {
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

  /**
   * Apply the given function to all classes that directly implement this interface.
   *
   * <p>The implementation does not consider how the hierarchy is encoded in the dex file, where
   * interfaces "implement" their super interfaces. Instead it takes the view of the source
   * language, where interfaces "extend" their superinterface.
   */
  public void forAllImplementsSubtypes(DexType type, Consumer<DexType> f) {
    allImplementsSubtypes(type).forEach(f);
  }

  public Iterable<DexType> allImplementsSubtypes(DexType type) {
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

  public boolean isExternalizable(DexType type) {
    return implementedInterfaces(type).contains(dexItemFactory().externalizableType);
  }

  public boolean isSerializable(DexType type) {
    return implementedInterfaces(type).contains(dexItemFactory().serializableType);
  }

  /** Collect all interfaces that this type directly or indirectly implements. */
  public Set<DexType> implementedInterfaces(DexType type) {
    TypeInfo info = getTypeInfo(type);
    if (info.implementedInterfaces != null) {
      return info.implementedInterfaces;
    }
    synchronized (this) {
      if (info.implementedInterfaces == null) {
        Set<DexType> interfaces = Sets.newIdentityHashSet();
        implementedInterfaces(type, interfaces);
        info.implementedInterfaces = interfaces;
      }
    }
    return info.implementedInterfaces;
  }

  private void implementedInterfaces(DexType type, Set<DexType> interfaces) {
    DexClass dexClass = definitionFor(type);
    // Loop to traverse the super type hierarchy of the current type.
    while (dexClass != null) {
      if (dexClass.isInterface()) {
        interfaces.add(dexClass.type);
      }
      for (DexType itf : dexClass.interfaces.values) {
        implementedInterfaces(itf, interfaces);
      }
      if (dexClass.superType == null) {
        break;
      }
      dexClass = definitionFor(dexClass.superType);
    }
  }

  public DexType getSingleSubtype(DexType type) {
    TypeInfo info = getTypeInfo(type);
    assert info.hierarchyLevel != UNKNOWN_LEVEL;
    if (info.directSubtypes.size() == 1) {
      return Iterables.getFirst(info.directSubtypes, null);
    } else {
      return null;
    }
  }

  @Override
  public boolean isDirectSubtype(DexType subtype, DexType supertype) {
    TypeInfo info = getTypeInfo(supertype);
    assert info.hierarchyLevel != UNKNOWN_LEVEL;
    return info.directSubtypes.contains(subtype);
  }

  // TODO(b/130636783): inconsistent location
  public DexType computeLeastUpperBoundOfClasses(DexType subtype, DexType other) {
    if (subtype == other) {
      return subtype;
    }
    DexType objectType = dexItemFactory().objectType;
    TypeInfo subInfo = getTypeInfo(subtype);
    TypeInfo superInfo = getTypeInfo(other);
    // If we have no definition for either class, stop proceeding.
    if (subInfo.hierarchyLevel == UNKNOWN_LEVEL || superInfo.hierarchyLevel == UNKNOWN_LEVEL) {
      return objectType;
    }
    if (subtype == objectType || other == objectType) {
      return objectType;
    }
    TypeInfo t1;
    TypeInfo t2;
    if (superInfo.hierarchyLevel < subInfo.hierarchyLevel) {
      t1 = superInfo;
      t2 = subInfo;
    } else {
      t1 = subInfo;
      t2 = superInfo;
    }
    // From now on, t2.hierarchyLevel >= t1.hierarchyLevel
    DexClass dexClass;
    // Make both of other and this in the same level.
    while (t2.hierarchyLevel > t1.hierarchyLevel) {
      dexClass = definitionFor(t2.type);
      if (dexClass == null || dexClass.superType == null) {
        return objectType;
      }
      t2 = getTypeInfo(dexClass.superType);
    }
    // At this point, they are at the same level.
    // lubType starts from t1, and will move up; t2 starts from itself, and will move up, too.
    // They move up in their own hierarchy tree, and will repeat the process until they meet.
    // (It will stop at anytime when either one's definition is not found.)
    DexType lubType = t1.type;
    DexType t2Type = t2.type;
    while (t2Type != lubType) {
      assert getTypeInfo(t2Type).hierarchyLevel == getTypeInfo(lubType).hierarchyLevel;
      dexClass = definitionFor(t2Type);
      if (dexClass == null) {
        lubType = objectType;
        break;
      }
      t2Type = dexClass.superType;
      dexClass = definitionFor(lubType);
      if (dexClass == null) {
        lubType = objectType;
        break;
      }
      lubType = dexClass.superType;
    }
    return lubType;
  }
}
