// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.FunctionUtils.ignoreArgument;
import static com.android.tools.r8.utils.MapUtils.unmodifiableForTesting;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessInfoCollection;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.graph.TopDownClassHierarchyTraversal;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * A pass to rename methods using common, short names.
 *
 * <p>To assign names, we model the scopes of methods names and overloading/shadowing based on the
 * subtyping tree of classes. Such a naming scope is encoded by {@link MethodReservationState} and
 * {@link MethodNamingState}. {@link MethodReservationState} keeps track of reserved names in the
 * library and pulls reservations on classpath and program path to the library frontier. It keeps
 * track of names that are are reserved (due to keep annotations or otherwise) where {@link
 * MethodNamingState} keeps track of all renamed names to ensure freshness.
 *
 * <p>As in the Dalvik VM method dispatch takes argument and return types of methods into account,
 * we can further reuse names if the prototypes of two methods differ. For this, we store the above
 * state separately for each proto using a map from protos to {@link
 * MethodReservationState.InternalReservationState} objects. These internal state objects are also
 * linked.
 *
 * <p>Name assignment happens in 4 stages. In the first stage, we record all names that are used by
 * library classes or are flagged using a keep rule as reserved. This step also allocates the {@link
 * MethodNamingState} objects for library classes. We can fully allocate these objects as we never
 * perform naming for library classes. For non-library classes, we only allocate a state for the
 * highest non-library class, i.e., we allocate states for every direct subtype of a library class.
 * The states at the boundary between library and program classes are referred to as the frontier
 * states in the code.
 *
 * <p>When reserving names in program classes, we reserve them in the state of the corresponding
 * frontier class. This is to ensure that the names are not used for renaming in any supertype.
 * Thus, they will still be available in the subtype where they are reserved. Note that name
 * reservation only blocks names from being used for minification. We assume that the input program
 * is correctly named.
 *
 * <p>In stage 2, we reserve names that stem from interfaces. These are not propagated to
 * subinterfaces or implementing classes. Instead, stage 3 makes sure to query related states when
 * making naming decisions.
 *
 * <p>In stage 3, we compute minified names for all interface methods. We do this first to reduce
 * assignment conflicts. Interfaces do not build a tree-like inheritance structure we can exploit.
 * Thus, we have to infer the structure on the fly. For this, we compute a sets of reachable
 * interfaces. i.e., interfaces that are related via subtyping. Based on these sets, we then find,
 * for each method signature, the classes and interfaces this method signature is defined in. For
 * classes, as we still use frontier states at this point, we do not have to consider subtype
 * relations. For interfaces, we reserve the name in all reachable interfaces and thus ensure
 * availability.
 *
 * <p>Name assignment in this phase is a search over all impacted naming states. Using the naming
 * state of the interface this method first originated from, we propose names until we find a
 * matching one. We use the naming state of the interface to not impact name availability in naming
 * states of classes. Hence, skipping over names during interface naming does not impact their
 * availability in the next phase.
 *
 * <p>In stage 4, we assign names to methods by traversing the subtype tree, now allocating separate
 * naming states for each class starting from the frontier. In the first swoop, we allocate all
 * non-private methods, updating naming states accordingly.
 *
 * <p>Finally, the computed renamings are returned as a map from {@link DexMethod} to {@link
 * DexString}. The MethodNameMinifier object should not be retained to ensure all intermediate state
 * is freed.
 *
 * <p>TODO(b/130338621): Currently, we do not minify members of annotation interfaces, as this would
 * require parsing and minification of the string arguments to annotations.
 */
class MethodNameMinifier {

  // A class that provides access to the minification state. An instance of this class is passed
  // from the method name minifier to the interface method name minifier.
  class State {

    @SuppressWarnings("ReferenceEquality")
    void putRenaming(DexEncodedMethod key, DexString newName) {
      if (newName != key.getName()) {
        renaming.put(key.getReference(), newName);
      }
    }

    MethodReservationState<?> getReservationState(DexType type) {
      return reservationStates.get(type);
    }

    MethodNamingState<?> getNamingState(DexType type) {
      return getOrAllocateMethodNamingStates(type);
    }

    void allocateReservationStateAndReserve(DexType type, DexType frontier) {
      MethodNameMinifier.this.allocateReservationStateAndReserve(
          type, frontier, rootReservationState);
    }

    DexType getFrontier(DexType type) {
      return frontiers.getOrDefault(type, type);
    }

    DexString getReservedName(DexEncodedMethod method, DexClass holder) {
      return strategy.getReservedName(method, holder);
    }
  }

  private final AppView<AppInfoWithLiveness> appView;
  private final MemberNamingStrategy strategy;

  private final Map<DexMethod, DexString> renaming = new IdentityHashMap<>();

  private final State minifierState = new State();

  // The use of a bidirectional map allows us to map a naming state to the type it represents,
  // which is useful for debugging.
  private final BiMap<DexType, MethodReservationState<?>> reservationStates = HashBiMap.create();
  private final Map<DexType, MethodNamingState<?>> namingStates = new IdentityHashMap<>();
  private final Map<DexType, DexType> frontiers = new IdentityHashMap<>();

  private final MethodNamingState<?> rootNamingState;
  private final MethodReservationState<?> rootReservationState;

  MethodNameMinifier(
      AppView<AppInfoWithLiveness> appView,
      MemberNamingStrategy strategy) {
    this.appView = appView;
    this.strategy = strategy;
    rootReservationState = MethodReservationState.createRoot(getReservationKeyTransform());
    reservationStates.put(null, rootReservationState);
    rootNamingState =
        MethodNamingState.createRoot(getNamingKeyTransform(), strategy, rootReservationState);
    namingStates.put(null, rootNamingState);
  }

  private Function<DexMethod, ?> getReservationKeyTransform() {
    if (appView.options().getProguardConfiguration().isOverloadAggressively()
        && appView.options().isGeneratingClassFiles()) {
      // Use the full proto as key, hence reuse names based on full signature.
      return method -> method.proto;
    } else {
      // Only use the parameters as key, hence do not reuse names on return type.
      return method -> method.proto.parameters;
    }
  }

  private Function<DexMethod, ?> getNamingKeyTransform() {
    return appView.options().isGeneratingClassFiles()
        ? getReservationKeyTransform()
        : method -> null;
  }

  static class MethodRenaming {

    final Map<DexMethod, DexString> renaming;

    private MethodRenaming(Map<DexMethod, DexString> renaming) {
      this.renaming = renaming;
    }

    public static MethodRenaming empty() {
      return new MethodRenaming(ImmutableMap.of());
    }
  }

  MethodRenaming computeRenaming(
      Iterable<DexClass> interfaces,
      SubtypingInfo subtypingInfo,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    // Phase 1: Reserve all the names that need to be kept and allocate linked state in the
    //          library part.
    timing.begin("Phase 1");
    reserveNamesInClasses();
    timing.end();
    // Phase 2: Reserve all the names that are required for interfaces, and then assign names to
    //          interface methods. These are assigned by finding a name that is free in all naming
    //          states that may hold an implementation.
    timing.begin("Phase 2");
    InterfaceMethodNameMinifier interfaceMethodNameMinifier =
        new InterfaceMethodNameMinifier(appView, minifierState, subtypingInfo);
    timing.end();
    timing.begin("Phase 3");
    interfaceMethodNameMinifier.assignNamesToInterfaceMethods(timing, interfaces);
    timing.end();
    // Phase 4: Assign names top-down by traversing the subtype hierarchy.
    timing.begin("Phase 4");
    assignNamesToClassesMethods();
    renameMethodsInUnrelatedClasspathClasses();
    timing.end();
    timing.begin("Phase 5: non-rebound references");
    renameNonReboundReferences(executorService);
    timing.end();

    return new MethodRenaming(renaming);
  }

  private void assignNamesToClassesMethods() {
    TopDownClassHierarchyTraversal.forAllClasses(appView)
        .excludeInterfaces()
        .visit(
            appView.appInfo().classes(),
            clazz -> {
              DexType type = clazz.type;
              MethodReservationState<?> reservationState =
                  reservationStates.get(frontiers.getOrDefault(type, type));
              assert reservationState != null
                  : "Could not find reservation state for " + type.toString();
              MethodNamingState<?> namingState =
                  namingStates.computeIfAbsent(
                      type,
                      ignore ->
                          namingStates
                              .getOrDefault(clazz.superType, rootNamingState)
                              .createChild(reservationState));
              if (strategy.allowMemberRenaming(clazz)) {
                for (DexEncodedMethod method : clazz.allMethodsSorted()) {
                  assignNameToMethod(clazz, method, namingState);
                }
              }
            });
  }

  @SuppressWarnings("ReferenceEquality")
  private void renameMethodsInUnrelatedClasspathClasses() {
    if (appView.options().getProguardConfiguration().hasApplyMappingFile()) {
      appView
          .appInfo()
          .forEachReferencedClasspathClass(
              clazz -> {
                for (DexEncodedMethod method : clazz.methods()) {
                  DexString reservedName = strategy.getReservedName(method, clazz);
                  if (reservedName != null && reservedName != method.getReference().name) {
                    renaming.put(method.getReference(), reservedName);
                  }
                }
              });
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private void assignNameToMethod(
      DexClass holder, DexEncodedMethod method, MethodNamingState<?> state) {
    if (method.isInitializer()) {
      return;
    }
    // The strategy may have an explicit naming for this member which we query first. It may be that
    // the strategy will return the identity name, for which we have to look into a previous
    // renaming tracked by the state.
    DexString newName = strategy.getReservedName(method, holder);
    if (newName == null || newName == method.getName()) {
      newName = state.newOrReservedNameFor(method);
    }
    if (method.getName() != newName) {
      renaming.put(method.getReference(), newName);
    }
    state.addRenaming(newName, method);
  }

  @SuppressWarnings("ReferenceEquality")
  private void reserveNamesInClasses() {
    // Ensure reservation state for java.lang.Object is always created, even if the type is missing.
    allocateReservationStateAndReserve(
        appView.dexItemFactory().objectType,
        appView.dexItemFactory().objectType,
        rootReservationState);
    TopDownClassHierarchyTraversal.forAllClasses(appView)
        .visit(
            appView.appInfo().classes(),
            clazz -> {
              DexType type = clazz.type;
              DexType frontier = frontiers.getOrDefault(clazz.superType, type);
              if (frontier != type || clazz.isProgramClass()) {
                DexType existingValue = frontiers.put(clazz.type, frontier);
                assert existingValue == null;
              }
              // If this is not a program class (or effectively a library class as it is missing)
              // move the frontier forward. This will ensure all reservations are put on the library
              // or classpath frontier for the program path.
              allocateReservationStateAndReserve(
                  type,
                  frontier,
                  reservationStates.getOrDefault(clazz.superType, rootReservationState));
            });
  }

  private void allocateReservationStateAndReserve(
      DexType type, DexType frontier, MethodReservationState<?> parent) {
    MethodReservationState<?> state =
        reservationStates.computeIfAbsent(frontier, ignore -> parent.createChild());
    DexClass holder = appView.definitionFor(type);
    if (holder != null) {
      // When javac compiles code against a class-path it will assume that the target of a synthetic
      // bridge has the same name as the bridge.
      // <pre>
      //   public class I { Result foo(); }
      //   public class Impl {
      //     ResultImpl foo() { ... }  <-- this needs to be named foo
      //     Result {synthetic,bridge} foo() { invoke-virtual foo():()ResultImpl; }
      //   }
      // </pre>
      // In general we should consider the bridge and target as a group and reserve/rename as one
      // however that is difficult in the current setup of reservation states. We therefore allow
      // them to differ unless there is a kept/reserved method. For one to observe this one would
      // have to do byte-code rewriting against a mapping file to observe the issue. Doing that they
      // may as well just adjust the keep rules to keep the targets of bridges.
      // See b/290711987 for an actual issue regarding this.
      Set<DexEncodedMethod> bridgeMethodCandidates = Sets.newIdentityHashSet();
      Iterable<DexEncodedMethod> methods = shuffleMethods(holder.methods(), appView.options());
      for (DexEncodedMethod method : methods) {
        DexString reservedName = strategy.getReservedName(method, holder);
        if (reservedName != null) {
          state.reserveName(reservedName, method);
        } else if (appView.options().isGeneratingClassFiles() && method.isSyntheticBridgeMethod()) {
          bridgeMethodCandidates.add(method);
        }
      }
      Map<DexString, Set<Integer>> methodNamesToReserve =
          computeBridgesThatAreReserved(holder, bridgeMethodCandidates);
      if (!methodNamesToReserve.isEmpty()) {
        for (DexEncodedMethod method : methods) {
          if (methodNamesToReserve
              .getOrDefault(method.getName(), Collections.emptySet())
              .contains(method.getProto().getArity())) {
            state.reserveName(method.getName(), method);
          }
        }
      }
    }
  }

  private Map<DexString, Set<Integer>> computeBridgesThatAreReserved(
      DexClass holder, Set<DexEncodedMethod> methods) {
    if (methods.isEmpty()) {
      return Collections.emptyMap();
    }
    WorkList<DexClass> workList = WorkList.newIdentityWorkList(holder);
    Map<DexString, Set<Integer>> reservedNamesWithArity = new HashMap<>();
    while (workList.hasNext()) {
      DexClass clazz = workList.next();
      MethodReservationState<?> state = reservationStates.get(frontiers.get(clazz.getType()));
      if (state != null) {
        methods.forEach(
            bridgeMethod -> {
              if (state.isReserved(bridgeMethod.getName(), bridgeMethod.getReference())) {
                reservedNamesWithArity
                    .computeIfAbsent(bridgeMethod.getName(), ignoreArgument(HashSet::new))
                    .add(bridgeMethod.getProto().getArity());
              }
            });
      }
      clazz.forEachImmediateSupertype(
          superType -> {
            DexClass superClass = appView.definitionFor(superType);
            if (superClass != null) {
              workList.addIfNotSeen(superClass);
            }
          });
    }
    return unmodifiableForTesting(reservedNamesWithArity);
  }

  @SuppressWarnings("ReferenceEquality")
  private MethodNamingState<?> getOrAllocateMethodNamingStates(DexType type) {
    MethodNamingState<?> namingState = namingStates.get(type);
    if (namingState == null) {
      MethodNamingState<?> parentState;
      if (type == appView.dexItemFactory().objectType) {
        parentState = rootNamingState;
      } else {
        DexClass holder = appView.definitionFor(type);
        if (holder == null) {
          parentState = getOrAllocateMethodNamingStates(appView.dexItemFactory().objectType);
        } else {
          parentState = getOrAllocateMethodNamingStates(holder.superType);
        }
      }
      // There can be gaps in the reservation states if a library class extends a program class.
      // See b/150325706 for more information.
      MethodReservationState<?> reservationState = findReservationStateInHierarchy(type);
      assert reservationState != null : "Could not find reservation state for " + type.toString();
      namingState = parentState.createChild(reservationState);
      namingStates.put(type, namingState);
    }
    return namingState;
  }

  private MethodReservationState<?> findReservationStateInHierarchy(DexType type) {
    MethodReservationState<?> reservationState = reservationStates.get(type);
    if (reservationState != null) {
      return reservationState;
    }
    if (appView.definitionFor(type) == null) {
      // Reservation states for missing definitions is always object.
      return reservationStates.get(appView.dexItemFactory().objectType);
    }
    // If we cannot find the reservation state, which is a result from a library class extending
    // a program class. The gap is tracked in the frontier state.
    assert frontiers.containsKey(type);
    DexType frontierType = frontiers.get(type);
    reservationState = reservationStates.get(frontierType);
    assert reservationState != null
        : "Could not find reservation state for frontier type " + frontierType.toString();
    return reservationState;
  }

  private void renameNonReboundReferences(ExecutorService executorService)
      throws ExecutionException {
    Map<DexMethod, DexString> nonReboundRenamings = new ConcurrentHashMap<>();
    MethodAccessInfoCollection methodAccessInfoCollection =
        appView.appInfo().getMethodAccessInfoCollection();
    ThreadUtils.processItems(
        methodAccessInfoCollection::forEachMethodReference,
        method -> renameNonReboundMethodReference(method, nonReboundRenamings),
        appView.options().getThreadingModule(),
        executorService);
    renaming.putAll(nonReboundRenamings);
  }

  @SuppressWarnings("ReferenceEquality")
  private void renameNonReboundMethodReference(
      DexMethod method, Map<DexMethod, DexString> nonReboundRenamings) {
    if (method.getHolderType().isArrayType()) {
      return;
    }

    DexClass holder = appView.contextIndependentDefinitionFor(method.getHolderType());
    if (holder == null) {
      return;
    }

    MethodResolutionResult resolutionResult =
        appView.appInfo().resolveMethodOnLegacy(holder, method);
    if (resolutionResult.isSingleResolution()) {
      DexEncodedMethod resolvedMethod = resolutionResult.getSingleTarget();
      if (resolvedMethod.getReference() == method) {
        return;
      }

      DexString newName = renaming.get(resolvedMethod.getReference());
      if (newName != null) {
        assert newName != resolvedMethod.getName();
        nonReboundRenamings.put(method, newName);
      }
      return;
    }

    // If resolution fails, the method must be renamed consistently with the targets that give rise
    // to the failure.
    assert resolutionResult.isFailedResolution();

    List<DexEncodedMethod> targets = new ArrayList<>();
    resolutionResult
        .asFailedResolution()
        .forEachFailureDependency(ConsumerUtils.emptyConsumer(), targets::add);
    if (!targets.isEmpty()) {
      DexString newName = renaming.get(targets.get(0).getReference());
      assert targets.stream().allMatch(target -> renaming.get(target.getReference()) == newName);
      if (newName != null) {
        assert newName != targets.get(0).getName();
        nonReboundRenamings.put(method, newName);
      }
    }
  }

  // Shuffles the given methods if assertions are enabled and deterministic debugging is disabled.
  // Used to ensure that the generated output is deterministic.
  private static Iterable<DexEncodedMethod> shuffleMethods(
      Iterable<DexEncodedMethod> methods, InternalOptions options) {
    return options.testing.irOrdering.order(methods);
  }
}
