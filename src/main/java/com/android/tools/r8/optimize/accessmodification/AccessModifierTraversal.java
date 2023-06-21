// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.accessmodification;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.optimize.argumentpropagation.utils.DepthFirstTopDownClassHierarchyTraversal;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepClassInfo;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.utils.collections.DexMethodSignatureMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

class AccessModifierTraversal extends DepthFirstTopDownClassHierarchyTraversal {

  private final AccessModifier accessModifier;
  private final AccessModifierNamingState namingState;

  private final Map<DexType, TraversalState> states = new IdentityHashMap<>();

  AccessModifierTraversal(
      AppView<AppInfoWithLiveness> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      AccessModifier accessModifier,
      AccessModifierNamingState namingState) {
    super(appView, immediateSubtypingInfo);
    this.accessModifier = accessModifier;
    this.namingState = namingState;
  }

  /** Predicate that specifies which program classes the depth-first traversal should start from. */
  @Override
  public boolean isRoot(DexProgramClass clazz) {
    return Iterables.all(
        clazz.allImmediateSupertypes(),
        supertype -> asProgramClassOrNull(appView.definitionFor(supertype)) == null);
  }

  /** Called when {@param clazz} is visited for the first time during the downwards traversal. */
  @Override
  public void visit(DexProgramClass clazz) {
    // TODO(b/279126633): Store a top down traversal state for the current class, which contains the
    //  protected and public method signatures when traversing downwards to enable publicizing of
    //  package private methods with illegal overrides.
    states.put(clazz.getType(), TopDownTraversalState.empty());
  }

  /** Called during backtracking when all subclasses of {@param clazz} have been processed. */
  @Override
  public void prune(DexProgramClass clazz) {
    // Remove the traversal state since all subclasses have now been processed.
    states.remove(clazz.getType());

    // Remove and join the bottom up traversal states of the subclasses.
    KeepClassInfo keepInfo = appView.getKeepInfo(clazz);
    InternalOptions options = appView.options();
    BottomUpTraversalState state =
        new BottomUpTraversalState(
            !keepInfo.isMinificationAllowed(options) && !keepInfo.isShrinkingAllowed(options));
    forEachSubClass(
        clazz,
        subclass -> {
          BottomUpTraversalState subState =
              MapUtils.removeOrDefault(states, subclass.getType(), BottomUpTraversalState.empty())
                  .asBottomUpTraversalState();
          state.add(subState);
        });

    // Apply access modification to the class and its members.
    accessModifier.processClass(clazz, namingState, state);

    // Add the methods of the current class.
    clazz.forEachProgramVirtualMethod(state::addMethod);

    // Store the bottom up traversal state for the current class.
    if (state.isEmpty()) {
      states.remove(clazz.getType());
    } else {
      states.put(clazz.getType(), state);
    }
  }

  abstract static class TraversalState {

    BottomUpTraversalState asBottomUpTraversalState() {
      return null;
    }

    TopDownTraversalState asTopDownTraversalState() {
      return null;
    }
  }

  // TODO(b/279126633): Collect the protected and public method signatures when traversing downwards
  //  to enable publicizing of package private methods with illegal overrides.
  static class TopDownTraversalState extends TraversalState {

    private static final TopDownTraversalState EMPTY = new TopDownTraversalState();

    static TopDownTraversalState empty() {
      return EMPTY;
    }

    @Override
    TopDownTraversalState asTopDownTraversalState() {
      return this;
    }

    boolean isEmpty() {
      return true;
    }
  }

  static class BottomUpTraversalState extends TraversalState {

    private static final BottomUpTraversalState EMPTY =
        new BottomUpTraversalState(DexMethodSignatureMap.empty());

    boolean isKeptOrHasKeptSubclass;

    // The set of non-private virtual methods below the current class.
    DexMethodSignatureMap<Set<String>> nonPrivateVirtualMethods;

    BottomUpTraversalState(boolean isKept) {
      this(DexMethodSignatureMap.create());
      this.isKeptOrHasKeptSubclass = isKept;
    }

    BottomUpTraversalState(DexMethodSignatureMap<Set<String>> packagePrivateMethods) {
      this.nonPrivateVirtualMethods = packagePrivateMethods;
    }

    static BottomUpTraversalState empty() {
      return EMPTY;
    }

    @Override
    BottomUpTraversalState asBottomUpTraversalState() {
      return this;
    }

    void add(BottomUpTraversalState backtrackingState) {
      isKeptOrHasKeptSubclass |= backtrackingState.isKeptOrHasKeptSubclass;
      backtrackingState.nonPrivateVirtualMethods.forEach(
          (methodSignature, packageDescriptors) ->
              this.nonPrivateVirtualMethods
                  .computeIfAbsent(methodSignature, ignoreKey(HashSet::new))
                  .addAll(packageDescriptors));
    }

    void addMethod(ProgramMethod method) {
      assert method.getDefinition().belongsToVirtualPool();
      nonPrivateVirtualMethods
          .computeIfAbsent(method.getMethodSignature(), ignoreKey(Sets::newIdentityHashSet))
          .add(method.getHolderType().getPackageDescriptor());
    }

    boolean hasIllegalOverrideOfPackagePrivateMethod(ProgramMethod method) {
      assert method.getAccessFlags().isPackagePrivate();
      String methodPackageDescriptor = method.getHolderType().getPackageDescriptor();
      return Iterables.any(
          nonPrivateVirtualMethods.getOrDefault(
              method.getMethodSignature(), Collections.emptySet()),
          methodOverridePackageDescriptor ->
              !methodOverridePackageDescriptor.equals(methodPackageDescriptor));
    }

    boolean isEmpty() {
      return nonPrivateVirtualMethods.isEmpty();
    }

    void setIsKeptOrHasKeptSubclass() {
      isKeptOrHasKeptSubclass = true;
    }
  }
}
