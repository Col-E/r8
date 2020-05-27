// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.InvokeVirtual;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.BottomUpClassHierarchyTraversal;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense.NestedGraphLense;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeInfo;
import com.android.tools.r8.ir.optimize.info.bridge.VirtualBridgeInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

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

  private static final OptimizationFeedbackSimple feedback =
      OptimizationFeedbackSimple.getInstance();

  private final AppView<AppInfoWithLiveness> appView;

  // A lens that keeps track of the changes for construction of the Proguard map.
  private final BridgeHoistingLens.Builder lensBuilder = new BridgeHoistingLens.Builder();

  public BridgeHoisting(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public void run() {
    SubtypingInfo subtypingInfo = appView.appInfo().computeSubtypingInfo();
    BottomUpClassHierarchyTraversal.forProgramClasses(appView, subtypingInfo)
        .excludeInterfaces()
        .visit(appView.appInfo().classes(), clazz -> processClass(clazz, subtypingInfo));
    if (!lensBuilder.isEmpty()) {
      BridgeHoistingLens lens = lensBuilder.build(appView);
      boolean changed = appView.setGraphLense(lens);
      assert changed;
      appView.setAppInfo(
          appView.appInfo().rewrittenWithLens(appView.appInfo().app().asDirect(), lens));
    }
  }

  private void processClass(DexProgramClass clazz, SubtypingInfo subtypingInfo) {
    Set<DexType> subtypes = subtypingInfo.allImmediateSubtypes(clazz.type);
    Set<DexProgramClass> subclasses = new TreeSet<>((x, y) -> x.type.slowCompareTo(y.type));
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
        if (bridgeInfo != null) {
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

    // Go through each of the subclasses and find the bridges that can be hoisted. The bridge holder
    // classes are stored in buckets grouped by the behavior of the body of the bridge (which is
    // implicitly defined by the signature of the invoke-virtual instruction).
    Map<Wrapper<DexMethod>, List<DexProgramClass>> eligibleVirtualInvokeBridges = new HashMap<>();
    for (DexProgramClass subclass : subclasses) {
      DexEncodedMethod definition = subclass.lookupVirtualMethod(method);
      if (definition == null) {
        DexEncodedMethod resolutionTarget =
            appView.appInfo().resolveMethodOnClass(method, subclass).getSingleTarget();
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

        // Hoisting would change the program behavior.
        return;
      }

      BridgeInfo currentBridgeInfo = definition.getOptimizationInfo().getBridgeInfo();
      if (currentBridgeInfo == null) {
        // This is not a bridge, so the method needs to remain on the subclass.
        continue;
      }

      assert currentBridgeInfo.isVirtualBridgeInfo();

      VirtualBridgeInfo currentVirtualBridgeInfo = currentBridgeInfo.asVirtualBridgeInfo();
      DexMethod invokedMethod = currentVirtualBridgeInfo.getInvokedMethod();
      Wrapper<DexMethod> wrapper = MethodSignatureEquivalence.get().wrap(invokedMethod);
      eligibleVirtualInvokeBridges
          .computeIfAbsent(wrapper, ignore -> new ArrayList<>())
          .add(subclass);
    }

    // There should be at least one method that is eligible for hoisting.
    assert !eligibleVirtualInvokeBridges.isEmpty();

    Entry<Wrapper<DexMethod>, List<DexProgramClass>> mostFrequentBridge =
        findMostFrequentBridge(eligibleVirtualInvokeBridges);
    assert mostFrequentBridge != null;
    DexMethod invokedMethod = mostFrequentBridge.getKey().get();
    List<DexProgramClass> eligibleSubclasses = mostFrequentBridge.getValue();

    // Choose one of the bridge definitions as the one that we will be moving to the superclass.
    ProgramMethod representative = findRepresentative(eligibleSubclasses, method);

    // Guard against accessibility issues.
    if (mayBecomeInaccessibleAfterHoisting(clazz, representative)) {
      return;
    }

    // Rewrite the invoke-virtual instruction to target the virtual method on the new holder class.
    // Otherwise the code might not type check.
    DexMethod methodToInvoke =
        appView.dexItemFactory().createMethod(clazz.type, invokedMethod.proto, invokedMethod.name);

    // The targeted method must be present on the new holder class for this to be feasible.
    ResolutionResult resolutionResult =
        appView.appInfo().resolveMethodOnClass(methodToInvoke, clazz);
    if (!resolutionResult.isSingleResolution()) {
      return;
    }

    // Now update the code of the bridge method chosen as representative.
    representative
        .getDefinition()
        .setCode(createCodeForVirtualBridge(representative, methodToInvoke), appView);
    feedback.setBridgeInfo(representative.getDefinition(), new VirtualBridgeInfo(methodToInvoke));

    // Move the bridge method to the super class, and record this in the graph lens.
    DexMethod newMethodReference =
        appView.dexItemFactory().createMethod(clazz.type, method.proto, method.name);
    DexEncodedMethod newMethod =
        representative.getDefinition().toTypeSubstitutedMethod(newMethodReference);
    clazz.addVirtualMethod(newMethod);
    lensBuilder.move(representative.getReference(), newMethodReference);

    // Remove all of the bridges in the eligible subclasses.
    for (DexProgramClass subclass : eligibleSubclasses) {
      assert !appView.appInfo().isPinned(method);
      DexEncodedMethod removed = subclass.removeMethod(method);
      assert removed != null;
    }
  }

  private static Entry<Wrapper<DexMethod>, List<DexProgramClass>> findMostFrequentBridge(
      Map<Wrapper<DexMethod>, List<DexProgramClass>> eligibleVirtualInvokeBridges) {
    Entry<Wrapper<DexMethod>, List<DexProgramClass>> result = null;
    for (Entry<Wrapper<DexMethod>, List<DexProgramClass>> candidate :
        eligibleVirtualInvokeBridges.entrySet()) {
      List<DexProgramClass> eligibleSubclassesCandidate = candidate.getValue();
      if (result == null || eligibleSubclassesCandidate.size() > result.getValue().size()) {
        result = candidate;
      }
    }
    return result;
  }

  private ProgramMethod findRepresentative(Iterable<DexProgramClass> subclasses, DexMethod method) {
    for (DexProgramClass subclass : subclasses) {
      DexEncodedMethod definition = subclass.lookupVirtualMethod(method);
      if (definition != null) {
        return new ProgramMethod(subclass, definition);
      }
    }
    throw new Unreachable();
  }

  private boolean mayBecomeInaccessibleAfterHoisting(
      DexProgramClass clazz, ProgramMethod representative) {
    if (clazz.type.isSamePackage(representative.getHolder().type)) {
      return false;
    }
    return !representative.getDefinition().isPublic();
  }

  private Code createCodeForVirtualBridge(ProgramMethod representative, DexMethod methodToInvoke) {
    Code code = representative.getDefinition().getCode();
    if (code.isCfCode()) {
      return createCfCodeForVirtualBridge(code.asCfCode(), methodToInvoke);
    }
    if (code.isDexCode()) {
      return createDexCodeForVirtualBridge(code.asDexCode(), methodToInvoke);
    }
    throw new Unreachable("Unexpected code object of type " + code.getClass().getTypeName());
  }

  private CfCode createCfCodeForVirtualBridge(CfCode code, DexMethod methodToInvoke) {
    List<CfInstruction> newInstructions = new ArrayList<>();
    for (CfInstruction instruction : code.getInstructions()) {
      if (instruction.isInvoke()) {
        CfInvoke invoke = instruction.asInvoke();
        assert invoke.isInvokeVirtual();
        assert !invoke.isInterface();
        assert invoke.getMethod().match(methodToInvoke);
        newInstructions.add(new CfInvoke(invoke.getOpcode(), methodToInvoke, false));
      } else {
        newInstructions.add(instruction);
      }
    }
    return new CfCode(
        methodToInvoke.holder,
        code.getMaxStack(),
        code.getMaxLocals(),
        newInstructions,
        code.getTryCatchRanges(),
        code.getLocalVariables());
  }

  private DexCode createDexCodeForVirtualBridge(DexCode code, DexMethod methodToInvoke) {
    Instruction[] newInstructions = new Instruction[code.instructions.length];
    for (int i = 0; i < code.instructions.length; i++) {
      Instruction instruction = code.instructions[i];
      if (instruction.isInvokeVirtual()) {
        InvokeVirtual invoke = instruction.asInvokeVirtual();
        InvokeVirtual newInvoke =
            new InvokeVirtual(
                invoke.A, methodToInvoke, invoke.C, invoke.D, invoke.E, invoke.F, invoke.G);
        newInvoke.setOffset(invoke.getOffset());
        newInstructions[i] = newInvoke;
      } else {
        newInstructions[i] = instruction;
      }
    }
    return new DexCode(
        code.registerSize,
        code.incomingRegisterSize,
        code.outgoingRegisterSize,
        newInstructions,
        code.tries,
        code.handlers,
        code.getDebugInfo());
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
