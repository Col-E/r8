// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.apimodel;

import static org.objectweb.asm.Opcodes.INVOKESTATIC;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInstanceOf;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.contexts.CompilationContext.UniqueContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringCollection;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.FreshLocalProvider;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.android.tools.r8.ir.synthetic.CheckCastSourceCode;
import com.android.tools.r8.ir.synthetic.FieldAccessorBuilder;
import com.android.tools.r8.ir.synthetic.ForwardMethodBuilder;
import com.android.tools.r8.ir.synthetic.InstanceOfSourceCode;
import com.android.tools.r8.synthesis.SyntheticMethodBuilder;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.TraversalContinuation;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * This desugaring will outline calls to library methods that are introduced after the min-api
 * level. For classes introduced after the min-api level see ApiReferenceStubber.
 */
public class ApiInvokeOutlinerDesugaring implements CfInstructionDesugaring {

  private final AppView<?> appView;
  private final AndroidApiLevelCompute apiLevelCompute;

  private final DexTypeList objectParams;

  public ApiInvokeOutlinerDesugaring(AppView<?> appView, AndroidApiLevelCompute apiLevelCompute) {
    this.appView = appView;
    this.apiLevelCompute = apiLevelCompute;
    this.objectParams = DexTypeList.create(new DexType[] {appView.dexItemFactory().objectType});
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
    ComputedApiLevel computedApiLevel =
        getComputedApiLevelInstructionOnHolderWithMinApi(instruction, context);
    if (computedApiLevel.isGreaterThan(appView.computedMinApiLevel())) {
      return desugarLibraryCall(
          methodProcessingContext.createUniqueContext(),
          instruction,
          computedApiLevel,
          dexItemFactory,
          eventConsumer,
          context);
    }
    return null;
  }

  @Override
  public boolean needsDesugaring(CfInstruction instruction, ProgramMethod context) {
    return getComputedApiLevelInstructionOnHolderWithMinApi(instruction, context)
        .isGreaterThan(appView.computedMinApiLevel());
  }

  private ComputedApiLevel getComputedApiLevelInstructionOnHolderWithMinApi(
      CfInstruction instruction, ProgramMethod context) {
    if (context.getDefinition().isD8R8Synthesized()) {
      return appView.computedMinApiLevel();
    }
    DexReference reference;
    if (instruction.isInvoke()) {
      CfInvoke cfInvoke = instruction.asInvoke();
      if (cfInvoke.isInvokeSpecial()) {
        return appView.computedMinApiLevel();
      }
      reference = cfInvoke.getMethod();
    } else if (instruction.isFieldInstruction()) {
      reference = instruction.asFieldInstruction().getField();
    } else if (instruction.isCheckCast()) {
      reference = instruction.asCheckCast().getType();
    } else if (instruction.isInstanceOf()) {
      reference = instruction.asInstanceOf().getType();
    } else {
      return appView.computedMinApiLevel();
    }
    if (!reference.getContextType().isClassType()) {
      return appView.computedMinApiLevel();
    }
    DexClass holder = appView.definitionFor(reference.getContextType());
    if (holder == null || !holder.isLibraryClass()) {
      return appView.computedMinApiLevel();
    }
    ComputedApiLevel referenceApiLevel =
        apiLevelCompute.computeApiLevelForLibraryReference(reference, ComputedApiLevel.unknown());
    if (appView.computedMinApiLevel().isGreaterThanOrEqualTo(referenceApiLevel)
        || isApiLevelLessThanOrEqualTo9(referenceApiLevel)
        || referenceApiLevel.isUnknownApiLevel()) {
      return appView.computedMinApiLevel();
    }
    // Check for protected or package private access flags before outlining.
    if (holder.isInterface() || instruction.isCheckCast() || instruction.isInstanceOf()) {
      return referenceApiLevel;
    } else {
      DexEncodedMember<?, ?> definition =
          simpleLookupInClassHierarchy(
              holder.asLibraryClass(),
              reference.isDexMethod()
                  ? x -> x.lookupMethod(reference.asDexMethod())
                  : x -> x.lookupField(reference.asDexField()));
      return definition != null && definition.isPublic()
          ? referenceApiLevel
          : appView.computedMinApiLevel();
    }
  }

  private DexEncodedMember<?, ?> simpleLookupInClassHierarchy(
      DexLibraryClass holder, Function<DexClass, DexEncodedMember<?, ?>> lookup) {
    DexEncodedMember<?, ?> result = lookup.apply(holder);
    if (result != null) {
      return result;
    }
    TraversalContinuation<DexEncodedMember<?, ?>, ?> traversalResult =
        appView
            .appInfoForDesugaring()
            .traverseSuperClasses(
                holder,
                (ignored, superClass, ignored_) -> {
                  DexEncodedMember<?, ?> definition = lookup.apply(superClass);
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
      CfInstruction instruction,
      ComputedApiLevel computedApiLevel,
      DexItemFactory factory,
      ApiInvokeOutlinerDesugaringEventConsumer eventConsumer,
      ProgramMethod context) {
    assert instruction.isInvoke()
        || instruction.isFieldInstruction()
        || instruction.isCheckCast()
        || instruction.isInstanceOf();
    ProgramMethod outlinedMethod =
        ensureOutlineMethod(uniqueContext, instruction, computedApiLevel, factory, context);
    eventConsumer.acceptOutlinedMethod(outlinedMethod, context);
    return ImmutableList.of(new CfInvoke(INVOKESTATIC, outlinedMethod.getReference(), false));
  }

  private ProgramMethod ensureOutlineMethod(
      UniqueContext context,
      CfInstruction instruction,
      ComputedApiLevel apiLevel,
      DexItemFactory factory,
      ProgramMethod programContext) {
    return appView
        .getSyntheticItems()
        .createMethod(
            kinds -> kinds.API_MODEL_OUTLINE,
            context,
            appView,
            syntheticMethodBuilder -> {
              syntheticMethodBuilder
                  .setAccessFlags(
                      MethodAccessFlags.builder()
                          .setPublic()
                          .setSynthetic()
                          .setStatic()
                          .setBridge()
                          .build())
                  .setApiLevelForDefinition(apiLevel)
                  .setApiLevelForCode(apiLevel);
              if (instruction.isInvoke()) {
                setCodeForInvoke(syntheticMethodBuilder, instruction.asInvoke(), factory);
              } else if (instruction.isCheckCast()) {
                setCodeForCheckCast(syntheticMethodBuilder, instruction.asCheckCast(), factory);
              } else if (instruction.isInstanceOf()) {
                setCodeForInstanceOf(syntheticMethodBuilder, instruction.asInstanceOf(), factory);
              } else {
                assert instruction.isCfInstruction();
                setCodeForFieldInstruction(
                    syntheticMethodBuilder,
                    instruction.asFieldInstruction(),
                    factory,
                    programContext);
              }
            });
  }

  private void setCodeForInvoke(
      SyntheticMethodBuilder methodBuilder, CfInvoke invoke, DexItemFactory factory) {
    DexMethod method = invoke.getMethod();
    DexClass libraryHolder = appView.definitionFor(method.getHolderType());
    assert libraryHolder != null;
    boolean isVirtualMethod = invoke.isInvokeVirtual() || invoke.isInvokeInterface();
    assert verifyLibraryHolderAndInvoke(libraryHolder, method, isVirtualMethod);
    DexProto proto = factory.prependHolderToProtoIf(method, isVirtualMethod);
    methodBuilder
        .setProto(proto)
        .setCode(
            m -> {
              if (isVirtualMethod) {
                return ForwardMethodBuilder.builder(factory)
                    .setVirtualTarget(method, libraryHolder.isInterface())
                    .setNonStaticSource(method)
                    .build();
              } else {
                return ForwardMethodBuilder.builder(factory)
                    .setStaticTarget(method, libraryHolder.isInterface())
                    .setStaticSource(method)
                    .build();
              }
            });
  }

  private void setCodeForFieldInstruction(
      SyntheticMethodBuilder methodBuilder,
      CfFieldInstruction fieldInstruction,
      DexItemFactory factory,
      ProgramMethod programContext) {
    DexField field = fieldInstruction.getField();
    DexClass libraryHolder = appView.definitionFor(field.getHolderType());
    assert libraryHolder != null;
    boolean isInstance =
        fieldInstruction.isInstanceFieldPut() || fieldInstruction.isInstanceFieldGet();
    // Outlined field references will return a value if getter and only takes arguments if
    // instance or if put or two arguments if both.
    DexType returnType = fieldInstruction.isFieldGet() ? field.getType() : factory.voidType;
    List<DexType> parameters = new ArrayList<>();
    if (isInstance) {
      parameters.add(libraryHolder.getType());
    }
    if (fieldInstruction.isFieldPut()) {
      parameters.add(field.getType());
    }
    methodBuilder
        .setProto(factory.createProto(returnType, parameters))
        .setCode(
            m ->
                FieldAccessorBuilder.builder()
                    .applyIf(
                        isInstance,
                        thenConsumer -> thenConsumer.setInstanceField(field),
                        elseConsumer -> elseConsumer.setStaticField(field))
                    .applyIf(
                        fieldInstruction.isFieldGet(),
                        FieldAccessorBuilder::setGetter,
                        FieldAccessorBuilder::setSetter)
                    .setSourceMethod(programContext.getReference())
                    .build());
  }

  private void setCodeForCheckCast(
      SyntheticMethodBuilder methodBuilder, CfCheckCast instruction, DexItemFactory factory) {
    DexClass target = appView.definitionFor(instruction.getType());
    assert target != null;
    methodBuilder
        .setProto(factory.createProto(target.getType(), objectParams))
        .setCode(
            m ->
                CheckCastSourceCode.create(appView, m.getHolderType(), target.getType())
                    .generateCfCode());
  }

  private void setCodeForInstanceOf(
      SyntheticMethodBuilder methodBuilder, CfInstanceOf instruction, DexItemFactory factory) {
    DexClass target = appView.definitionFor(instruction.getType());
    assert target != null;
    methodBuilder
        .setProto(factory.createProto(factory.booleanType, objectParams))
        .setCode(
            m ->
                InstanceOfSourceCode.create(appView, m.getHolderType(), target.getType())
                    .generateCfCode());
  }

  private boolean verifyLibraryHolderAndInvoke(
      DexClass libraryHolder, DexMethod apiMethod, boolean isVirtualInvoke) {
    DexEncodedMethod libraryApiMethodDefinition = libraryHolder.lookupMethod(apiMethod);
    return libraryApiMethodDefinition == null
        || libraryApiMethodDefinition.isVirtualMethod() == isVirtualInvoke;
  }
}
