// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.MethodJavaSignatureEquivalence;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.Timing;
import com.google.common.base.Equivalence;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A pass to rename methods using common, short names.
 *
 * <p>To assign names, we model the scopes of methods names and overloading/shadowing based on the
 * subtyping tree of classes. Such a naming scope is encoded by {@link MethodNamingState}. It keeps
 * track of its parent node, names that have been reserved (due to keep annotations or otherwise)
 * and what names have been used for renaming so far.
 *
 * <p>As in the Dalvik VM method dispatch takes argument and return types of methods into account,
 * we can further reuse names if the prototypes of two methods differ. For this, we store the above
 * state separately for each proto using a map from protos to {@link
 * MethodNamingState.InternalReservationState} objects. These internal state objects are also
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

    DexString getRenaming(DexMethod key) {
      return renaming.get(key);
    }

    void putRenaming(DexMethod key, DexString value) {
      renaming.put(key, value);
    }

    MethodNamingState<?> getState(DexType type) {
      return states.get(type);
    }

    DexType getStateKey(MethodNamingState<?> state) {
      return states.inverse().get(state);
    }

    boolean isReservedInGlobalState(DexString name, DexProto state) {
      return globalState.isReserved(name, state);
    }
  }

  private final AppView<AppInfoWithLiveness> appView;
  private final Equivalence<DexMethod> equivalence;
  private final MemberNamingStrategy strategy;

  private final Map<DexMethod, DexString> renaming = new IdentityHashMap<>();
  private final MethodNamingState<?> globalState;

  private final State minifierState = new State();
  private final FrontierState frontierState = new FrontierState();

  // The use of a bidirectional map allows us to map a naming state to the type it represents,
  // which is useful for debugging.
  private final BiMap<DexType, MethodNamingState<?>> states = HashBiMap.create();

  MethodNameMinifier(AppView<AppInfoWithLiveness> appView, MemberNamingStrategy strategy) {
    this.appView = appView;
    this.equivalence =
        appView.options().getProguardConfiguration().isOverloadAggressively()
            ? MethodSignatureEquivalence.get()
            : MethodJavaSignatureEquivalence.get();
    this.globalState = MethodNamingState.createRoot(getKeyTransform(), strategy);
    this.strategy = strategy;
  }

  private MethodNamingState<?> computeStateIfAbsent(
      DexType type, Function<DexType, MethodNamingState<?>> f) {
    return states.computeIfAbsent(type, f);
  }

  private Function<DexProto, ?> getKeyTransform() {
    if (appView.options().getProguardConfiguration().isOverloadAggressively()) {
      // Use the full proto as key, hence reuse names based on full signature.
      return a -> a;
    } else {
      // Only use the parameters as key, hence do not reuse names on return type.
      return proto -> proto.parameters;
    }
  }

  static class MethodRenaming {

    final Map<DexMethod, DexString> renaming;
    final Map<DexCallSite, DexString> callSiteRenaming;

    private MethodRenaming(
        Map<DexMethod, DexString> renaming, Map<DexCallSite, DexString> callSiteRenaming) {
      this.renaming = renaming;
      this.callSiteRenaming = callSiteRenaming;
    }

    public static MethodRenaming empty() {
      return new MethodRenaming(ImmutableMap.of(), ImmutableMap.of());
    }
  }

  MethodRenaming computeRenaming(
      Collection<DexClass> interfaces, Set<DexCallSite> desugaredCallSites, Timing timing) {
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
        new InterfaceMethodNameMinifier(
            appView, desugaredCallSites, equivalence, frontierState, minifierState);
    timing.end();
    timing.begin("Phase 3");
    interfaceMethodNameMinifier.assignNamesToInterfaceMethods(timing, interfaces);
    timing.end();
    // Phase 4: Assign names top-down by traversing the subtype hierarchy.
    timing.begin("Phase 4");
    assignNamesToClassesMethods(appView.dexItemFactory().objectType);
    timing.end();

    return new MethodRenaming(renaming, interfaceMethodNameMinifier.getCallSiteRenamings());
  }

  private void assignNamesToClassesMethods(DexType type) {
    // The names for direct methods should not contribute to the naming of methods in sub-types:
    // class A {
    //   public int foo() { ... }   --> a
    //   private int bar() { ... }  --> b
    // }
    //
    // class B extends A {
    //   public int baz() { ... }   --> b
    // }
    //
    // A simple way to ensure this is to process virtual methods first and then direct methods.
    DexClass holder = appView.definitionFor(type);
    boolean shouldAssignName = holder != null && strategy.allowMemberRenaming(holder);
    if (shouldAssignName) {
      MethodNamingState<?> state =
          computeStateIfAbsent(type, k -> minifierState.getState(holder.superType).createChild());
      for (DexEncodedMethod method : holder.virtualMethodsSorted()) {
        assignNameToMethod(method, state, holder);
      }
      for (DexEncodedMethod method : holder.directMethodsSorted()) {
        assignNameToMethod(method, state, holder);
      }
    }

    appView.appInfo().forAllExtendsSubtypes(type, this::assignNamesToClassesMethods);
  }

  private void assignNameToMethod(
      DexEncodedMethod encodedMethod, MethodNamingState<?> state, DexClass holder) {
    if (encodedMethod.accessFlags.isConstructor()) {
      return;
    }
    DexMethod method = encodedMethod.method;
    DexString reservedName = strategy.getReservedNameOrDefault(encodedMethod, holder, method.name);
    if (state.isReserved(reservedName, method.proto)) {
      return;
    }
    DexString newName = state.assignNewNameFor(method, method.name, method.proto);
    addRenaming(encodedMethod, state, newName);
  }

  private void addRenaming(
      DexEncodedMethod encodedMethod, MethodNamingState<?> state, DexString renamedName) {
    if (encodedMethod.method.name != renamedName) {
      renaming.put(encodedMethod.method, renamedName);
    }
    if (!encodedMethod.accessFlags.isPrivate()) {
      state.addRenaming(encodedMethod.method.name, encodedMethod.method.proto, renamedName);
    }
  }

  private void reserveNamesInClasses() {
    reserveNamesInClasses(
        appView.dexItemFactory().objectType, appView.dexItemFactory().objectType, null);
  }

  private void reserveNamesInClasses(
      DexType type, DexType libraryFrontier, MethodNamingState<?> parent) {
    assert appView.isInterface(type).isFalse();

    MethodNamingState<?> state =
        frontierState.allocateNamingStateAndReserve(type, libraryFrontier, parent);

    // If this is a library class (or effectively a library class as it is missing) move the
    // frontier forward.
    DexClass holder = appView.definitionFor(type);
    for (DexType subtype : appView.appInfo().allExtendsSubtypes(type)) {
      reserveNamesInClasses(
          subtype, holder == null || holder.isNotProgramClass() ? subtype : libraryFrontier, state);
    }
  }

  class FrontierState {

    private final Map<DexType, DexType> frontiers = new IdentityHashMap<>();

    MethodNamingState<?> allocateNamingStateAndReserve(
        DexType type, DexType frontier, MethodNamingState<?> parent) {
      if (frontier != type) {
        frontiers.put(type, frontier);
      }

      MethodNamingState<?> state =
          computeStateIfAbsent(
              frontier,
              ignore ->
                  parent == null
                      ? MethodNamingState.createRoot(getKeyTransform(), strategy)
                      : parent.createChild());

      DexClass holder = appView.definitionFor(type);
      if (holder != null) {
        for (DexEncodedMethod method : shuffleMethods(holder.methods(), appView.options())) {
          DexString reservedName = strategy.getReservedNameOrDefault(method, holder, null);
          if (reservedName != null) {
            // If the mapping is incorrect, we might try to map a method to an existing name.
            // class A {
            //   int m1(String foo) { ... }
            // }
            //
            // class B extends A {
            //   int m2(String bar) { ... }
            // }
            //
            // and the following reservations (mapping):
            // A -> a:
            //   int m1(String foo) -> a
            // B -> b:
            //   int m2(String foo) -> a <- ERROR
            //
            // In the example, the mapping file specifies that A.m1() should become a.a() and that
            // B.m2() should become b.a(). This should not be allowed, since b.a() now overrides
            // a.a(), unlike in the original program.
            DexString previouslyReservedOriginalName =
                state.getReservedOriginalName(reservedName, method.method.proto);
            if (previouslyReservedOriginalName != null
                && previouslyReservedOriginalName != method.method.name) {
              strategy.reportReservationError(method.method, reservedName);
            }
            state.reserveName(reservedName, method.method.proto, method.method.name);
            globalState.reserveName(reservedName, method.method.proto, method.method.name);
            addRenaming(method, state, reservedName);
          }
        }
      }

      return state;
    }

    public DexType get(DexType type) {
      return frontiers.getOrDefault(type, type);
    }

    public DexType put(DexType type, DexType frontier) {
      assert frontier != type;
      return frontiers.put(type, frontier);
    }
  }

  // Shuffles the given methods if assertions are enabled and deterministic debugging is disabled.
  // Used to ensure that the generated output is deterministic.
  static Iterable<DexEncodedMethod> shuffleMethods(
      Iterable<DexEncodedMethod> methods, InternalOptions options) {
    return options.testing.irOrdering.order(methods);
  }
}
