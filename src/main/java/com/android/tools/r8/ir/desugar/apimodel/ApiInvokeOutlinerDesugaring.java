// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.apimodel;

import static com.android.tools.r8.utils.AndroidApiLevelUtils.isApiLevelLessThanOrEqualToG;
import static com.android.tools.r8.utils.AndroidApiLevelUtils.isOutlinedAtSameOrLowerLevel;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfConstClass;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInstanceOf;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
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
import com.android.tools.r8.ir.desugar.DesugarDescription;
import com.android.tools.r8.ir.synthetic.CheckCastSourceCode;
import com.android.tools.r8.ir.synthetic.ConstClassSourceCode;
import com.android.tools.r8.ir.synthetic.FieldAccessorBuilder;
import com.android.tools.r8.ir.synthetic.ForwardMethodBuilder;
import com.android.tools.r8.ir.synthetic.InstanceOfSourceCode;
import com.android.tools.r8.synthesis.SyntheticMethodBuilder;
import com.android.tools.r8.utils.AndroidApiLevelUtils;
import com.android.tools.r8.utils.Pair;
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
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    ComputedApiLevel computedApiLevel =
        getComputedApiLevelInstructionOnHolderWithMinApi(instruction, context);
    if (appView.computedMinApiLevel().isGreaterThanOrEqualTo(computedApiLevel)) {
      return DesugarDescription.nothing();
    }
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (freshLocalProvider,
                localStackAllocator,
                desugaringInfo,
                eventConsumer,
                context1,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) ->
                desugarLibraryCall(
                    methodProcessingContext.createUniqueContext(),
                    instruction,
                    computedApiLevel,
                    dexItemFactory,
                    eventConsumer,
                    context))
        .build();
  }

  private ComputedApiLevel getComputedApiLevelInstructionOnHolderWithMinApi(
      CfInstruction instruction, ProgramMethod context) {
    if (context.getDefinition().isD8R8Synthesized()) {
      return appView.computedMinApiLevel();
    }
    DexReference reference = getReferenceFromInstruction(instruction);
    if (reference == null || !reference.getContextType().isClassType()) {
      return appView.computedMinApiLevel();
    }
    DexClass holder = appView.definitionFor(reference.getContextType());
    if (holder == null) {
      return appView.computedMinApiLevel();
    }
    Pair<DexClass, ComputedApiLevel> classAndApiLevel =
        reference.isDexType()
            ? Pair.create(
                holder,
                apiLevelCompute.computeApiLevelForLibraryReference(
                    reference, ComputedApiLevel.unknown()))
            : AndroidApiLevelUtils.findAndComputeApiLevelForLibraryDefinition(
                appView, appView.appInfoForDesugaring(), holder, reference.asDexMember());
    ComputedApiLevel referenceApiLevel = classAndApiLevel.getSecond();
    if (appView.computedMinApiLevel().isGreaterThanOrEqualTo(referenceApiLevel)
        || isApiLevelLessThanOrEqualToG(referenceApiLevel)
        || referenceApiLevel.isUnknownApiLevel()) {
      return appView.computedMinApiLevel();
    }
    assert referenceApiLevel.isKnownApiLevel();
    DexClass firstLibraryClass = classAndApiLevel.getFirst();
    if (firstLibraryClass == null || !firstLibraryClass.isLibraryClass()) {
      assert false : "When computed a known api level we should always have a library class";
      return appView.computedMinApiLevel();
    }
    // Check if this is already outlined.
    if (isOutlinedAtSameOrLowerLevel(context.getHolder(), referenceApiLevel)) {
      return appView.computedMinApiLevel();
    }
    // Check for protected or package private access flags before outlining.
    if (firstLibraryClass.isInterface()
        || instruction.isCheckCast()
        || instruction.isInstanceOf()
        || instruction.isConstClass()) {
      return referenceApiLevel;
    } else {
      DexEncodedMember<?, ?> definition =
          simpleLookupInClassHierarchy(
              firstLibraryClass.asLibraryClass(),
              reference.isDexMethod()
                  ? x -> x.lookupMethod(reference.asDexMethod())
                  : x -> x.lookupField(reference.asDexField()));
      return definition != null && definition.isPublic()
          ? referenceApiLevel
          : appView.computedMinApiLevel();
    }
  }

  private DexReference getReferenceFromInstruction(CfInstruction instruction) {
    if (instruction.isFieldInstruction()) {
      return instruction.asFieldInstruction().getField();
    } else if (instruction.isCheckCast()) {
      return instruction.asCheckCast().getType();
    } else if (instruction.isInstanceOf()) {
      return instruction.asInstanceOf().getType();
    } else if (instruction.isConstClass()) {
      return instruction.asConstClass().getType();
    } else if (instruction.isInvoke() && !instruction.asInvoke().isInvokeSpecial()) {
      return instruction.asInvoke().getMethod();
    } else {
      return null;
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
        || instruction.isInstanceOf()
        || instruction.isConstClass();
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
    DexReference reference = getReferenceFromInstruction(instruction);
    assert reference != null;
    DexClass holder = appView.definitionFor(reference.getContextType());
    assert holder != null;
    return appView
        .getSyntheticItems()
        .createMethod(
            kinds ->
                // We've already checked that the definition the reference is targeting is public
                // when computing the api-level for desugaring. We still have to ensure that the
                // class cannot be merged globally if it is package private.
                holder.isPublic()
                    ? kinds.API_MODEL_OUTLINE
                    : kinds.API_MODEL_OUTLINE_WITHOUT_GLOBAL_MERGING,
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
              } else if (instruction.isConstClass()) {
                setCodeForConstClass(syntheticMethodBuilder, instruction.asConstClass(), factory);
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

  private void setCodeForConstClass(
      SyntheticMethodBuilder methodBuilder, CfConstClass instruction, DexItemFactory factory) {
    DexClass target = appView.definitionFor(instruction.getType());
    assert target != null;
    methodBuilder
        .setProto(factory.createProto(factory.classType))
        .setCode(
            m ->
                ConstClassSourceCode.create(appView, m.getHolderType(), target.getType())
                    .generateCfCode());
  }

  private boolean verifyLibraryHolderAndInvoke(
      DexClass libraryHolder, DexMethod apiMethod, boolean isVirtualInvoke) {
    DexEncodedMethod libraryApiMethodDefinition = libraryHolder.lookupMethod(apiMethod);
    return libraryApiMethodDefinition == null
        || libraryApiMethodDefinition.isVirtualMethod() == isVirtualInvoke;
  }
}
