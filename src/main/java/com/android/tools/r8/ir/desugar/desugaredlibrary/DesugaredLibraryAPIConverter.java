// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary;

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
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.FreshLocalProvider;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryWrapperSynthesizerEventConsumer.DesugaredLibraryClasspathWrapperSynthesizeEventConsumer;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodRewriter;
import com.android.tools.r8.ir.synthetic.DesugaredLibraryAPIConversionCfCodeProvider.APIConversionCfCodeProvider;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.objectweb.asm.Opcodes;

// I convert library calls with desugared parameters/return values so they can work normally.
// In the JSON of the desugared library, one can specify conversions between desugared and
// non-desugared types. If no conversion is specified, D8/R8 simply generate wrapper classes around
// the types. Wrappers induce both memory and runtime performance overhead. Wrappers overload
// all potential called APIs.
// Since many types are going to be rewritten, I also need to change the signature of the method
// called so that they are still called with the original types. Hence the vivified types.
// Given a type from the library, the prefix rewriter rewrites (->) as follow:
// vivifiedType -> type;
// type -> desugarType;
// No vivified types can be present in the compiled program (will necessarily be rewritten).
// DesugarType is only a rewritten type (generated through rewriting of type).
// The type, from the library, may either be rewritten to the desugarType,
// or be a rewritten type (generated through rewriting of vivifiedType).
public class DesugaredLibraryAPIConverter implements CfInstructionDesugaring {

  static final String VIVIFIED_PREFIX = "$-vivified-$.";
  public static final String DESCRIPTOR_VIVIFIED_PREFIX = "L$-vivified-$/";

  private final AppView<?> appView;
  private final DexItemFactory factory;
  // This is used to filter out double desugaring on backported methods.
  private final BackportedMethodRewriter backportedMethodRewriter;
  private final InterfaceMethodRewriter interfaceMethodRewriter;
  private final DesugaredLibraryRetargeter retargeter;

  private final DesugaredLibraryWrapperSynthesizer wrapperSynthesizor;
  private final Set<DexMethod> trackedCallBackAPIs;
  private final Set<DexMethod> trackedAPIs;

  public DesugaredLibraryAPIConverter(
      AppView<?> appView,
      InterfaceMethodRewriter interfaceMethodRewriter,
      DesugaredLibraryRetargeter retargeter,
      BackportedMethodRewriter backportedMethodRewriter) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    this.interfaceMethodRewriter = interfaceMethodRewriter;
    this.retargeter = retargeter;
    this.backportedMethodRewriter = backportedMethodRewriter;
    this.wrapperSynthesizor = new DesugaredLibraryWrapperSynthesizer(appView);
    if (appView.options().testing.trackDesugaredAPIConversions) {
      trackedCallBackAPIs = Sets.newConcurrentHashSet();
      trackedAPIs = Sets.newConcurrentHashSet();
    } else {
      trackedCallBackAPIs = null;
      trackedAPIs = null;
    }
  }

  @Override
  public Collection<CfInstruction> desugarInstruction(
      CfInstruction instruction,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      DexItemFactory dexItemFactory) {
    if (needsDesugaring(instruction, context)) {
      assert instruction.isInvoke();
      return rewriteLibraryInvoke(
          instruction.asInvoke(),
          methodProcessingContext,
          localStackAllocator,
          eventConsumer,
          context);
    }
    return null;
  }

  @Override
  public boolean needsDesugaring(CfInstruction instruction, ProgramMethod context) {
    if (!instruction.isInvoke()) {
      return false;
    }
    if (isAPIConversionSyntheticType(context.getHolderType(), wrapperSynthesizor, appView)) {
      return false;
    }
    return shouldRewriteInvoke(instruction.asInvoke(), context);
  }

  static boolean isAPIConversionSyntheticType(
      DexType type, DesugaredLibraryWrapperSynthesizer wrapperSynthesizor, AppView<?> appView) {
    return wrapperSynthesizor.isSyntheticWrapper(type)
        || appView.getSyntheticItems().isSyntheticOfKind(type, SyntheticKind.API_CONVERSION);
  }

  public static boolean isVivifiedType(DexType type) {
    return type.descriptor.toString().startsWith(DESCRIPTOR_VIVIFIED_PREFIX);
  }

  private DexClassAndMethod getMethodForDesugaring(CfInvoke invoke, ProgramMethod context) {
    DexMethod invokedMethod = invoke.getMethod();
    // TODO(b/191656218): Use lookupInvokeSpecial instead when this is all to Cf.
    return invoke.isInvokeSuper(context.getHolderType())
        ? appView.appInfoForDesugaring().lookupSuperTarget(invokedMethod, context)
        : appView
            .appInfoForDesugaring()
            .resolveMethod(invokedMethod, invoke.isInterface())
            .getResolutionPair();
  }

  // TODO(b/191656218): Consider caching the result.
  private boolean shouldRewriteInvoke(CfInvoke invoke, ProgramMethod context) {
    DexClassAndMethod invokedMethod = getMethodForDesugaring(invoke, context);
    if (invokedMethod == null) {
      // Implies a resolution/look-up failure, we do not convert to keep the runtime error.
      return false;
    }
    DexType holderType = invokedMethod.getHolderType();
    if (appView.rewritePrefix.hasRewrittenType(holderType, appView) || holderType.isArrayType()) {
      return false;
    }
    DexClass dexClass = appView.definitionFor(holderType);
    if (dexClass == null || !dexClass.isLibraryClass()) {
      return false;
    }
    if (isEmulatedInterfaceOverride(invokedMethod)) {
      return false;
    }
    if (isAlreadyDesugared(invoke, context)) {
      return false;
    }
    return appView.rewritePrefix.hasRewrittenTypeInSignature(invokedMethod.getProto(), appView);
  }

  // The problem is that a method can resolve into a library method which is not present at runtime,
  // the code relies in that case on emulated interface dispatch. We should not convert such API.
  private boolean isEmulatedInterfaceOverride(DexClassAndMethod invokedMethod) {
    if (interfaceMethodRewriter == null) {
      return false;
    }
    if (!interfaceMethodRewriter.getEmulatedMethods().contains(invokedMethod.getName())) {
      return false;
    }
    DexClassAndMethod interfaceResult =
        appView
            .appInfoForDesugaring()
            .lookupMaximallySpecificMethod(invokedMethod.getHolder(), invokedMethod.getReference());
    return interfaceResult != null
        && appView
            .options()
            .desugaredLibraryConfiguration
            .getEmulateLibraryInterface()
            .containsKey(interfaceResult.getHolderType());
  }

  private boolean isAlreadyDesugared(CfInvoke invoke, ProgramMethod context) {
    if (interfaceMethodRewriter != null
        && interfaceMethodRewriter.needsDesugaring(invoke, context)) {
      return true;
    }
    if (retargeter != null && retargeter.needsDesugaring(invoke, context)) {
      return true;
    }
    if (backportedMethodRewriter != null
        && backportedMethodRewriter.needsDesugaring(invoke, context)) {
      return true;
    }
    return false;
  }

  public static DexMethod methodWithVivifiedTypeInSignature(
      DexMethod originalMethod, DexType holder, AppView<?> appView) {
    DexType[] newParameters = originalMethod.proto.parameters.values.clone();
    int index = 0;
    for (DexType param : originalMethod.proto.parameters.values) {
      if (appView.rewritePrefix.hasRewrittenType(param, appView)) {
        newParameters[index] = vivifiedTypeFor(param, appView);
      }
      index++;
    }
    DexType returnType = originalMethod.proto.returnType;
    DexType newReturnType =
        appView.rewritePrefix.hasRewrittenType(returnType, appView)
            ? vivifiedTypeFor(returnType, appView)
            : returnType;
    DexProto newProto = appView.dexItemFactory().createProto(newReturnType, newParameters);
    return appView.dexItemFactory().createMethod(holder, newProto, originalMethod.name);
  }
  public void generateTrackingWarnings() {
    generateTrackDesugaredAPIWarnings(trackedAPIs, "", appView);
  }

  static void generateTrackDesugaredAPIWarnings(
      Set<DexMethod> tracked, String inner, AppView<?> appView) {
    if (!appView.options().testing.trackDesugaredAPIConversions) {
      return;
    }
    StringBuilder sb = new StringBuilder();
    sb.append("Tracked ").append(inner).append("desugared API conversions: ");
    for (DexMethod method : tracked) {
      sb.append("\n");
      sb.append(method);
    }
    appView.options().reporter.warning(new StringDiagnostic(sb.toString()));
    tracked.clear();
  }

  public static DexType vivifiedTypeFor(DexType type, AppView<?> appView) {
    DexType vivifiedType =
        appView
            .dexItemFactory()
            .createSynthesizedType(
                DescriptorUtils.javaTypeToDescriptor(VIVIFIED_PREFIX + type.toString()));
    appView.rewritePrefix.rewriteType(vivifiedType, type);
    return vivifiedType;
  }

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

  public static DexMethod getConvertedAPI(
      DexMethod invokedMethod,
      DexMethod returnConversion,
      DexMethod[] parameterConversions,
      AppView<?> appView) {
    DexType newReturnType =
        returnConversion != null ? returnConversion.getParameter(0) : invokedMethod.getReturnType();
    DexType[] newParameterTypes = new DexType[parameterConversions.length];
    for (int i = 0; i < parameterConversions.length; i++) {
      newParameterTypes[i] =
          parameterConversions[i] != null
              ? parameterConversions[i].getReturnType()
              : invokedMethod.getParameter(i);
    }
    DexMethod convertedAPI =
        appView
            .dexItemFactory()
            .createMethod(
                invokedMethod.holder,
                appView.dexItemFactory().createProto(newReturnType, newParameterTypes),
                invokedMethod.name);
    assert convertedAPI
            == methodWithVivifiedTypeInSignature(invokedMethod, invokedMethod.holder, appView)
        || invalidType(invokedMethod, returnConversion, parameterConversions, appView) != null;
    return convertedAPI;
  }

  private DexMethod computeReturnConversion(
      DexMethod invokedMethod,
      DesugaredLibraryClasspathWrapperSynthesizeEventConsumer eventConsumer) {
    DexType returnType = invokedMethod.proto.returnType;
    if (wrapperSynthesizor.shouldConvert(returnType, invokedMethod)) {
      DexType newReturnType = DesugaredLibraryAPIConverter.vivifiedTypeFor(returnType, appView);
      return wrapperSynthesizor.ensureConversionMethod(
          returnType, newReturnType, returnType, eventConsumer);
    }
    return null;
  }

  private DexMethod[] computeParameterConversions(
      DexMethod invokedMethod,
      DesugaredLibraryClasspathWrapperSynthesizeEventConsumer eventConsumer) {
    DexMethod[] parameterConversions = new DexMethod[invokedMethod.getArity()];
    DexType[] parameters = invokedMethod.proto.parameters.values;
    for (int i = 0; i < parameters.length; i++) {
      DexType argType = parameters[i];
      if (wrapperSynthesizor.shouldConvert(argType, invokedMethod)) {
        DexType argVivifiedType = vivifiedTypeFor(argType, appView);
        parameterConversions[i] =
            wrapperSynthesizor.ensureConversionMethod(
                argType, argType, argVivifiedType, eventConsumer);
      }
    }
    return parameterConversions;
  }

  private Collection<CfInstruction> rewriteLibraryInvoke(
      CfInvoke invoke,
      MethodProcessingContext methodProcessingContext,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context) {
    DexMethod invokedMethod = invoke.getMethod();
    if (trackedAPIs != null) {
      trackedAPIs.add(invokedMethod);
    }
    if (shouldOutlineAPIConversion(invoke, context)) {
      DexMethod outlinedAPIConversion =
          createOutlinedAPIConversion(invoke, methodProcessingContext, eventConsumer);
      return Collections.singletonList(
          new CfInvoke(Opcodes.INVOKESTATIC, outlinedAPIConversion, false));
    }
    return rewriteLibraryInvokeToInlineAPIConversion(
        invoke, methodProcessingContext, localStackAllocator, eventConsumer);
  }

  // If the option is set, we try to outline API conversions as much as possible to reduce the
  // number
  // of soft verification failures. We cannot outline API conversions through super invokes, to
  // instance initializers and to non public methods.
  private boolean shouldOutlineAPIConversion(CfInvoke invoke, ProgramMethod context) {
    if (invoke.isInvokeSuper(context.getHolderType())) {
      return false;
    }
    if (invoke.getMethod().isInstanceInitializer(appView.dexItemFactory())) {
      return false;
    }
    DexClassAndMethod methodForDesugaring = getMethodForDesugaring(invoke, context);
    assert methodForDesugaring != null;
    return methodForDesugaring.getAccessFlags().isPublic();
  }

  private Collection<CfInstruction> rewriteLibraryInvokeToInlineAPIConversion(
      CfInvoke invoke,
      MethodProcessingContext methodProcessingContext,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer) {

    DexMethod invokedMethod = invoke.getMethod();
    DexMethod returnConversion = computeReturnConversion(invokedMethod, eventConsumer);
    DexMethod[] parameterConversions = computeParameterConversions(invokedMethod, eventConsumer);

    // If only the last 2 parameters require conversion, we do everything inlined.
    // If other parameters require conversion, we outline the parameter conversion but keep the API
    // call inlined.
    // The returned value is always converted inlined.
    boolean requireOutlinedParameterConversion = false;
    for (int i = 0; i < parameterConversions.length - 2; i++) {
      requireOutlinedParameterConversion |= parameterConversions[i] != null;
    }

    ArrayList<CfInstruction> cfInstructions = new ArrayList<>();
    if (requireOutlinedParameterConversion) {
      addOutlineParameterConversionInstructions(
          parameterConversions,
          cfInstructions,
          methodProcessingContext,
          invokedMethod,
          localStackAllocator,
          eventConsumer);
    } else {
      addInlineParameterConversionInstructions(parameterConversions, cfInstructions);
    }

    DexMethod convertedMethod =
        getConvertedAPI(invokedMethod, returnConversion, parameterConversions, appView);
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
                SyntheticKind.API_CONVERSION_PARAMETERS,
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
    eventConsumer.acceptAPIConversion(parameterConversion);
    cfInstructions.add(
        new CfInvoke(Opcodes.INVOKESTATIC, parameterConversion.getReference(), false));
    for (int i = 0; i < parameterConversions.length; i++) {
      cfInstructions.add(new CfStackInstruction(Opcode.Dup));
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
      cfInstructions.add(new CfStackInstruction(Opcode.Swap));
    }
    cfInstructions.add(new CfStackInstruction(Opcode.Pop));
  }

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
    return new CfCode(
        holder,
        invokedMethod.getParameters().size() + 4,
        invokedMethod.getParameters().size(),
        cfInstructions);
  }

  private void addInlineParameterConversionInstructions(
      DexMethod[] parameterConversions, ArrayList<CfInstruction> cfInstructions) {
    if (parameterConversions.length > 0
        && parameterConversions[parameterConversions.length - 1] != null) {
      cfInstructions.add(
          new CfInvoke(
              Opcodes.INVOKESTATIC, parameterConversions[parameterConversions.length - 1], false));
    }
    if (parameterConversions.length > 1
        && parameterConversions[parameterConversions.length - 2] != null) {
      cfInstructions.add(new CfStackInstruction(Opcode.Swap));
      cfInstructions.add(
          new CfInvoke(
              Opcodes.INVOKESTATIC, parameterConversions[parameterConversions.length - 2], false));
      cfInstructions.add(new CfStackInstruction(Opcode.Swap));
    }
  }

  private DexMethod createOutlinedAPIConversion(
      CfInvoke invoke,
      MethodProcessingContext methodProcessingContext,
      CfInstructionDesugaringEventConsumer eventConsumer) {
    DexMethod invokedMethod = invoke.getMethod();
    DexProto newProto =
        invoke.isInvokeStatic()
            ? invokedMethod.proto
            : factory.prependTypeToProto(invokedMethod.getHolderType(), invokedMethod.getProto());
    DexMethod returnConversion = computeReturnConversion(invokedMethod, eventConsumer);
    DexMethod[] parameterConversions = computeParameterConversions(invokedMethod, eventConsumer);
    ProgramMethod outline =
        appView
            .getSyntheticItems()
            .createMethod(
                SyntheticKind.API_CONVERSION,
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
                                        invoke,
                                        returnConversion,
                                        parameterConversions)
                                    .generateCfCode()));
    eventConsumer.acceptAPIConversion(outline);
    return outline.getReference();
  }
}
