// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import com.android.tools.r8.ir.desugar.FreshLocalProvider;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.Iterables;
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
  private final Set<CfInstructionDesugaring> precedingDesugarings;
  private final Set<DexString> emulatedMethods;

  private final DesugaredLibraryWrapperSynthesizer wrapperSynthesizor;
  private final Set<DexMethod> trackedAPIs;

  public DesugaredLibraryAPIConverter(
      AppView<?> appView,
      Set<CfInstructionDesugaring> precedingDesugarings,
      Set<DexString> emulatedMethods) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    this.precedingDesugarings = precedingDesugarings;
    this.emulatedMethods = emulatedMethods;
    this.wrapperSynthesizor = new DesugaredLibraryWrapperSynthesizer(appView);
    if (appView.options().testing.trackDesugaredAPIConversions) {
      trackedAPIs = SetUtils.newConcurrentHashSet();
    } else {
      trackedAPIs = null;
    }
  }

  private boolean invokeNeedsDesugaring(CfInvoke invoke, ProgramMethod context) {
    if (isAPIConversionSyntheticType(context.getHolderType(), wrapperSynthesizor, appView)) {
      return false;
    }
    if (appView.dexItemFactory().multiDexTypes.contains(context.getHolderType())) {
      return false;
    }
    return shouldRewriteInvoke(invoke.asInvoke(), context);
  }

  @Override
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    if (!instruction.isInvoke()) {
      return DesugarDescription.nothing();
    }
    CfInvoke invoke = instruction.asInvoke();
    if (!invokeNeedsDesugaring(invoke, context)) {
      return DesugarDescription.nothing();
    }
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (freshLocalProvider,
                localStackAllocator,
                eventConsumer,
                theContext,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) ->
                rewriteLibraryInvoke(
                    invoke,
                    methodProcessingContext,
                    freshLocalProvider,
                    localStackAllocator,
                    eventConsumer,
                    context))
        .build();
  }

  static boolean isAPIConversionSyntheticType(
      DexType type, DesugaredLibraryWrapperSynthesizer wrapperSynthesizor, AppView<?> appView) {
    return wrapperSynthesizor.isSyntheticWrapper(type)
        || appView.getSyntheticItems().isSyntheticOfKind(type, kinds -> kinds.API_CONVERSION);
  }

  public static boolean isVivifiedType(DexType type) {
    return type.descriptor.toString().startsWith(DESCRIPTOR_VIVIFIED_PREFIX);
  }

  private DexClassAndMethod getMethodForDesugaring(CfInvoke invoke, ProgramMethod context) {
    DexMethod invokedMethod = invoke.getMethod();
    // TODO(b/191656218): Use lookupInvokeSpecial instead when this is all to Cf.
    AppInfoWithClassHierarchy appInfoForDesugaring = appView.appInfoForDesugaring();
    return invoke.isInvokeSuper(context.getHolderType())
        ? appInfoForDesugaring.lookupSuperTarget(
            invokedMethod, context, appView, appInfoForDesugaring)
        : appInfoForDesugaring
            .resolveMethodLegacy(invokedMethod, invoke.isInterface())
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
    if (appView.typeRewriter.hasRewrittenType(holderType, appView) || holderType.isArrayType()) {
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
    if (appView
            .options()
            .machineDesugaredLibrarySpecification
            .getApiGenericConversion()
            .get(invokedMethod.getReference())
        != null) {
      return true;
    }
    return appView.typeRewriter.hasRewrittenTypeInSignature(invokedMethod.getProto(), appView);
  }

  // The problem is that a method can resolve into a library method which is not present at runtime,
  // the code relies in that case on emulated interface dispatch. We should not convert such API.
  private boolean isEmulatedInterfaceOverride(DexClassAndMethod invokedMethod) {
    if (!emulatedMethods.contains(invokedMethod.getName())) {
      return false;
    }
    DexClassAndMethod interfaceResult =
        appView
            .appInfoForDesugaring()
            .lookupMaximallySpecificMethod(invokedMethod.getHolder(), invokedMethod.getReference());
    return interfaceResult != null
        && appView
            .options()
            .machineDesugaredLibrarySpecification
            .getEmulatedInterfaces()
            .containsKey(interfaceResult.getHolderType());
  }

  private boolean isAlreadyDesugared(CfInvoke invoke, ProgramMethod context) {
    return Iterables.any(
        precedingDesugarings, desugaring -> desugaring.compute(invoke, context).needsDesugaring());
  }

  public static DexMethod methodWithVivifiedTypeInSignature(
      DexMethod originalMethod, DexType holder, AppView<?> appView) {
    DexType[] newParameters = originalMethod.proto.parameters.values.clone();
    int index = 0;
    for (DexType param : originalMethod.proto.parameters.values) {
      if (appView.typeRewriter.hasRewrittenType(param, appView)) {
        newParameters[index] = vivifiedTypeFor(param, appView);
      }
      index++;
    }
    DexType returnType = originalMethod.proto.returnType;
    DexType newReturnType =
        appView.typeRewriter.hasRewrittenType(returnType, appView)
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
    // We would need to ensure a classpath class for each type to remove this rewriteType call.
    appView.typeRewriter.rewriteType(vivifiedType, type);
    return vivifiedType;
  }

  private Collection<CfInstruction> rewriteLibraryInvoke(
      CfInvoke invoke,
      MethodProcessingContext methodProcessingContext,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context) {
    DexMethod invokedMethod = invoke.getMethod();
    if (trackedAPIs != null) {
      trackedAPIs.add(invokedMethod);
    }
    if (shouldOutlineAPIConversion(invoke, context)) {
      DexMethod outlinedAPIConversion =
          wrapperSynthesizor
              .getConversionCfProvider()
              .generateOutlinedAPIConversion(
                  invoke, eventConsumer, context, methodProcessingContext)
              .getReference();
      return Collections.singletonList(
          new CfInvoke(Opcodes.INVOKESTATIC, outlinedAPIConversion, false));
    }
    return wrapperSynthesizor
        .getConversionCfProvider()
        .generateInlinedAPIConversion(
            invoke,
            methodProcessingContext,
            freshLocalProvider,
            localStackAllocator,
            eventConsumer,
            context);
  }

  // If the option is set, we try to outline API conversions as much as possible to reduce the
  // number
  // of soft verification failures. We cannot outline API conversions through super invokes, to
  // instance initializers and to non public methods.
  private boolean shouldOutlineAPIConversion(CfInvoke invoke, ProgramMethod context) {
    if (appView.options().testing.forceInlineAPIConversions) {
      return false;
    }
    if (invoke.isInvokeSuper(context.getHolderType())) {
      return false;
    }
    if (invoke.getMethod().isInstanceInitializer(appView.dexItemFactory())) {
      return false;
    }
    DexClassAndMethod methodForDesugaring = getMethodForDesugaring(invoke, context);
    assert methodForDesugaring != null;
    // Specific apis that we never want to outline, namely, apis for stack introspection since it
    // confuses developers in debug mode.
    if (appView
        .options()
        .machineDesugaredLibrarySpecification
        .getNeverOutlineApi()
        .contains(methodForDesugaring.getReference())) {
      return false;
    }
    return methodForDesugaring.getAccessFlags().isPublic();
  }


}
