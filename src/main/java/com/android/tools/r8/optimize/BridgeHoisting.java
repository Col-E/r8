// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.BottomUpClassHierarchyTraversal;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense.NestedGraphLense;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeInfo;
import com.android.tools.r8.ir.optimize.info.bridge.VirtualBridgeInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.SetUtils;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import java.util.HashSet;
import java.util.Set;

/**
 * An optimization pass that hoists bridges upwards with the purpose of sharing redundant bridge
 * methods.
 *
 * <p>Example: <code>
 *   class A {
 *     void m() { ... }
 *   }
 *   class B1 extends A {
 *     void bridge() { m(); }
 *   }
 *   class B2 extends A {
 *     void bridge() { m(); }
 *   }
 * </code> Is transformed into: <code>
 *   class A {
 *     void m() { ... }
 *     void bridge() { m(); }
 *   }
 *   class B1 extends A {}
 *   class B2 extends A {}
 * </code>
 */
public class BridgeHoisting {

  private final AppView<AppInfoWithLiveness> appView;

  // A lens that keeps track of the changes for construction of the Proguard map.
  private final BridgeHoistingLens.Builder lensBuilder = new BridgeHoistingLens.Builder();

  public BridgeHoisting(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public void run() {
    BottomUpClassHierarchyTraversal.forProgramClasses(appView)
        .excludeInterfaces()
        .visit(appView.appInfo().classes(), this::processClass);
    if (!lensBuilder.isEmpty()) {
      BridgeHoistingLens lens = lensBuilder.build(appView);
      boolean changed = appView.setGraphLense(lens);
      assert changed;
      appView.setAppInfo(
          appView.appInfo().rewrittenWithLens(appView.appInfo().app().asDirect(), lens));
    }
  }

  private void processClass(DexProgramClass clazz) {
    Set<DexType> subtypes = appView.appInfo().allImmediateSubtypes(clazz.type);
    Set<DexProgramClass> subclasses = SetUtils.newIdentityHashSet(subtypes.size());
    for (DexType subtype : subtypes) {
      DexProgramClass subclass = asProgramClassOrNull(appView.definitionFor(subtype));
      if (subclass == null) {
        return;
      }
      subclasses.add(subclass);
    }
    for (Wrapper<DexMethod> candidate : getCandidatesForHoisting(subclasses)) {
      hoistBridgeIfPossible(candidate.get(), clazz, subclasses);
    }
  }

  private Set<Wrapper<DexMethod>> getCandidatesForHoisting(Set<DexProgramClass> subclasses) {
    Equivalence<DexMethod> equivalence = MethodSignatureEquivalence.get();
    Set<Wrapper<DexMethod>> candidates = new HashSet<>();
    for (DexProgramClass subclass : subclasses) {
      for (DexEncodedMethod method : subclass.virtualMethods()) {
        BridgeInfo bridgeInfo = method.getOptimizationInfo().getBridgeInfo();
        // TODO(b/153147967): Even if the bridge is not targeting a method in the superclass, it may
        //  be possible to rewrite the bridge to target a method in the superclass, such that we can
        //  hoist it. Add a test.
        if (bridgeInfo != null && bridgeIsTargetingMethodInSuperclass(subclass, bridgeInfo)) {
          candidates.add(equivalence.wrap(method.method));
        }
      }
    }
    return candidates;
  }

  /**
   * Returns true if the bridge method is referencing a method in the superclass of {@param holder}.
   * If this is not the case, we cannot hoist the bridge method, as that would lead to a type error:
   * <code>
   *   class A {
   *     void bridge() {
   *       v0 <- Argument
   *       invoke-virtual {v0}, void B.m() // <- not valid
   *       Return
   *     }
   *   }
   *   class B extends A {
   *     void m() {
   *       ...
   *     }
   *   }
   * </code>
   */
  private boolean bridgeIsTargetingMethodInSuperclass(
      DexProgramClass holder, BridgeInfo bridgeInfo) {
    if (bridgeInfo.isVirtualBridgeInfo()) {
      VirtualBridgeInfo virtualBridgeInfo = bridgeInfo.asVirtualBridgeInfo();
      DexMethod invokedMethod = virtualBridgeInfo.getInvokedMethod();
      assert !appView.appInfo().isStrictSubtypeOf(invokedMethod.holder, holder.type);
      if (invokedMethod.holder == holder.type) {
        return false;
      }
      assert appView.appInfo().isStrictSubtypeOf(holder.type, invokedMethod.holder);
      return true;
    }
    assert false;
    return false;
  }

  private void hoistBridgeIfPossible(
      DexMethod method, DexProgramClass clazz, Set<DexProgramClass> subclasses) {
    // If the method is defined on the parent class, we cannot hoist the bridge.
    // TODO(b/153147967): If the declared method is abstract, we could replace it by the bridge.
    //  Add a test.
    if (clazz.lookupMethod(method) != null) {
      return;
    }

    // Go through each of the subclasses and bail-out if each subclass does not declare the same
    // bridge.
    BridgeInfo firstBridgeInfo = null;
    for (DexProgramClass subclass : subclasses) {
      DexEncodedMethod definition = subclass.lookupVirtualMethod(method);
      if (definition == null) {
        DexEncodedMethod resolutionTarget =
            appView.appInfo().resolveMethodOnClass(subclass, method).getSingleTarget();
        if (resolutionTarget == null || resolutionTarget.isAbstract()) {
          // The fact that this class does not declare the bridge (or the bridge is abstract) should
          // not prevent us from hoisting the bridge.
          //
          // Strictly speaking, there could be an invoke instruction that targets the bridge on this
          // subclass and fails with an AbstractMethodError or a NoSuchMethodError in the input
          // program. After hoisting the bridge to the superclass such an instruction would no
          // longer fail with an error in the generated program.
          //
          // If this ever turns out be an issue, it would be possible to track if there is an invoke
          // instruction targeting the bridge on this subclass that fails in the Enqueuer, but this
          // should never be the case in practice.
          continue;
        }
        return;
      }

      BridgeInfo currentBridgeInfo = definition.getOptimizationInfo().getBridgeInfo();
      if (currentBridgeInfo == null) {
        return;
      }

      if (firstBridgeInfo == null) {
        firstBridgeInfo = currentBridgeInfo;
      } else if (!currentBridgeInfo.hasSameTarget(firstBridgeInfo)) {
        return;
      }
    }

    // If we reached this point, it is because all of the subclasses define the same bridge.
    assert firstBridgeInfo != null;

    // Choose one of the bridge definitions as the one that we will be moving to the superclass.
    ProgramMethod representative = findRepresentative(subclasses, method);
    assert representative != null;

    // Guard against accessibility issues.
    if (mayBecomeInaccessibleAfterHoisting(clazz, representative)) {
      return;
    }

    // Move the bridge method to the super class, and record this in the graph lens.
    DexMethod newMethod =
        appView.dexItemFactory().createMethod(clazz.type, method.proto, method.name);
    clazz.addVirtualMethod(representative.getMethod().toTypeSubstitutedMethod(newMethod));
    lensBuilder.move(representative.getMethod().method, newMethod);

    // Remove all of the bridges in the subclasses.
    for (DexProgramClass subclass : subclasses) {
      DexEncodedMethod removed = subclass.removeMethod(method);
      assert removed == null || !appView.appInfo().isPinned(removed.method);
    }
  }

  private ProgramMethod findRepresentative(Iterable<DexProgramClass> subclasses, DexMethod method) {
    for (DexProgramClass subclass : subclasses) {
      DexEncodedMethod definition = subclass.lookupVirtualMethod(method);
      if (definition != null) {
        return new ProgramMethod(subclass, definition);
      }
    }
    return null;
  }

  private boolean mayBecomeInaccessibleAfterHoisting(
      DexProgramClass clazz, ProgramMethod representative) {
    if (clazz.type.isSamePackage(representative.getHolder().type)) {
      return false;
    }
    return !representative.getMethod().isPublic();
  }

  static class BridgeHoistingLens extends NestedGraphLense {

    public BridgeHoistingLens(
        AppView<?> appView, BiMap<DexMethod, DexMethod> originalMethodSignatures) {
      super(
          ImmutableMap.of(),
          ImmutableMap.of(),
          ImmutableMap.of(),
          null,
          originalMethodSignatures,
          appView.graphLense(),
          appView.dexItemFactory());
    }

    @Override
    public boolean isLegitimateToHaveEmptyMappings() {
      return true;
    }

    static class Builder {

      private final BiMap<DexMethod, DexMethod> originalMethodSignatures = HashBiMap.create();

      public boolean isEmpty() {
        return originalMethodSignatures.isEmpty();
      }

      public void move(DexMethod from, DexMethod to) {
        originalMethodSignatures.forcePut(to, originalMethodSignatures.getOrDefault(from, from));
      }

      public BridgeHoistingLens build(AppView<?> appView) {
        assert !isEmpty();
        return new BridgeHoistingLens(appView, originalMethodSignatures);
      }
    }
  }
}
