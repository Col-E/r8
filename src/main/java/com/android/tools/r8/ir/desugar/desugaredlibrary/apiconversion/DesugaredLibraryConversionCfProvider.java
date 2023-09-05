// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion;

import static com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryAPIConverter.methodWithVivifiedTypeInSignature;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryAPIConverter.vivifiedTypeFor;

import com.android.tools.r8.cf.code.CfArrayLoad;
import com.android.tools.r8.cf.code.CfArrayStore;
import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNewArray;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.contexts.CompilationContext.MainThreadContext;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.contexts.CompilationContext.UniqueContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.FreshLocalProvider;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryWrapperSynthesizerEventConsumer.DesugaredLibraryAPICallbackSynthesizorEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryWrapperSynthesizerEventConsumer.DesugaredLibraryAPIConverterEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryWrapperSynthesizerEventConsumer.DesugaredLibraryClasspathWrapperSynthesizeEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryWrapperSynthesizerEventConsumer.DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer;
import com.android.tools.r8.ir.synthetic.apiconverter.APIConversionCfCodeProvider;
import com.android.tools.r8.ir.synthetic.apiconverter.EqualsCfCodeProvider;
import com.android.tools.r8.ir.synthetic.apiconverter.HashCodeCfCodeProvider;
import com.android.tools.r8.utils.OptionalBool;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.objectweb.asm.Opcodes;

public class DesugaredLibraryConversionCfProvider {

  private final AppView<?> appView;
  private final DexItemFactory factory;
  private final DesugaredLibraryWrapperSynthesizer wrapperSynthesizer;

  public DesugaredLibraryConversionCfProvider(
      AppView<?> appView, DesugaredLibraryWrapperSynthesizer wrapperSynthesizer) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    this.wrapperSynthesizer = wrapperSynthesizer;
  }

  public DexEncodedMethod generateWrapperConversionWithoutCode(
      DexMethod method, DexField wrapperField) {
    DexMethod methodToInstall =
        methodWithVivifiedTypeInSignature(method, wrapperField.getHolderType(), appView);
    return wrapperSynthesizer.newSynthesizedMethod(methodToInstall, null);
  }

  public DexEncodedMethod generateWrapperConversion(
      DexMethod method,
      DexField wrapperField,
      DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer eventConsumer,
      Supplier<UniqueContext> contextSupplier) {
    DexClass holderClass = appView.definitionFor(method.getHolderType());
    assert holderClass != null || appView.options().isDesugaredLibraryCompilation();
    boolean isInterface = holderClass == null || holderClass.isInterface();
    ProgramMethod context = resolveContext(method, isInterface);
    DexMethod returnConversion =
        computeReturnConversion(method, true, eventConsumer, context, contextSupplier);
    DexMethod[] parameterConversions =
        computeParameterConversions(method, false, eventConsumer, context, contextSupplier);
    DexMethod methodToInstall =
        convertedMethod(
            method, false, returnConversion, parameterConversions, wrapperField.getHolderType());
    CfCode cfCode =
        new APIConversionCfCodeProvider(
                appView,
                wrapperField.getHolderType(),
                method,
                isInterface,
                returnConversion,
                parameterConversions,
                wrapperField)
            .generateCfCode();
    return wrapperSynthesizer.newSynthesizedMethod(methodToInstall, cfCode);
  }

  public DexEncodedMethod generateWrapperHashCode(DexField wrapperField) {
    return wrapperSynthesizer.newSynthesizedMethod(
        appView
            .dexItemFactory()
            .createMethod(
                wrapperField.getHolderType(),
                appView.dexItemFactory().createProto(appView.dexItemFactory().intType),
                appView.dexItemFactory().hashCodeMethodName),
        new HashCodeCfCodeProvider(appView, wrapperField.getHolderType(), wrapperField)
            .generateCfCode());
  }

  public DexEncodedMethod generateWrapperEquals(DexField wrapperField) {
    return wrapperSynthesizer.newSynthesizedMethod(
        appView
            .dexItemFactory()
            .createMethod(
                wrapperField.getHolderType(),
                appView
                    .dexItemFactory()
                    .createProto(
                        appView.dexItemFactory().booleanType, appView.dexItemFactory().objectType),
                appView.dexItemFactory().equalsMethodName),
        new EqualsCfCodeProvider(appView, wrapperField.getHolderType(), wrapperField)
            .generateCfCode());
  }

  public DexEncodedMethod generateVivifiedWrapperConversionWithoutCode(
      DexMethod method, DexField wrapperField) {
    DexMethod methodToInstall =
        factory.createMethod(wrapperField.getHolderType(), method.proto, method.name);
    return wrapperSynthesizer.newSynthesizedMethod(methodToInstall, null);
  }

  public DexEncodedMethod generateVivifiedWrapperConversion(
      DexMethod method,
      DexField wrapperField,
      DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer eventConsumer,
      Supplier<UniqueContext> contextSupplier) {
    DexMethod methodToInstall =
        factory.createMethod(wrapperField.getHolderType(), method.proto, method.name);
    DexClass holderClass = appView.definitionFor(method.getHolderType());
    boolean isInterface;
    if (holderClass == null) {
      assert appView
          .options()
          .machineDesugaredLibrarySpecification
          .isEmulatedInterfaceRewrittenType(method.getHolderType());
      isInterface = true;
    } else {
      isInterface = holderClass.isInterface();
    }
    ProgramMethod context = resolveContext(method, isInterface);
    DexMethod returnConversion =
        computeReturnConversion(method, false, eventConsumer, context, contextSupplier);
    DexMethod[] parameterConversions =
        computeParameterConversions(method, true, eventConsumer, context, contextSupplier);
    DexType newHolder =
        appView.typeRewriter.hasRewrittenType(method.getHolderType(), appView)
            ? vivifiedTypeFor(method.getHolderType(), appView)
            : method.getHolderType();
    DexMethod forwardMethod =
        convertedMethod(method, true, returnConversion, parameterConversions, newHolder);
    CfCode cfCode =
        new APIConversionCfCodeProvider(
                appView,
                wrapperField.getHolderType(),
                forwardMethod,
                isInterface,
                returnConversion,
                parameterConversions,
                wrapperField)
            .generateCfCode();
    return wrapperSynthesizer.newSynthesizedMethod(methodToInstall, cfCode);
  }

  public ProgramMethod generateCallbackConversion(
      ProgramMethod method,
      DesugaredLibraryAPICallbackSynthesizorEventConsumer eventConsumer,
      MainThreadContext context) {
    DexProgramClass clazz = method.getHolder();
    DexMethod returnConversion =
        computeReturnConversion(
            method.getReference(),
            true,
            eventConsumer,
            method,
            () -> context.createUniqueContext(clazz));
    DexMethod[] parameterConversions =
        computeParameterConversions(
            method.getReference(),
            false,
            eventConsumer,
            method,
            () -> context.createUniqueContext(clazz));
    DexMethod methodToInstall =
        convertedMethod(method.getReference(), false, returnConversion, parameterConversions);
    CfCode cfCode =
        new APIConversionCfCodeProvider(
                appView,
                method.getHolderType(),
                method.getReference(),
                clazz.isInterface(),
                returnConversion,
                parameterConversions)
            .generateCfCode();
    DexEncodedMethod newMethod = wrapperSynthesizer.newSynthesizedMethod(methodToInstall, cfCode);
    newMethod.setCode(cfCode, DexEncodedMethod.NO_PARAMETER_INFO);
    if (method.getDefinition().isLibraryMethodOverride().isTrue()) {
      newMethod.setLibraryMethodOverride(OptionalBool.TRUE);
    }
    ProgramMethod callback = newMethod.asProgramMethod(clazz);
    assert eventConsumer != null;
    eventConsumer.acceptAPIConversionCallback(callback, method);
    return callback;
  }

  public ProgramMethod generateOutlinedAPIConversion(
      CfInvoke invoke,
      DesugaredLibraryAPIConverterEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext) {
    DexMethod method = invoke.getMethod();
    DexProto newProto = factory.prependHolderToProtoIf(method, !invoke.isInvokeStatic());
    DexMethod returnConversion =
        computeReturnConversion(
            method, false, eventConsumer, context, methodProcessingContext::createUniqueContext);
    DexMethod[] parameterConversions =
        computeParameterConversions(
            method, true, eventConsumer, context, methodProcessingContext::createUniqueContext);
    ProgramMethod outline =
        appView
            .getSyntheticItems()
            .createMethod(
                kinds -> kinds.API_CONVERSION,
                methodProcessingContext.createUniqueContext(),
                appView,
                builder ->
                    builder
                        .setProto(newProto)
                        .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                        // Will be traced by the enqueuer.
                        .disableAndroidApiLevelCheck()
                        .setCode(
                            methodSignature ->
                                new APIConversionCfCodeProvider(
                                        appView,
                                        methodSignature.holder,
                                        convertedMethod(
                                            method, true, returnConversion, parameterConversions),
                                        invoke.isInterface(),
                                        returnConversion,
                                        parameterConversions,
                                        invoke.getOpcode())
                                    .generateCfCode()));
    eventConsumer.acceptAPIConversionOutline(outline, context);
    return outline;
  }

  public Collection<CfInstruction> generateInlinedAPIConversion(
      CfInvoke invoke,
      MethodProcessingContext methodProcessingContext,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context) {

    DexMethod invokedMethod = invoke.getMethod();
    DexMethod returnConversion =
        computeReturnConversion(
            invokedMethod,
            false,
            eventConsumer,
            context,
            methodProcessingContext::createUniqueContext);
    DexMethod[] parameterConversions =
        computeParameterConversions(
            invokedMethod,
            true,
            eventConsumer,
            context,
            methodProcessingContext::createUniqueContext);

    int parameterSize = invokedMethod.getParameters().size();
    ArrayList<CfInstruction> cfInstructions = new ArrayList<>();
    if (parameterSize != 0) {
      // If only the last 2 parameters require conversion, we do everything inlined.
      // If other parameters require conversion, we outline the parameter conversion but keep the
      // API
      // call inlined. The returned value is always converted inlined.
      boolean requireOutlinedParameterConversion = false;
      for (int i = 0; i < parameterConversions.length - 2; i++) {
        requireOutlinedParameterConversion |= parameterConversions[i] != null;
      }
      // We cannot use the swap instruction if the last parameter is wide.
      requireOutlinedParameterConversion |=
          invokedMethod.getParameters().get(parameterSize - 1).isWideType();

      if (requireOutlinedParameterConversion) {
        addOutlineParameterConversionInstructions(
            parameterConversions,
            cfInstructions,
            methodProcessingContext,
            invokedMethod,
            freshLocalProvider,
            localStackAllocator,
            eventConsumer);
      } else {
        addInlineParameterConversionInstructions(
            parameterConversions, cfInstructions, invokedMethod);
      }
    }

    DexMethod convertedMethod =
        convertedMethod(invokedMethod, true, returnConversion, parameterConversions);
    cfInstructions.add(new CfInvoke(invoke.getOpcode(), convertedMethod, invoke.isInterface()));

    if (returnConversion != null) {
      cfInstructions.add(new CfInvoke(Opcodes.INVOKESTATIC, returnConversion, false));
    }

    return cfInstructions;
  }

  // The parameters are converted and returned in an array of converted parameters. The parameter
  // array then needs to be unwrapped at the call site.
  private void addOutlineParameterConversionInstructions(
      DexMethod[] parameterConversions,
      ArrayList<CfInstruction> cfInstructions,
      MethodProcessingContext methodProcessingContext,
      DexMethod invokedMethod,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer) {
    localStackAllocator.allocateLocalStack(4);
    DexProto newProto =
        appView
            .dexItemFactory()
            .createProto(
                appView.dexItemFactory().objectArrayType, invokedMethod.getParameters().values);
    ProgramMethod parameterConversion =
        appView
            .getSyntheticItems()
            .createMethod(
                kinds -> kinds.API_CONVERSION_PARAMETERS,
                methodProcessingContext.createUniqueContext(),
                appView,
                builder ->
                    builder
                        .setProto(newProto)
                        .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                        // Will be traced by the enqueuer.
                        .disableAndroidApiLevelCheck()
                        .setCode(
                            methodSignature ->
                                computeParameterConversionCfCode(
                                    methodSignature.holder, invokedMethod, parameterConversions)));
    eventConsumer.acceptAPIConversionOutline(
        parameterConversion, methodProcessingContext.getMethodContext());
    cfInstructions.add(
        new CfInvoke(Opcodes.INVOKESTATIC, parameterConversion.getReference(), false));
    int arrayLocal = freshLocalProvider.getFreshLocal(ValueType.OBJECT.requiredRegisters());
    cfInstructions.add(new CfStore(ValueType.OBJECT, arrayLocal));
    for (int i = 0; i < parameterConversions.length; i++) {
      cfInstructions.add(new CfLoad(ValueType.OBJECT, arrayLocal));
      cfInstructions.add(new CfConstNumber(i, ValueType.INT));
      DexType parameterType =
          parameterConversions[i] != null
              ? parameterConversions[i].getReturnType()
              : invokedMethod.getParameter(i);
      cfInstructions.add(new CfArrayLoad(MemberType.OBJECT));
      if (parameterType.isPrimitiveType()) {
        cfInstructions.add(new CfCheckCast(factory.getBoxedForPrimitiveType(parameterType)));
        DexMethod method = appView.dexItemFactory().getUnboxPrimitiveMethod(parameterType);
        cfInstructions.add(new CfInvoke(Opcodes.INVOKEVIRTUAL, method, false));
      } else {
        cfInstructions.add(new CfCheckCast(parameterType));
      }
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private CfCode computeParameterConversionCfCode(
      DexType holder, DexMethod invokedMethod, DexMethod[] parameterConversions) {
    ArrayList<CfInstruction> cfInstructions = new ArrayList<>();
    cfInstructions.add(new CfConstNumber(parameterConversions.length, ValueType.INT));
    cfInstructions.add(new CfNewArray(factory.objectArrayType));
    int stackIndex = 0;
    for (int i = 0; i < invokedMethod.getArity(); i++) {
      cfInstructions.add(new CfStackInstruction(Opcode.Dup));
      cfInstructions.add(new CfConstNumber(i, ValueType.INT));
      DexType param = invokedMethod.getParameter(i);
      cfInstructions.add(new CfLoad(ValueType.fromDexType(param), stackIndex));
      if (parameterConversions[i] != null) {
        cfInstructions.add(new CfInvoke(Opcodes.INVOKESTATIC, parameterConversions[i], false));
      }
      if (param.isPrimitiveType()) {
        DexMethod method = appView.dexItemFactory().getBoxPrimitiveMethod(param);
        cfInstructions.add(new CfInvoke(Opcodes.INVOKESTATIC, method, false));
      }
      cfInstructions.add(new CfArrayStore(MemberType.OBJECT));
      if (param == appView.dexItemFactory().longType
          || param == appView.dexItemFactory().doubleType) {
        stackIndex++;
      }
      stackIndex++;
    }
    cfInstructions.add(new CfReturn(ValueType.OBJECT));
    return new CfCode(holder, stackIndex + 4, stackIndex, cfInstructions);
  }

  private void addInlineParameterConversionInstructions(
      DexMethod[] parameterConversions,
      ArrayList<CfInstruction> cfInstructions,
      DexMethod invokedMethod) {
    if (parameterConversions.length > 0
        && parameterConversions[parameterConversions.length - 1] != null) {
      cfInstructions.add(
          new CfInvoke(
              Opcodes.INVOKESTATIC, parameterConversions[parameterConversions.length - 1], false));
    }
    if (parameterConversions.length > 1
        && parameterConversions[parameterConversions.length - 2] != null) {
      assert !invokedMethod
          .getParameters()
          .get(invokedMethod.getParameters().size() - 1)
          .isWideType();
      cfInstructions.add(new CfStackInstruction(Opcode.Swap));
      cfInstructions.add(
          new CfInvoke(
              Opcodes.INVOKESTATIC, parameterConversions[parameterConversions.length - 2], false));
      cfInstructions.add(new CfStackInstruction(Opcode.Swap));
    }
  }

  private ProgramMethod resolveContext(DexMethod method, boolean isInterface) {
    // The context is provided to improve the error message with origin and position. If the method
    // is missing from the input, the context is null but the code runs correctly.
    return appView
        .appInfoForDesugaring()
        .resolveMethodLegacy(method, isInterface)
        .getResolvedProgramMethod();
  }

  private DexMethod computeReturnConversion(
      DexMethod invokedMethod,
      boolean destIsVivified,
      DesugaredLibraryClasspathWrapperSynthesizeEventConsumer eventConsumer,
      ProgramMethod context,
      Supplier<UniqueContext> contextSupplier) {
    return internalComputeReturnConversion(
        invokedMethod,
        (returnType, apiConversionCollection) ->
            wrapperSynthesizer.ensureConversionMethod(
                returnType,
                destIsVivified,
                apiConversionCollection,
                eventConsumer,
                context,
                contextSupplier),
        context);
  }

  private DexMethod computeReturnConversion(
      DexMethod invokedMethod,
      boolean destIsVivified,
      DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer eventConsumer,
      ProgramMethod context,
      Supplier<UniqueContext> contextSupplier) {
    return internalComputeReturnConversion(
        invokedMethod,
        (returnType, apiConversionCollection) ->
            wrapperSynthesizer.getExistingProgramConversionMethod(
                returnType,
                destIsVivified,
                apiConversionCollection,
                eventConsumer,
                context,
                contextSupplier),
        context);
  }

  private DexMethod internalComputeReturnConversion(
      DexMethod invokedMethod,
      BiFunction<DexType, DexMethod, DexMethod> methodSupplier,
      ProgramMethod context) {
    DexType returnType = invokedMethod.proto.returnType;
    DexMethod apiGenericTypesConversion = getReturnApiGenericConversion(invokedMethod);
    if (wrapperSynthesizer.shouldConvert(
        returnType, apiGenericTypesConversion, invokedMethod, context)) {
      return methodSupplier.apply(returnType, apiGenericTypesConversion);
    }
    return null;
  }

  private DexMethod[] computeParameterConversions(
      DexMethod invokedMethod,
      boolean destIsVivified,
      DesugaredLibraryClasspathWrapperSynthesizeEventConsumer eventConsumer,
      ProgramMethod context,
      Supplier<UniqueContext> contextSupplier) {
    return internalComputeParameterConversions(
        invokedMethod,
        wrapperSynthesizer,
        (argType, apiGenericTypesConversion) ->
            wrapperSynthesizer.ensureConversionMethod(
                argType,
                destIsVivified,
                apiGenericTypesConversion,
                eventConsumer,
                context,
                contextSupplier),
        context);
  }

  private DexMethod[] computeParameterConversions(
      DexMethod invokedMethod,
      boolean destIsVivified,
      DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer eventConsumer,
      ProgramMethod context,
      Supplier<UniqueContext> contextSupplier) {
    return internalComputeParameterConversions(
        invokedMethod,
        wrapperSynthesizer,
        (argType, apiGenericTypesConversion) ->
            wrapperSynthesizer.getExistingProgramConversionMethod(
                argType,
                destIsVivified,
                apiGenericTypesConversion,
                eventConsumer,
                context,
                contextSupplier),
        context);
  }

  private DexMethod[] internalComputeParameterConversions(
      DexMethod invokedMethod,
      DesugaredLibraryWrapperSynthesizer wrapperSynthesizor,
      BiFunction<DexType, DexMethod, DexMethod> methodSupplier,
      ProgramMethod context) {
    DexMethod[] parameterConversions = new DexMethod[invokedMethod.getArity()];
    DexType[] parameters = invokedMethod.proto.parameters.values;
    for (int i = 0; i < parameters.length; i++) {
      DexMethod apiGenericTypesConversion = getApiGenericConversion(invokedMethod, i);
      DexType argType = parameters[i];
      if (wrapperSynthesizor.shouldConvert(
          argType, apiGenericTypesConversion, invokedMethod, context)) {
        parameterConversions[i] = methodSupplier.apply(argType, apiGenericTypesConversion);
      }
    }
    return parameterConversions;
  }

  public DexMethod getReturnApiGenericConversion(DexMethod method) {
    return getApiGenericConversion(method, method.getArity());
  }

  public DexMethod getApiGenericConversion(DexMethod method, int parameterIndex) {
    DexMethod[] conversions =
        appView
            .options()
            .machineDesugaredLibrarySpecification
            .getApiGenericConversion()
            .get(method);
    return conversions == null ? null : conversions[parameterIndex];
  }

  private DexMethod convertedMethod(
      DexMethod method,
      boolean parameterDestIsVivified,
      DexMethod returnConversion,
      DexMethod[] parameterConversions) {
    return convertedMethod(
        method,
        parameterDestIsVivified,
        returnConversion,
        parameterConversions,
        method.getHolderType());
  }

  @SuppressWarnings("ReferenceEquality")
  private DexMethod convertedMethod(
      DexMethod method,
      boolean parameterDestIsVivified,
      DexMethod returnConversion,
      DexMethod[] parameterConversions,
      DexType newHolder) {
    DexType newReturnType =
        returnConversion != null
            ? parameterDestIsVivified
                ? returnConversion.getParameter(0)
                : returnConversion.getReturnType()
            : method.getReturnType();
    DexType[] newParameterTypes = new DexType[parameterConversions.length];
    for (int i = 0; i < parameterConversions.length; i++) {
      newParameterTypes[i] =
          parameterConversions[i] != null
              ? parameterDestIsVivified
                  ? parameterConversions[i].getReturnType()
                  : parameterConversions[i].getParameter(0)
              : method.getParameter(i);
    }
    DexMethod convertedAPI =
        appView
            .dexItemFactory()
            .createMethod(
                newHolder,
                appView.dexItemFactory().createProto(newReturnType, newParameterTypes),
                method.name);
    assert convertedAPI == methodWithVivifiedTypeInSignature(method, newHolder, appView)
        || appView
            .options()
            .machineDesugaredLibrarySpecification
            .getApiGenericConversion()
            .containsKey(method)
        || invalidType(method, returnConversion, parameterConversions, appView) != null;
    return convertedAPI;
  }

  @SuppressWarnings("ReferenceEquality")
  private static DexType invalidType(
      DexMethod invokedMethod,
      DexMethod returnConversion,
      DexMethod[] parameterConversions,
      AppView<?> appView) {
    DexMethod convertedMethod =
        methodWithVivifiedTypeInSignature(invokedMethod, invokedMethod.holder, appView);
    if (invokedMethod.getReturnType() != convertedMethod.getReturnType()
        && returnConversion == null) {
      return invokedMethod.getReturnType();
    }
    for (int i = 0; i < invokedMethod.getArity(); i++) {
      if (invokedMethod.getParameter(i) != convertedMethod.getParameter(i)
          && parameterConversions[i] == null) {
        return invokedMethod.getParameter(i);
      }
    }
    return null;
  }
}
