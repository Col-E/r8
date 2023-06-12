// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.bridgehoisting;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexInvokeVirtual;
import com.android.tools.r8.dex.code.DexInvokeVirtualRange;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.BottomUpClassHierarchyTraversal;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMember;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessInfoCollection;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeInfo;
import com.android.tools.r8.ir.optimize.info.bridge.VirtualBridgeInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.Timing;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

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

  // Structure that keeps track of the changes for construction of the Proguard map and
  // AppInfoWithLiveness maintenance.
  private final BridgeHoistingResult result;

  public BridgeHoisting(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.result = new BridgeHoistingResult(appView);
  }

  public void run(ExecutorService executorService, Timing timing) throws ExecutionException {
    timing.begin("Bridge hoisting");
    SubtypingInfo subtypingInfo = appView.appInfo().computeSubtypingInfo();
    BottomUpClassHierarchyTraversal.forProgramClasses(appView, subtypingInfo)
        .excludeInterfaces()
        .visit(appView.appInfo().classes(), clazz -> processClass(clazz, subtypingInfo));
    if (!result.isEmpty()) {
      BridgeHoistingLens lens = result.buildLens();
      appView.rewriteWithLens(lens, executorService, timing);

      // Record the invokes from the newly synthesized bridge methods in the method access info
      // collection.
      MethodAccessInfoCollection.Modifier methodAccessInfoCollectionModifier =
          appView.appInfo().getMethodAccessInfoCollection().modifier();
      result.forEachHoistedBridge(
          (bridge, bridgeInfo) -> {
            if (bridgeInfo.isVirtualBridgeInfo()) {
              DexMethod reference = bridgeInfo.asVirtualBridgeInfo().getInvokedMethod();
              methodAccessInfoCollectionModifier.registerInvokeVirtualInContext(reference, bridge);
            } else {
              assert false;
            }
          });
    }

    appView.notifyOptimizationFinishedForTesting();
    timing.end();
  }

  private void processClass(DexProgramClass clazz, SubtypingInfo subtypingInfo) {
    Set<DexType> subtypes = subtypingInfo.allImmediateSubtypes(clazz.type);
    Set<DexProgramClass> subclasses = new TreeSet<>(Comparator.comparing(DexClass::getType));
    for (DexType subtype : subtypes) {
      DexProgramClass subclass = asProgramClassOrNull(appView.definitionFor(subtype));
      if (subclass == null || !appView.testing().isEligibleForBridgeHoisting.test(subclass)) {
        continue;
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
        if (bridgeInfo != null && bridgeInfo.isVirtualBridgeInfo()) {
          candidates.add(equivalence.wrap(method.getReference()));
        }
      }
    }
    return candidates;
  }

  private void hoistBridgeIfPossible(
      DexMethod method, DexProgramClass clazz, Set<DexProgramClass> subclasses) {
    // If the method is defined on the parent class, we cannot hoist the bridge.
    // TODO(b/153147967): If the declared method is abstract, we could replace it by the bridge.
    //  Add a test.
    if (clazz.lookupProgramMethod(method) != null) {
      return;
    }

    // Bail out if the bridge is also declared in the parent class. In that case, hoisting would
    // change the behavior of calling the bridge on an instance of the parent class.
    MethodResolutionResult res =
        appView.appInfo().resolveMethodOnClass(clazz.getSuperType(), method);
    if (res.isSingleResolution()) {
      if (!res.getResolvedMethod().isAbstract()) {
        return;
      }
    } else if (res.isMultiMethodResolutionResult()) {
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
            appView.appInfo().resolveMethodOnClassLegacy(subclass, method).getSingleTarget();
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
      if (currentBridgeInfo == null || currentBridgeInfo.isDirectBridgeInfo()) {
        // This is not a bridge, so the method needs to remain on the subclass.
        continue;
      }

      assert currentBridgeInfo.isVirtualBridgeInfo();

      VirtualBridgeInfo currentVirtualBridgeInfo = currentBridgeInfo.asVirtualBridgeInfo();
      DexMethod invokedMethod = currentVirtualBridgeInfo.getInvokedMethod();

      if (!clazz.getType().isSamePackage(subclass.getType())) {
        DexEncodedMethod resolvedMethod =
            appView.appInfo().resolveMethodOnClass(clazz, invokedMethod).getSingleTarget();
        if (resolvedMethod == null || resolvedMethod.getAccessFlags().isPackagePrivate()) {
          // After hoisting this bridge would now dispatch to another method, namely the package
          // private method in the parent class.
          continue;
        }
      }

      Wrapper<DexMethod> wrapper = MethodSignatureEquivalence.get().wrap(invokedMethod);
      eligibleVirtualInvokeBridges
          .computeIfAbsent(wrapper, ignore -> new ArrayList<>())
          .add(subclass);
    }

    // Check if any bridges may be eligible for hoisting.
    if (eligibleVirtualInvokeBridges.isEmpty()) {
      return;
    }

    Entry<Wrapper<DexMethod>, List<DexProgramClass>> mostFrequentBridge =
        findMostFrequentBridge(eligibleVirtualInvokeBridges);
    assert mostFrequentBridge != null;
    DexMethod invokedMethod = mostFrequentBridge.getKey().get();
    List<DexProgramClass> eligibleSubclasses = mostFrequentBridge.getValue();

    // Choose one of the bridge definitions as the one that we will be moving to the superclass.
    List<ProgramMethod> eligibleBridgeMethods =
        getBridgesEligibleForHoisting(eligibleSubclasses, method);
    ProgramMethod representative = eligibleBridgeMethods.iterator().next();

    // Guard against accessibility issues.
    if (mayBecomeInaccessibleAfterHoisting(clazz, eligibleBridgeMethods, representative)) {
      return;
    }

    // Rewrite the invoke-virtual instruction to target the virtual method on the new holder class.
    // Otherwise the code might not type check.
    DexMethod methodToInvoke =
        appView.dexItemFactory().createMethod(clazz.type, invokedMethod.proto, invokedMethod.name);

    // The targeted method must be present on the new holder class for this to be feasible.
    MethodResolutionResult resolutionResult =
        appView.appInfo().resolveMethodOnClassLegacy(clazz, methodToInvoke);
    if (!resolutionResult.isSingleResolution()) {
      return;
    }

    // Now update the code of the bridge method chosen as representative.
    representative
        .setCode(createCodeForVirtualBridge(representative, methodToInvoke), appView);
    feedback.setBridgeInfo(representative.getDefinition(), new VirtualBridgeInfo(methodToInvoke));

    // Move the bridge method to the super class, and record this in the graph lens.
    DexMethod newMethodReference =
        appView.dexItemFactory().createMethod(clazz.type, method.proto, method.name);
    DexEncodedMethod newMethod =
        representative.getDefinition().toTypeSubstitutedMethod(newMethodReference);
    if (newMethod.getAccessFlags().isFinal()) {
      newMethod.getAccessFlags().demoteFromFinal();
    }
    clazz.addVirtualMethod(newMethod);
    result.move(
        Iterables.transform(eligibleBridgeMethods, DexClassAndMember::getReference),
        newMethodReference,
        representative.getReference());

    // Remove all of the bridges in the eligible subclasses.
    assert !appView.appInfo().isPinnedWithDefinitionLookup(method);
    for (DexProgramClass subclass : eligibleSubclasses) {
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

  private List<ProgramMethod> getBridgesEligibleForHoisting(
      Iterable<DexProgramClass> subclasses, DexMethod reference) {
    List<ProgramMethod> result = new ArrayList<>();
    for (DexProgramClass subclass : subclasses) {
      ProgramMethod method = subclass.lookupProgramMethod(reference);
      if (method != null) {
        result.add(method);
      }
    }
    assert !result.isEmpty();
    return result;
  }

  private boolean mayBecomeInaccessibleAfterHoisting(
      DexProgramClass clazz,
      List<ProgramMethod> eligibleBridgeMethods,
      ProgramMethod representative) {
    int representativeVisibility = representative.getAccessFlags().getVisibilityOrdinal();
    for (ProgramMethod eligibleBridgeMethod : eligibleBridgeMethods) {
      if (eligibleBridgeMethod.getAccessFlags().getVisibilityOrdinal()
          != representativeVisibility) {
        return true;
      }
      if (!clazz.getType().isSamePackage(eligibleBridgeMethod.getHolderType())
          && !eligibleBridgeMethod.getDefinition().isPublic()) {
        return true;
      }
    }
    return false;
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
    boolean modified = false;
    for (CfInstruction instruction : code.getInstructions()) {
      if (instruction.isInvoke() && instruction.asInvoke().getMethod() != methodToInvoke) {
        CfInvoke invoke = instruction.asInvoke();
        assert invoke.isInvokeVirtual();
        assert !invoke.isInterface();
        assert invoke.getMethod().match(methodToInvoke);
        newInstructions.add(new CfInvoke(invoke.getOpcode(), methodToInvoke, false));
        modified = true;
      } else {
        newInstructions.add(instruction);
      }
    }
    return modified
        ? new CfCode(
            methodToInvoke.holder,
            code.getMaxStack(),
            code.getMaxLocals(),
            newInstructions,
            code.getTryCatchRanges(),
            code.getLocalVariables())
        : code;
  }

  private DexCode createDexCodeForVirtualBridge(DexCode code, DexMethod methodToInvoke) {
    DexInstruction[] newInstructions = new DexInstruction[code.instructions.length];
    boolean modified = false;
    for (int i = 0; i < code.instructions.length; i++) {
      DexInstruction instruction = code.instructions[i];
      if (instruction.isInvokeVirtual()
          && instruction.asInvokeVirtual().getMethod() != methodToInvoke) {
        DexInvokeVirtual invoke = instruction.asInvokeVirtual();
        DexInvokeVirtual newInvoke =
            new DexInvokeVirtual(
                invoke.A, methodToInvoke, invoke.C, invoke.D, invoke.E, invoke.F, invoke.G);
        newInvoke.setOffset(invoke.getOffset());
        newInstructions[i] = newInvoke;
        modified = true;
      } else if (instruction.isInvokeVirtualRange()
          && instruction.asInvokeVirtualRange().getMethod() != methodToInvoke) {
        DexInvokeVirtualRange invoke = instruction.asInvokeVirtualRange();
        DexInvokeVirtualRange newInvoke =
            new DexInvokeVirtualRange(invoke.CCCC, invoke.AA, methodToInvoke);
        newInvoke.setOffset(invoke.getOffset());
        modified = true;
        newInstructions[i] = newInvoke;
      } else {
        newInstructions[i] = instruction;
      }
    }
    return modified ? code.withNewInstructions(newInstructions) : code;
  }
}
