// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.itf;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaring;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.DerivedMethod;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.EmulatedDispatchMethodDescriptor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.EmulatedInterfaceDescriptor;
import com.android.tools.r8.ir.desugar.itf.EmulatedInterfaceSynthesizerEventConsumer.L8ProgramEmulatedInterfaceSynthesizerEventConsumer;
import com.android.tools.r8.ir.synthetic.EmulateDispatchSyntheticCfCodeProvider;
import com.android.tools.r8.synthesis.SyntheticMethodBuilder;
import com.android.tools.r8.synthesis.SyntheticNaming;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.synthesis.SyntheticProgramClassBuilder;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.Iterables;
import java.util.LinkedHashMap;

public final class ProgramEmulatedInterfaceSynthesizer implements CfClassSynthesizerDesugaring {

  private final AppView<?> appView;
  private final InterfaceDesugaringSyntheticHelper helper;

  public static ProgramEmulatedInterfaceSynthesizer create(AppView<?> appView) {
    if (!appView.options().isDesugaredLibraryCompilation()
        || appView.options().desugaredLibrarySpecification.getEmulateLibraryInterface().isEmpty()) {
      return null;
    }
    return new ProgramEmulatedInterfaceSynthesizer(appView);
  }

  public ProgramEmulatedInterfaceSynthesizer(AppView<?> appView) {
    this.appView = appView;
    helper = new InterfaceDesugaringSyntheticHelper(appView);
  }

  DexProgramClass synthesizeProgramEmulatedInterface(
      DexProgramClass emulatedInterface,
      L8ProgramEmulatedInterfaceSynthesizerEventConsumer eventConsumer) {
    return appView
        .getSyntheticItems()
        .ensureFixedClass(
            SyntheticNaming.SyntheticKind.EMULATED_INTERFACE_CLASS,
            emulatedInterface,
            appView,
            builder -> synthesizeEmulateInterfaceMethods(emulatedInterface, builder),
            eventConsumer::acceptProgramEmulatedInterface);
  }

  private void synthesizeEmulateInterfaceMethods(
      DexProgramClass emulatedInterface, SyntheticProgramClassBuilder builder) {
    assert helper.isEmulatedInterface(emulatedInterface.type);
    EmulatedInterfaceDescriptor emulatedInterfaceDescriptor =
        appView
            .options()
            .machineDesugaredLibrarySpecification
            .getRewritingFlags()
            .getEmulatedInterfaces()
            .get(emulatedInterface.type);
    emulatedInterface.forEachProgramVirtualMethodMatching(
        m -> emulatedInterfaceDescriptor.getEmulatedMethods().containsKey(m.getReference()),
        method ->
            builder.addMethod(
                methodBuilder ->
                    synthesizeEmulatedInterfaceMethod(
                        method,
                        emulatedInterfaceDescriptor.getEmulatedMethods().get(method.getReference()),
                        builder.getType(),
                        methodBuilder)));
  }

  private DexMethod emulatedMethod(DerivedMethod method, DexType holder) {
    assert method.getHolderKind() == SyntheticKind.EMULATED_INTERFACE_CLASS;
    DexProto newProto = appView.dexItemFactory().prependHolderToProto(method.getMethod());
    return appView.dexItemFactory().createMethod(holder, newProto, method.getName());
  }

  private DexMethod interfaceMethod(DerivedMethod method) {
    assert method.getHolderKind() == null;
    return method.getMethod();
  }

  private void synthesizeEmulatedInterfaceMethod(
      ProgramMethod method,
      EmulatedDispatchMethodDescriptor descriptor,
      DexType dispatchType,
      SyntheticMethodBuilder methodBuilder) {
    assert !method.getDefinition().isStatic();
    DexMethod emulatedMethod = emulatedMethod(descriptor.getEmulatedDispatchMethod(), dispatchType);
    DexMethod itfMethod = interfaceMethod(descriptor.getInterfaceMethod());
    // TODO(b/184026720): Adapt to use the forwarding method.
    DerivedMethod forwardingMethod = descriptor.getForwardingMethod();
    assert forwardingMethod.getHolderKind() == SyntheticKind.COMPANION_CLASS;
    assert forwardingMethod.getMethod() == method.getReference();
    DexMethod companionMethod =
        helper.ensureDefaultAsMethodOfProgramCompanionClassStub(method).getReference();
    LinkedHashMap<DexType, DexMethod> extraDispatchCases = resolveDispatchCases(descriptor);
    methodBuilder
        .setName(emulatedMethod.getName())
        .setProto(emulatedMethod.getProto())
        .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
        .setCode(
            emulatedInterfaceMethod ->
                new EmulateDispatchSyntheticCfCodeProvider(
                        emulatedMethod.getHolderType(),
                        companionMethod,
                        itfMethod,
                        extraDispatchCases,
                        appView)
                    .generateCfCode());
  }

  private LinkedHashMap<DexType, DexMethod> resolveDispatchCases(
      EmulatedDispatchMethodDescriptor descriptor) {
    LinkedHashMap<DexType, DexMethod> extraDispatchCases = new LinkedHashMap<>();
    descriptor
        .getDispatchCases()
        .forEach(
            (type, derivedMethod) -> {
              DexMethod caseMethod;
              if (derivedMethod.getHolderKind() == null) {
                caseMethod = derivedMethod.getMethod();
              } else {
                assert derivedMethod.getHolderKind() == SyntheticKind.COMPANION_CLASS;
                ProgramMethod resolvedProgramMethod =
                    appView
                        .appInfoForDesugaring()
                        .resolveMethod(derivedMethod.getMethod(), true)
                        .getResolvedProgramMethod();
                caseMethod =
                    helper
                        .ensureDefaultAsMethodOfProgramCompanionClassStub(resolvedProgramMethod)
                        .getReference();
              }
              extraDispatchCases.put(type, caseMethod);
            });
    return extraDispatchCases;
  }

  @Override
  public void synthesizeClasses(CfClassSynthesizerDesugaringEventConsumer eventConsumer) {
    assert appView.options().isDesugaredLibraryCompilation();
    for (DexType emulatedInterfaceType : helper.getEmulatedInterfaces()) {
      DexClass emulatedInterfaceClazz = appView.definitionFor(emulatedInterfaceType);
      if (emulatedInterfaceClazz == null || !emulatedInterfaceClazz.isProgramClass()) {
        warnMissingEmulatedInterface(emulatedInterfaceType);
        continue;
      }
      DexProgramClass emulatedInterface = emulatedInterfaceClazz.asProgramClass();
      assert emulatedInterface != null;
      if (!appView.isAlreadyLibraryDesugared(emulatedInterface)
          && needsEmulateInterfaceLibrary(emulatedInterface)) {
        synthesizeProgramEmulatedInterface(emulatedInterface, eventConsumer);
      }
    }
  }

  private boolean needsEmulateInterfaceLibrary(DexClass emulatedInterface) {
    return Iterables.any(emulatedInterface.methods(), DexEncodedMethod::isDefaultMethod);
  }

  private void warnMissingEmulatedInterface(DexType interfaceType) {
    StringDiagnostic warning =
        new StringDiagnostic(
            "Cannot emulate interface "
                + interfaceType.getName()
                + " because the interface is missing.");
    appView.options().reporter.warning(warning);
  }
}
