// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.callgraph;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.GraphLens.MethodLookupResult;
import com.android.tools.r8.graph.LookupResult;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.function.Predicate;

class InvokeExtractor extends UseRegistry<ProgramMethod> {

  private final CallGraphBuilderBase callGraphBuilderBase;
  private final Node currentMethod;
  private final Predicate<ProgramMethod> targetTester;

  InvokeExtractor(
      CallGraphBuilderBase callGraphBuilderBase,
      Node currentMethod,
      Predicate<ProgramMethod> targetTester) {
    super(callGraphBuilderBase.appView, currentMethod.getProgramMethod());
    this.callGraphBuilderBase = callGraphBuilderBase;
    this.currentMethod = currentMethod;
    this.targetTester = targetTester;
  }

  private void addClassInitializerTarget(DexProgramClass clazz) {
    assert clazz != null;
    if (clazz.hasClassInitializer()) {
      addCallEdge(clazz.getProgramClassInitializer(), false);
    }
  }

  private void addClassInitializerTarget(DexType type) {
    assert type.isClassType();
    DexProgramClass clazz = asProgramClassOrNull(callGraphBuilderBase.appView.definitionFor(type));
    if (clazz != null) {
      addClassInitializerTarget(clazz);
    }
  }

  private void addCallEdge(ProgramMethod callee, boolean likelySpuriousCallEdge) {
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
    if (!callGraphBuilderBase
        .appView
        .getKeepInfo(callee)
        .isInliningAllowed(callGraphBuilderBase.appView.options())) {
      // Since the callee is kept and optimizations are disallowed, we cannot inline it into the
      // caller, and we also cannot collect any optimization info for the method. Therefore, we
      // drop the call edge to reduce the total number of call graph edges, which should lead to
      // fewer call graph cycles.
      return;
    }
    callGraphBuilderBase
        .getOrCreateNode(callee)
        .addCallerConcurrently(currentMethod, likelySpuriousCallEdge);
  }

  private void addFieldReadEdge(ProgramMethod writer) {
    assert !writer.getDefinition().isAbstract();
    if (!targetTester.test(writer)) {
      return;
    }
    callGraphBuilderBase.getOrCreateNode(writer).addReaderConcurrently(currentMethod);
  }

  private void processInvoke(Invoke.Type originalType, DexMethod originalMethod) {
    ProgramMethod context = currentMethod.getProgramMethod();
    MethodLookupResult result =
        callGraphBuilderBase
            .appView
            .graphLens()
            .lookupMethod(
                originalMethod,
                context.getReference(),
                originalType,
                callGraphBuilderBase.codeLens);
    DexMethod method = result.getReference();
    Invoke.Type type = result.getType();
    if (type == Invoke.Type.INTERFACE || type == Invoke.Type.VIRTUAL) {
      // For virtual and interface calls add all potential targets that could be called.
      MethodResolutionResult resolutionResult =
          callGraphBuilderBase
              .appView
              .appInfo()
              .resolveMethod(method, type == Invoke.Type.INTERFACE);
      DexEncodedMethod target = resolutionResult.getSingleTarget();
      if (target != null) {
        processInvokeWithDynamicDispatch(type, target, context);
      }
    } else {
      ProgramMethod singleTarget =
          callGraphBuilderBase
              .appView
              .appInfo()
              .lookupSingleProgramTarget(type, method, context, callGraphBuilderBase.appView);
      if (singleTarget != null) {
        assert !context.getDefinition().isBridge()
            || singleTarget.getDefinition() != context.getDefinition();
        // For static invokes, the class could be initialized.
        if (type.isStatic()) {
          addClassInitializerTarget(singleTarget.getHolder());
        }
        addCallEdge(singleTarget, false);
      }
    }
  }

  private void processInvokeWithDynamicDispatch(
      Invoke.Type type, DexEncodedMethod encodedTarget, ProgramMethod context) {
    DexMethod target = encodedTarget.getReference();
    DexClass clazz = callGraphBuilderBase.appView.definitionFor(target.holder);
    if (clazz == null) {
      assert false : "Unable to lookup holder of `" + target.toSourceString() + "`";
      return;
    }

    if (!callGraphBuilderBase.appView.options().testing.addCallEdgesForLibraryInvokes) {
      if (clazz.isLibraryClass()) {
        // Likely to have many possible targets.
        return;
      }
    }

    boolean isInterface = type == Invoke.Type.INTERFACE;
    ProgramMethodSet possibleProgramTargets =
        callGraphBuilderBase.possibleProgramTargetsCache.computeIfAbsent(
            target,
            method -> {
              MethodResolutionResult resolution =
                  callGraphBuilderBase.appView.appInfo().resolveMethod(method, isInterface);
              if (resolution.isVirtualTarget()) {
                LookupResult lookupResult =
                    resolution.lookupVirtualDispatchTargets(
                        context.getHolder(), callGraphBuilderBase.appView.appInfo());
                if (lookupResult.isLookupResultSuccess()) {
                  ProgramMethodSet targets = ProgramMethodSet.create();
                  lookupResult
                      .asLookupResultSuccess()
                      .forEach(
                          methodTarget -> {
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
              >= callGraphBuilderBase.appView.options().callGraphLikelySpuriousCallEdgeThreshold;
      for (ProgramMethod possibleTarget : possibleProgramTargets) {
        addCallEdge(possibleTarget, likelySpuriousCallEdge);
      }
    }
  }

  private void processFieldRead(DexField reference) {
    if (!callGraphBuilderBase.includeFieldReadWriteEdges) {
      return;
    }

    DexField rewrittenReference =
        callGraphBuilderBase
            .appView
            .graphLens()
            .lookupField(reference, callGraphBuilderBase.codeLens);
    if (!rewrittenReference.getHolderType().isClassType()) {
      return;
    }

    ProgramField field =
        callGraphBuilderBase.appView.appInfo().resolveField(rewrittenReference).getProgramField();
    if (field == null || callGraphBuilderBase.appView.appInfo().isPinned(field)) {
      return;
    }

    // Each static field access implicitly triggers the class initializer.
    if (field.getAccessFlags().isStatic()) {
      addClassInitializerTarget(field.getHolder());
    }

    FieldAccessInfo fieldAccessInfo =
        callGraphBuilderBase.fieldAccessInfoCollection.get(field.getReference());
    if (fieldAccessInfo != null && fieldAccessInfo.hasKnownWriteContexts()) {
      if (fieldAccessInfo.getNumberOfWriteContexts() == 1) {
        fieldAccessInfo.forEachWriteContext(this::addFieldReadEdge);
      }
    }
  }

  private void processFieldWrite(DexField reference) {
    if (!callGraphBuilderBase.includeFieldReadWriteEdges) {
      return;
    }

    DexField rewrittenReference =
        callGraphBuilderBase
            .appView
            .graphLens()
            .lookupField(reference, callGraphBuilderBase.codeLens);
    if (!rewrittenReference.getHolderType().isClassType()) {
      return;
    }

    ProgramField field =
        callGraphBuilderBase.appView.appInfo().resolveField(rewrittenReference).getProgramField();
    if (field == null || callGraphBuilderBase.appView.appInfo().isPinned(field)) {
      return;
    }

    // Each static field access implicitly triggers the class initializer.
    if (field.getAccessFlags().isStatic()) {
      addClassInitializerTarget(field.getHolder());
    }
  }

  private void processInitClass(DexType type) {
    DexType rewrittenType = callGraphBuilderBase.appView.graphLens().lookupType(type);
    DexProgramClass clazz =
        asProgramClassOrNull(callGraphBuilderBase.appView.definitionFor(rewrittenType));
    if (clazz == null) {
      assert false;
      return;
    }
    addClassInitializerTarget(clazz);
  }

  @Override
  public GraphLens getCodeLens() {
    return callGraphBuilderBase.codeLens;
  }

  @Override
  public void registerInitClass(DexType clazz) {
    processInitClass(clazz);
  }

  @Override
  public void registerInvokeVirtual(DexMethod method) {
    processInvoke(Invoke.Type.VIRTUAL, method);
  }

  @Override
  public void registerInvokeDirect(DexMethod method) {
    processInvoke(Invoke.Type.DIRECT, method);
  }

  @Override
  public void registerInvokeStatic(DexMethod method) {
    processInvoke(Invoke.Type.STATIC, method);
  }

  @Override
  public void registerInvokeInterface(DexMethod method) {
    processInvoke(Invoke.Type.INTERFACE, method);
  }

  @Override
  public void registerInvokeSuper(DexMethod method) {
    processInvoke(Invoke.Type.SUPER, method);
  }

  @Override
  public void registerInstanceFieldRead(DexField field) {
    processFieldRead(field);
  }

  @Override
  public void registerInstanceFieldWrite(DexField field) {
    processFieldWrite(field);
  }

  @Override
  public void registerNewInstance(DexType type) {
    if (type.isClassType()) {
      addClassInitializerTarget(type);
    }
  }

  @Override
  public void registerStaticFieldRead(DexField field) {
    processFieldRead(field);
  }

  @Override
  public void registerStaticFieldWrite(DexField field) {
    processFieldWrite(field);
  }

  @Override
  public void registerTypeReference(DexType type) {}

  @Override
  public void registerInstanceOf(DexType type) {}

  @Override
  public void registerCallSite(DexCallSite callSite) {
    registerMethodHandle(
        callSite.bootstrapMethod, MethodHandleUse.NOT_ARGUMENT_TO_LAMBDA_METAFACTORY);
  }
}
