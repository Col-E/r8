// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionOrPhi;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.BranchSimplifier;
import com.android.tools.r8.ir.conversion.passes.TrivialCheckCastAndInstanceOfRemover;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.ir.optimize.Inliner;
import com.android.tools.r8.ir.optimize.InliningOracle;
import com.android.tools.r8.ir.optimize.classinliner.InlineCandidateProcessor.IllegalClassInlinerStateException;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.inliner.InliningIRProvider;
import com.android.tools.r8.ir.optimize.string.StringOptimizer;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.LazyBox;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class ClassInliner {

  enum EligibilityStatus {
    ELIGIBLE,
    NOT_ELIGIBLE
  }

  private final ConcurrentHashMap<DexClass, EligibilityStatus> knownClasses =
      new ConcurrentHashMap<>();

  // Process method code and inline eligible class instantiations, in short:
  //
  // - collect all 'new-instance' and 'static-get' instructions (called roots below) in
  // the original code. Note that class inlining, if happens, mutates code and may add
  // new root instructions. Processing them as well is possible, but does not seem to
  // bring much value.
  //
  // - for each 'new-instance' root we check if it is eligible for inlining, i.e:
  //     -> the class of the new instance is 'eligible' (see computeClassEligible(...))
  //     -> the instance is initialized with 'eligible' constructor (see comments in
  //        CodeRewriter::identifyClassInlinerEligibility(...))
  //     -> has only 'eligible' uses, i.e:
  //          * as a receiver of a field read/write for a field defined in same class
  //            as method.
  //          * as a receiver of virtual or interface call with single target being
  //            an eligible method according to identifyClassInlinerEligibility(...);
  //            NOTE: if method receiver is used as a return value, the method call
  //            should ignore return value
  //
  // - for each 'static-get' root we check if it is eligible for inlining, i.e:
  //     -> the class of the new instance is 'eligible' (see computeClassEligible(...))
  //        *and* has a trivial class constructor (see CoreRewriter::computeClassInitializerInfo
  //        and description in isClassAndUsageEligible(...)) initializing the field root reads
  //     -> has only 'eligible' uses, (see above)
  //
  // - inline eligible root instructions, i.e:
  //     -> force inline methods called on the instance (including the initializer);
  //        (this may introduce additional instance field reads/writes on the receiver)
  //     -> replace instance field reads with appropriate values calculated based on
  //        fields writes
  //     -> remove the call to superclass initializer (if root is 'new-instance')
  //     -> remove all field writes
  //     -> remove root instructions
  //
  // For example:
  //
  // Original code:
  //   class C {
  //     static class L {
  //       final int x;
  //       L(int x) {
  //         this.x = x;
  //       }
  //       int getX() {
  //         return x;
  //       }
  //     }
  //     static class F {
  //       final static F I = new F();
  //       int getX() {
  //         return 123;
  //       }
  //     }
  //     static int method1() {
  //       return new L(1).x;
  //     }
  //     static int method2() {
  //       return new L(1).getX();
  //     }
  //     static int method3() {
  //       return F.I.getX();
  //     }
  //   }
  //
  // Code after class C is 'inlined':
  //   class C {
  //     static int method1() {
  //       return 1;
  //     }
  //     static int method2() {
  //       return 1;
  //     }
  //     static int method3() {
  //       return 123;
  //     }
  //   }
  //
  public final void processMethodCode(
      AppView<AppInfoWithLiveness> appView,
      ProgramMethod method,
      IRCode code,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext,
      Inliner inliner,
      LazyBox<InliningOracle> defaultOracle) {

    // Collect all the new-instance and static-get instructions in the code before inlining.
    List<Instruction> roots =
        Lists.newArrayList(code.instructions(insn -> insn.isNewInstance() || insn.isStaticGet()));

    // We loop inlining iterations until there was no inlining, but still use same set
    // of roots to avoid infinite inlining. Looping makes possible for some roots to
    // become eligible after other roots are inlined.
    boolean anyInlinedGeneratedMessageLiteBuilders = false;
    boolean anyInlinedMethods = false;
    boolean repeat;
    do {
      repeat = false;

      Iterator<Instruction> rootsIterator = roots.iterator();
      while (rootsIterator.hasNext()) {
        Instruction root = rootsIterator.next();
        InlineCandidateProcessor processor =
            new InlineCandidateProcessor(
                appView,
                inliner,
                clazz -> isClassEligible(appView, clazz),
                methodProcessor,
                method,
                root);

        // Assess eligibility of instance and class.
        EligibilityStatus status = processor.isInstanceEligible();
        if (status != EligibilityStatus.ELIGIBLE) {
          // This root will never be inlined.
          rootsIterator.remove();
          continue;
        }
        status = processor.isClassAndUsageEligible();
        if (status != EligibilityStatus.ELIGIBLE) {
          // This root will never be inlined.
          rootsIterator.remove();
          continue;
        }

        // Assess users eligibility and compute inlining of direct calls and extra methods needed.
        InstructionOrPhi ineligibleUser = processor.areInstanceUsersEligible(defaultOracle);
        if (ineligibleUser != null) {
          // This root may succeed if users change in future.
          continue;
        }

        // Is inlining allowed.
        InliningIRProvider inliningIRProvider =
            new InliningIRProvider(
                appView, method, code, inliner.getLensCodeRewriter(), methodProcessor);
        ClassInlinerCostAnalysis costAnalysis =
            new ClassInlinerCostAnalysis(appView, inliningIRProvider, processor.getReceivers());
        if (costAnalysis.willExceedInstructionBudget(
            code,
            processor.getEligibleClass(),
            processor.getDirectInlinees(),
            processor.getIndirectInlinees())) {
          // This root is unlikely to be inlined in the future.
          rootsIterator.remove();
          continue;
        }

        if (appView.protoShrinker() != null && root.isNewInstance()) {
          DexType instantiatedType = root.asNewInstance().clazz;
          DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(instantiatedType));
          if (clazz != null
              && appView.protoShrinker().references.isGeneratedMessageLiteBuilder(clazz)) {
            anyInlinedGeneratedMessageLiteBuilders = true;
          }
        }

        // Inline the class instance.
        AffectedValues affectedValues = new AffectedValues();
        try {
          anyInlinedMethods |= processor.processInlining(code, affectedValues, inliningIRProvider);
        } catch (IllegalClassInlinerStateException e) {
          // We introduced a user that we cannot handle in the class inliner as a result of force
          // inlining. Abort gracefully from class inlining without removing the instance.
          //
          // Alternatively we would need to collect additional information about the behavior of
          // methods (which is bad for memory), or we would need to analyze the called methods
          // before inlining them. The latter could be good solution, since we are going to build IR
          // for the methods that need to be inlined anyway.
          assert false;
          anyInlinedMethods = true;
        }

        // Restore normality.
        code.removeAllDeadAndTrivialPhis(affectedValues);
        affectedValues.narrowingWithAssumeRemoval(appView, code);
        code.removeRedundantBlocks();
        assert code.isConsistentSSA(appView);
        rootsIterator.remove();
        repeat = true;
      }
    } while (repeat);

    if (anyInlinedGeneratedMessageLiteBuilders) {
      // Inline all calls to dynamicMethod() where the given MethodToInvoke is considered simple.
      appView.withGeneratedMessageLiteBuilderShrinker(
          shrinker ->
              shrinker.inlineCallsToDynamicMethod(
                  method, code, feedback, methodProcessor, inliner));
    }

    if (anyInlinedMethods) {
      // If a method was inlined we may be able to remove check-cast instructions because we may
      // have more information about the types of the arguments at the call site. This is
      // particularly important for bridge methods.
      new TrivialCheckCastAndInstanceOfRemover(appView)
          .run(code, methodProcessor, methodProcessingContext, Timing.empty());
      // If a method was inlined we may be able to prune additional branches.
      new BranchSimplifier(appView)
          .run(code, methodProcessor, methodProcessingContext, Timing.empty());
      // If a method was inlined we may see more trivial computation/conversion of String.
      new StringOptimizer(appView).run(code, Timing.empty());
    }
  }

  private EligibilityStatus isClassEligible(
      AppView<AppInfoWithLiveness> appView, DexProgramClass clazz) {
    EligibilityStatus eligible = knownClasses.get(clazz);
    if (eligible == null) {
      EligibilityStatus computed = computeClassEligible(appView, clazz);
      EligibilityStatus existing = knownClasses.putIfAbsent(clazz, computed);
      assert existing == null || existing == computed;
      eligible = existing == null ? computed : existing;
    }
    return eligible;
  }

  @SuppressWarnings("ReferenceEquality")
  // Class is eligible for this optimization. Eligibility implementation:
  //   - is not an abstract class or interface
  //   - does not declare finalizer
  //   - does not trigger any static initializers except for its own
  private EligibilityStatus computeClassEligible(
      AppView<AppInfoWithLiveness> appView, DexProgramClass clazz) {
    if (clazz == null
        || clazz.isAbstract()
        || clazz.isInterface()
        || !appView.appInfo().isClassInliningAllowed(clazz)) {
      return EligibilityStatus.NOT_ELIGIBLE;
    }

    // Class must not define finalizer.
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    for (DexEncodedMethod method : clazz.virtualMethods()) {
      if (method.getReference().name == dexItemFactory.finalizeMethodName
          && method.getReference().proto == dexItemFactory.objectMembers.finalize.proto) {
        return EligibilityStatus.NOT_ELIGIBLE;
      }
    }

    // Check for static initializers in this class or any of interfaces it implements.
    if (clazz.classInitializationMayHaveSideEffects(appView)) {
      return EligibilityStatus.NOT_ELIGIBLE;
    }

    if (!appView.testing().allowClassInliningOfSynthetics
        && appView.getSyntheticItems().isSyntheticClass(clazz)) {
      return EligibilityStatus.NOT_ELIGIBLE;
    }

    return EligibilityStatus.ELIGIBLE;
  }
}
