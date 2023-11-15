// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.callgraph;

import static com.android.tools.r8.graph.DexClassAndMethod.asProgramMethodOrNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DefaultUseRegistry;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.LookupResult;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public class InvokeExtractor<N extends NodeBase<N>> extends DefaultUseRegistry<ProgramMethod> {

  protected final AppView<AppInfoWithLiveness> appViewWithLiveness;
  protected final N currentMethod;
  protected final Function<ProgramMethod, N> nodeFactory;
  protected final Map<DexMethod, ProgramMethodSet> possibleProgramTargetsCache;
  protected final Predicate<ProgramMethod> targetTester;

  public InvokeExtractor(
      AppView<AppInfoWithLiveness> appViewWithLiveness,
      N currentMethod,
      Function<ProgramMethod, N> nodeFactory,
      Map<DexMethod, ProgramMethodSet> possibleProgramTargetsCache,
      Predicate<ProgramMethod> targetTester) {
    super(appViewWithLiveness, currentMethod.getProgramMethod());
    this.appViewWithLiveness = appViewWithLiveness;
    this.currentMethod = currentMethod;
    this.nodeFactory = nodeFactory;
    this.possibleProgramTargetsCache = possibleProgramTargetsCache;
    this.targetTester = targetTester;
  }

  protected void addCallEdge(ProgramMethod callee, boolean likelySpuriousCallEdge) {
    if (!targetTester.test(callee)) {
      return;
    }
    if (callee.getDefinition().isAbstract()) {
      // Not a valid target.
      return;
    }
    if (callee.getDefinition().isNative()) {
      // We don't care about calls to native methods.
      return;
    }
    if (!appViewWithLiveness
        .getKeepInfo(callee)
        .isOptimizationAllowed(appViewWithLiveness.options())) {
      // Since the callee is kept and optimizations are disallowed, we cannot inline it into the
      // caller, and we also cannot collect any optimization info for the method. Therefore, we
      // drop the call edge to reduce the total number of call graph edges, which should lead to
      // fewer call graph cycles.
      return;
    }
    nodeFactory.apply(callee).addCallerConcurrently(currentMethod, likelySpuriousCallEdge);
  }

  private void processInvoke(InvokeType originalType, DexMethod originalMethod) {
    ProgramMethod context = currentMethod.getProgramMethod();
    MethodLookupResult result =
        appViewWithLiveness
            .graphLens()
            .lookupMethod(originalMethod, context.getReference(), originalType, getCodeLens());
    DexMethod method = result.getReference();
    InvokeType type = result.getType();
    MethodResolutionResult resolutionResult =
        type.isInterface() || type.isVirtual()
            ? appViewWithLiveness.appInfo().resolveMethodLegacy(method, type.isInterface())
            : appViewWithLiveness.appInfo().unsafeResolveMethodDueToDexFormatLegacy(method);
    if (!resolutionResult.isSingleResolution()) {
      return;
    }
    if (type.isInterface() || type.isVirtual()) {
      // For virtual and interface calls add all potential targets that could be called.
      processInvokeWithDynamicDispatch(type, resolutionResult.getResolutionPair(), context);
    } else {
      ProgramMethod singleTarget =
          asProgramMethodOrNull(
              appViewWithLiveness
                  .appInfo()
                  .lookupSingleTarget(
                      appViewWithLiveness,
                      type,
                      method,
                      resolutionResult.asSingleResolution(),
                      context,
                      appViewWithLiveness));
      if (singleTarget != null) {
        processSingleTarget(singleTarget, context);
      }
    }
  }

  @SuppressWarnings("ReferenceEquality")
  protected void processSingleTarget(ProgramMethod singleTarget, ProgramMethod context) {
    assert !context.getDefinition().isBridge()
        || singleTarget.getDefinition() != context.getDefinition();
    addCallEdge(singleTarget, false);
  }

  protected void processInvokeWithDynamicDispatch(
      InvokeType type, DexClassAndMethod encodedTarget, ProgramMethod context) {
    DexMethod target = encodedTarget.getReference();
    DexClass clazz = encodedTarget.getHolder();
    if (!appViewWithLiveness.options().testing.addCallEdgesForLibraryInvokes) {
      if (clazz.isLibraryClass()) {
        // Likely to have many possible targets.
        return;
      }
    }

    boolean isInterface = type == InvokeType.INTERFACE;
    ProgramMethodSet possibleProgramTargets =
        possibleProgramTargetsCache.computeIfAbsent(
            target,
            method -> {
              MethodResolutionResult resolution =
                  appViewWithLiveness.appInfo().resolveMethodLegacy(method, isInterface);
              if (resolution.isVirtualTarget()) {
                LookupResult lookupResult =
                    resolution.lookupVirtualDispatchTargets(
                        context.getHolder(), appViewWithLiveness);
                if (lookupResult.isLookupResultSuccess()) {
                  ProgramMethodSet targets = ProgramMethodSet.create();
                  lookupResult
                      .asLookupResultSuccess()
                      .forEach(
                          lookupMethodTarget -> {
                            DexClassAndMethod methodTarget = lookupMethodTarget.getTarget();
                            if (methodTarget.isProgramMethod()) {
                              targets.add(methodTarget.asProgramMethod());
                            }
                          },
                          lambdaTarget -> {
                            // The call target will ultimately be the implementation method.
                            DexClassAndMethod implementationMethod =
                                lambdaTarget.getImplementationMethod();
                            if (implementationMethod.isProgramMethod()) {
                              targets.add(implementationMethod.asProgramMethod());
                            }
                          });
                  return targets;
                }
              }
              return null;
            });
    if (possibleProgramTargets != null) {
      boolean likelySpuriousCallEdge =
          possibleProgramTargets.size()
              >= appViewWithLiveness.options().callGraphLikelySpuriousCallEdgeThreshold;
      for (ProgramMethod possibleTarget : possibleProgramTargets) {
        addCallEdge(possibleTarget, likelySpuriousCallEdge);
      }
    }
  }

  @Override
  public void registerCallSite(DexCallSite callSite) {
    registerMethodHandle(
        callSite.bootstrapMethod, MethodHandleUse.NOT_ARGUMENT_TO_LAMBDA_METAFACTORY);
  }

  @Override
  public void registerInvokeDirect(DexMethod method) {
    processInvoke(InvokeType.DIRECT, method);
  }

  @Override
  public void registerInvokeInterface(DexMethod method) {
    processInvoke(InvokeType.INTERFACE, method);
  }

  @Override
  public void registerInvokeStatic(DexMethod method) {
    processInvoke(InvokeType.STATIC, method);
  }

  @Override
  public void registerInvokeSuper(DexMethod method) {
    processInvoke(InvokeType.SUPER, method);
  }

  @Override
  public void registerInvokeVirtual(DexMethod method) {
    processInvoke(InvokeType.VIRTUAL, method);
  }
}
