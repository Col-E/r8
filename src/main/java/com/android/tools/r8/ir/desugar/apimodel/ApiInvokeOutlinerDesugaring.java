// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.apimodel;

import static org.objectweb.asm.Opcodes.INVOKESTATIC;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.contexts.CompilationContext.UniqueContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringCollection;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.FreshLocalProvider;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.android.tools.r8.ir.synthetic.ForwardMethodBuilder;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.TraversalContinuation;
import com.google.common.collect.ImmutableList;
import java.util.Collection;

/**
 * This desugaring will outline calls to library methods that are introduced after the min-api
 * level. For classes introduced after the min-api level see ApiReferenceStubber.
 */
public class ApiInvokeOutlinerDesugaring implements CfInstructionDesugaring {

  private final AppView<?> appView;
  private final AndroidApiLevelCompute apiLevelCompute;

  public ApiInvokeOutlinerDesugaring(AppView<?> appView, AndroidApiLevelCompute apiLevelCompute) {
    this.appView = appView;
    this.apiLevelCompute = apiLevelCompute;
  }

  @Override
  public Collection<CfInstruction> desugarInstruction(
      CfInstruction instruction,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      CfInstructionDesugaringCollection desugaringCollection,
      DexItemFactory dexItemFactory) {
    ComputedApiLevel computedApiLevel = getComputedApiLevelForMethodOnHolderWithMinApi(instruction);
    if (computedApiLevel.isGreaterThan(appView.computedMinApiLevel())) {
      return desugarLibraryCall(
          methodProcessingContext.createUniqueContext(),
          instruction.asInvoke(),
          computedApiLevel,
          dexItemFactory,
          eventConsumer,
          context);
    }
    return null;
  }

  @Override
  public boolean needsDesugaring(CfInstruction instruction, ProgramMethod context) {
    if (context.getDefinition().isD8R8Synthesized()) {
      return false;
    }
    return getComputedApiLevelForMethodOnHolderWithMinApi(instruction)
        .isGreaterThan(appView.computedMinApiLevel());
  }

  private ComputedApiLevel getComputedApiLevelForMethodOnHolderWithMinApi(
      CfInstruction instruction) {
    if (!instruction.isInvoke()) {
      return appView.computedMinApiLevel();
    }
    CfInvoke cfInvoke = instruction.asInvoke();
    if (cfInvoke.isInvokeSpecial()) {
      return appView.computedMinApiLevel();
    }
    DexType holderType = cfInvoke.getMethod().getHolderType();
    if (!holderType.isClassType()) {
      return appView.computedMinApiLevel();
    }
    DexClass holder = appView.definitionFor(holderType);
    if (holder == null || !holder.isLibraryClass()) {
      return appView.computedMinApiLevel();
    }
    ComputedApiLevel methodApiLevel =
        apiLevelCompute.computeApiLevelForLibraryReference(
            cfInvoke.getMethod(), ComputedApiLevel.unknown());
    if (appView.computedMinApiLevel().isGreaterThanOrEqualTo(methodApiLevel)
        || isApiLevelLessThanOrEqualTo9(methodApiLevel)
        || methodApiLevel.isUnknownApiLevel()) {
      return appView.computedMinApiLevel();
    }
    // Check for protected or package private access flags before outlining.
    if (holder.isInterface()) {
      return methodApiLevel;
    } else {
      DexEncodedMethod methodDefinition =
          simpleLookupInClassHierarchy(holder.asLibraryClass(), cfInvoke.getMethod());
      return methodDefinition != null && methodDefinition.isPublic()
          ? methodApiLevel
          : appView.computedMinApiLevel();
    }
  }

  private DexEncodedMethod simpleLookupInClassHierarchy(DexLibraryClass holder, DexMethod method) {
    DexEncodedMethod result = holder.lookupMethod(method);
    if (result != null) {
      return result;
    }
    TraversalContinuation<DexEncodedMethod, ?> traversalResult =
        appView
            .appInfoForDesugaring()
            .traverseSuperClasses(
                holder,
                (ignored, superClass, ignored_) -> {
                  DexEncodedMethod definition = superClass.lookupMethod(method);
                  if (definition != null) {
                    return TraversalContinuation.doBreak(definition);
                  }
                  return TraversalContinuation.doContinue();
                });
    return traversalResult.isBreak() ? traversalResult.asBreak().getValue() : null;
  }

  private boolean isApiLevelLessThanOrEqualTo9(ComputedApiLevel apiLevel) {
    return apiLevel.isKnownApiLevel()
        && apiLevel.asKnownApiLevel().getApiLevel().isLessThanOrEqualTo(AndroidApiLevel.G);
  }

  private Collection<CfInstruction> desugarLibraryCall(
      UniqueContext uniqueContext,
      CfInvoke invoke,
      ComputedApiLevel computedApiLevel,
      DexItemFactory factory,
      ApiInvokeOutlinerDesugaringEventConsumer eventConsumer,
      ProgramMethod context) {
    DexMethod method = invoke.getMethod();
    ProgramMethod outlinedMethod =
        ensureOutlineMethod(uniqueContext, method, computedApiLevel, factory, invoke);
    eventConsumer.acceptOutlinedMethod(outlinedMethod, context);
    return ImmutableList.of(new CfInvoke(INVOKESTATIC, outlinedMethod.getReference(), false));
  }

  private ProgramMethod ensureOutlineMethod(
      UniqueContext context,
      DexMethod apiMethod,
      ComputedApiLevel apiLevel,
      DexItemFactory factory,
      CfInvoke invoke) {
    DexClass libraryHolder = appView.definitionFor(apiMethod.getHolderType());
    assert libraryHolder != null;
    boolean isVirtualMethod = invoke.isInvokeVirtual() || invoke.isInvokeInterface();
    assert verifyLibraryHolderAndInvoke(libraryHolder, apiMethod, isVirtualMethod);
    DexProto proto = factory.prependHolderToProtoIf(apiMethod, isVirtualMethod);
    return appView
        .getSyntheticItems()
        .createMethod(
            kinds -> kinds.API_MODEL_OUTLINE,
            context,
            appView,
            syntheticMethodBuilder -> {
              syntheticMethodBuilder
                  .setProto(proto)
                  .setAccessFlags(
                      MethodAccessFlags.builder()
                          .setPublic()
                          .setSynthetic()
                          .setStatic()
                          .setBridge()
                          .build())
                  .setApiLevelForDefinition(apiLevel)
                  .setApiLevelForCode(apiLevel)
                  .setCode(
                      m -> {
                        if (isVirtualMethod) {
                          return ForwardMethodBuilder.builder(factory)
                              .setVirtualTarget(apiMethod, libraryHolder.isInterface())
                              .setNonStaticSource(apiMethod)
                              .build();
                        } else {
                          return ForwardMethodBuilder.builder(factory)
                              .setStaticTarget(apiMethod, libraryHolder.isInterface())
                              .setStaticSource(apiMethod)
                              .build();
                        }
                      });
            });
  }

  private boolean verifyLibraryHolderAndInvoke(
      DexClass libraryHolder, DexMethod apiMethod, boolean isVirtualInvoke) {
    DexEncodedMethod libraryApiMethodDefinition = libraryHolder.lookupMethod(apiMethod);
    return libraryApiMethodDefinition == null
        || libraryApiMethodDefinition.isVirtualMethod() == isVirtualInvoke;
  }
}
