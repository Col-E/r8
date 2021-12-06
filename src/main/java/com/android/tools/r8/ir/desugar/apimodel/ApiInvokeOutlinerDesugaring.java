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
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.google.common.collect.ImmutableList;
import java.util.Collection;

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
          instruction,
          computedApiLevel,
          dexItemFactory);
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
    DexClass clazz = appView.definitionFor(holderType);
    if (clazz == null || !clazz.isLibraryClass()) {
      return appView.computedMinApiLevel();
    }
    ComputedApiLevel apiLevel =
        apiLevelCompute.computeApiLevelForLibraryReference(
            cfInvoke.getMethod(), ComputedApiLevel.unknown());
    if (apiLevel.isGreaterThan(appView.computedMinApiLevel())) {
      ComputedApiLevel holderApiLevel =
          apiLevelCompute.computeApiLevelForLibraryReference(
              holderType, ComputedApiLevel.unknown());
      if (holderApiLevel.isGreaterThan(appView.computedMinApiLevel())) {
        // Do not outline where the holder is unknown or introduced later then min api.
        // TODO(b/208978971): Describe where mocking is done when landing.
        return appView.computedMinApiLevel();
      }
      return apiLevel;
    }
    return appView.computedMinApiLevel();
  }

  private Collection<CfInstruction> desugarLibraryCall(
      UniqueContext context,
      CfInstruction instruction,
      ComputedApiLevel computedApiLevel,
      DexItemFactory factory) {
    DexMethod method = instruction.asInvoke().getMethod();
    ProgramMethod programMethod = ensureOutlineMethod(context, method, computedApiLevel, factory);
    return ImmutableList.of(new CfInvoke(INVOKESTATIC, programMethod.getReference(), false));
  }

  private ProgramMethod ensureOutlineMethod(
      UniqueContext context,
      DexMethod apiMethod,
      ComputedApiLevel apiLevel,
      DexItemFactory factory) {
    DexClass libraryHolder = appView.definitionFor(apiMethod.getHolderType());
    assert libraryHolder != null;
    DexEncodedMethod libraryApiMethodDefinition = libraryHolder.lookupMethod(apiMethod);
    DexProto proto =
        factory.prependHolderToProtoIf(apiMethod, libraryApiMethodDefinition.isVirtualMethod());
    return appView
        .getSyntheticItems()
        .createMethod(
            SyntheticKind.API_MODEL_OUTLINE,
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
                        if (libraryApiMethodDefinition.isStatic()) {
                          return ForwardMethodBuilder.builder(factory)
                              .setStaticTarget(apiMethod, libraryHolder.isInterface())
                              .setStaticSource(apiMethod)
                              .build();
                        } else {
                          return ForwardMethodBuilder.builder(factory)
                              .setVirtualTarget(apiMethod, libraryHolder.isInterface())
                              .setNonStaticSource(apiMethod)
                              .build();
                        }
                      });
            });
  }
}
