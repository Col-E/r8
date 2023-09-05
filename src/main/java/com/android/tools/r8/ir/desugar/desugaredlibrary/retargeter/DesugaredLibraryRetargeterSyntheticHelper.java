// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.ClasspathMethod;
import com.android.tools.r8.graph.ClasspathOrLibraryClass;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.DerivedMethod;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.EmulatedDispatchMethodDescriptor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.DesugaredLibraryRetargeterSynthesizerEventConsumer.DesugaredLibraryRetargeterInstructionEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.DesugaredLibraryRetargeterSynthesizerEventConsumer.DesugaredLibraryRetargeterL8SynthesizerEventConsumer;
import com.android.tools.r8.ir.synthetic.EmulateDispatchSyntheticCfCodeProvider;
import com.android.tools.r8.ir.synthetic.ForwardMethodBuilder;
import com.android.tools.r8.synthesis.SyntheticClassBuilder;
import com.android.tools.r8.synthesis.SyntheticItems.SyntheticKindSelector;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import java.util.LinkedHashMap;

public class DesugaredLibraryRetargeterSyntheticHelper {

  private final AppView<?> appView;

  public DesugaredLibraryRetargeterSyntheticHelper(AppView<?> appView) {
    this.appView = appView;
  }

  public DexMethod ensureCovariantRetargetMethod(
      DexMethod target,
      DexMethod retarget,
      DesugaredLibraryRetargeterInstructionEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    ProgramMethod method =
        appView
            .getSyntheticItems()
            .createMethod(
                kinds -> kinds.COVARIANT_OUTLINE,
                methodProcessingContext.createUniqueContext(),
                appView,
                methodBuilder ->
                    methodBuilder
                        .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                        .setProto(appView.dexItemFactory().prependHolderToProto(target))
                        .setCode(
                            m ->
                                ForwardMethodBuilder.builder(appView.dexItemFactory())
                                    .setVirtualTarget(retarget, false)
                                    .setNonStaticSource(target)
                                    .setCastResult()
                                    .build()));
    eventConsumer.acceptCovariantRetargetMethod(method, methodProcessingContext.getMethodContext());
    return method.getReference();
  }

  @SuppressWarnings("ReferenceEquality")
  public DexMethod ensureRetargetMethod(
      DexMethod retarget, DesugaredLibraryRetargeterInstructionEventConsumer eventConsumer) {
    DexClass holderClass = appView.definitionFor(retarget.getHolderType());
    if (holderClass != null && !holderClass.isClasspathClass()) {
      // The holder class is a library class in orthodox set-ups where the L8 compilation
      // is done in multiple steps, this is only partially supported (most notably for tests).
      assert holderClass.lookupMethod(retarget) != null;
      return retarget;
    }
    assert eventConsumer != null;
    ClasspathMethod ensuredMethod =
        appView
            .getSyntheticItems()
            .ensureFixedClasspathMethodFromType(
                retarget.getName(),
                retarget.getProto(),
                kinds -> kinds.RETARGET_STUB,
                retarget.getHolderType(),
                appView,
                ignored -> {},
                eventConsumer::acceptDesugaredLibraryRetargeterDispatchClasspathClass,
                methodBuilder ->
                    methodBuilder
                        .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                        .setCode(null));
    assert ensuredMethod.getReference() == retarget;
    return retarget;
  }

  DexMethod forwardingMethod(EmulatedDispatchMethodDescriptor descriptor) {
    assert descriptor.getForwardingMethod().getHolderKind(appView) == null;
    return descriptor.getForwardingMethod().getMethod();
  }

  public DexMethod ensureForwardingMethod(
      EmulatedDispatchMethodDescriptor descriptor,
      DesugaredLibraryRetargeterInstructionEventConsumer eventConsumer) {
    return ensureRetargetMethod(forwardingMethod(descriptor), eventConsumer);
  }

  private boolean verifyKind(DerivedMethod method, SyntheticKindSelector kindSelector) {
    SyntheticKind kind = kindSelector.select(appView.getSyntheticItems().getNaming());
    assert method.getHolderKind(appView).equals(kind);
    return true;
  }

  private DexMethod emulatedHolderDispatchMethod(DexType holder, DerivedMethod method) {
    assert verifyKind(method, kinds -> kinds.RETARGET_CLASS);
    DexProto newProto = appView.dexItemFactory().prependHolderToProto(method.getMethod());
    return appView.dexItemFactory().createMethod(holder, newProto, method.getName());
  }

  DexMethod emulatedInterfaceDispatchMethod(DexType holder, DerivedMethod method) {
    assert verifyKind(method, kinds -> kinds.RETARGET_INTERFACE);
    return appView.dexItemFactory().createMethod(holder, method.getProto(), method.getName());
  }

  public DexMethod emulatedInterfaceDispatchMethod(
      DexClass newInterface, EmulatedDispatchMethodDescriptor descriptor) {
    DexMethod method =
        emulatedInterfaceDispatchMethod(newInterface.type, descriptor.getInterfaceMethod());
    assert newInterface.lookupMethod(method) != null;
    return method;
  }

  public DexMethod ensureEmulatedHolderDispatchMethod(
      EmulatedDispatchMethodDescriptor descriptor,
      DesugaredLibraryRetargeterInstructionEventConsumer eventConsumer) {
    assert eventConsumer != null;
    DerivedMethod emulatedDispatchMethod = descriptor.getEmulatedDispatchMethod();
    DexClass holderContext =
        appView.contextIndependentDefinitionFor(emulatedDispatchMethod.getHolderContext());
    DexClass syntheticClass;
    if (appView.options().isDesugaredLibraryCompilation()) {
      syntheticClass =
          appView
              .getSyntheticItems()
              .getExistingFixedClass(
                  ignored -> emulatedDispatchMethod.getHolderKind(appView), holderContext, appView);
      DexMethod dispatchMethod =
          emulatedHolderDispatchMethod(syntheticClass.type, emulatedDispatchMethod);
      assert syntheticClass.lookupMethod(dispatchMethod) != null;
      return dispatchMethod;
    } else {
      DexClass itfClass = ensureEmulatedInterfaceDispatchMethod(descriptor, eventConsumer);
      ClasspathOrLibraryClass context = holderContext.asClasspathOrLibraryClass();
      assert context != null;
      syntheticClass =
          appView
              .getSyntheticItems()
              .ensureFixedClasspathClass(
                  kinds -> kinds.RETARGET_CLASS,
                  context,
                  appView,
                  classBuilder ->
                      buildHolderDispatchMethod(classBuilder, itfClass, descriptor, eventConsumer),
                  eventConsumer::acceptDesugaredLibraryRetargeterDispatchClasspathClass);
    }
    DexMethod dispatchMethod =
        emulatedHolderDispatchMethod(syntheticClass.type, emulatedDispatchMethod);
    assert syntheticClass.lookupMethod(dispatchMethod) != null;
    return dispatchMethod;
  }

  public void ensureProgramEmulatedHolderDispatchMethod(
      EmulatedDispatchMethodDescriptor descriptor,
      DesugaredLibraryRetargeterL8SynthesizerEventConsumer eventConsumer) {
    assert eventConsumer != null;
    assert appView.options().isDesugaredLibraryCompilation();
    DerivedMethod emulatedDispatchMethod = descriptor.getEmulatedDispatchMethod();
    DexClass holderContext =
        appView.contextIndependentDefinitionFor(emulatedDispatchMethod.getHolderContext());
    DexClass itfClass = ensureEmulatedInterfaceDispatchMethod(descriptor, eventConsumer);
    appView
        .getSyntheticItems()
        .ensureFixedClass(
            ignored -> emulatedDispatchMethod.getHolderKind(appView),
            holderContext,
            appView,
            classBuilder -> buildHolderDispatchMethod(classBuilder, itfClass, descriptor, null),
            eventConsumer::acceptDesugaredLibraryRetargeterDispatchProgramClass);
  }

  public DexClass ensureEmulatedInterfaceDispatchMethod(
      EmulatedDispatchMethodDescriptor descriptor,
      DesugaredLibraryRetargeterInstructionEventConsumer eventConsumer) {
    assert eventConsumer != null;
    DerivedMethod itfMethod = descriptor.getInterfaceMethod();
    DexClass itfContext = appView.contextIndependentDefinitionFor(itfMethod.getHolderContext());
    if (appView.options().isDesugaredLibraryCompilation()) {
      return appView
          .getSyntheticItems()
          .getExistingFixedClass(ignored -> itfMethod.getHolderKind(appView), itfContext, appView);
    }
    ClasspathOrLibraryClass context = itfContext.asClasspathOrLibraryClass();
    assert context != null;
    return appView
        .getSyntheticItems()
        .ensureFixedClasspathClass(
            kinds -> kinds.RETARGET_INTERFACE,
            context,
            appView,
            classBuilder -> buildInterfaceDispatchMethod(classBuilder, descriptor),
            eventConsumer::acceptDesugaredLibraryRetargeterDispatchClasspathClass);
  }

  public DexClass ensureEmulatedInterfaceDispatchMethod(
      EmulatedDispatchMethodDescriptor descriptor,
      DesugaredLibraryRetargeterL8SynthesizerEventConsumer eventConsumer) {
    assert appView.options().isDesugaredLibraryCompilation();
    assert eventConsumer != null;
    DerivedMethod itfMethod = descriptor.getInterfaceMethod();
    DexClass itfContext = appView.contextIndependentDefinitionFor(itfMethod.getHolderContext());
    return appView
        .getSyntheticItems()
        .ensureFixedClass(
            ignore -> itfMethod.getHolderKind(appView),
            itfContext,
            appView,
            classBuilder -> buildInterfaceDispatchMethod(classBuilder, descriptor),
            eventConsumer::acceptDesugaredLibraryRetargeterDispatchProgramClass);
  }

  private void buildInterfaceDispatchMethod(
      SyntheticClassBuilder<?, ?> classBuilder, EmulatedDispatchMethodDescriptor descriptor) {
    classBuilder
        .setInterface()
        .addMethod(
            methodBuilder -> {
              DexMethod itfMethod =
                  emulatedInterfaceDispatchMethod(
                      classBuilder.getType(), descriptor.getInterfaceMethod());
              MethodAccessFlags flags =
                  MethodAccessFlags.fromSharedAccessFlags(
                      Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                      false);
              methodBuilder
                  .setName(itfMethod.getName())
                  .setProto(itfMethod.getProto())
                  // Will be traced by the enqueuer.
                  .disableAndroidApiLevelCheck()
                  .setAccessFlags(flags);
            });
  }

  private <SCB extends SyntheticClassBuilder<?, ?>> void buildHolderDispatchMethod(
      SCB classBuilder,
      DexClass itfClass,
      EmulatedDispatchMethodDescriptor descriptor,
      DesugaredLibraryRetargeterInstructionEventConsumer eventConsumer) {
    classBuilder.addMethod(
        methodBuilder -> {
          DexMethod dispatchMethod =
              emulatedHolderDispatchMethod(
                  classBuilder.getType(), descriptor.getEmulatedDispatchMethod());
          methodBuilder
              .setName(dispatchMethod.getName())
              .setProto(dispatchMethod.getProto())
              .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
              // Will be traced by the enqueuer.
              .disableAndroidApiLevelCheck()
              .setCode(
                  methodSig ->
                      appView.options().isDesugaredLibraryCompilation()
                          ? generateEmulatedDispatchCfCode(
                              descriptor, itfClass, methodSig, eventConsumer)
                          : null);
        });
  }

  private CfCode generateEmulatedDispatchCfCode(
      EmulatedDispatchMethodDescriptor descriptor,
      DexClass itfClass,
      DexMethod methodSig,
      DesugaredLibraryRetargeterInstructionEventConsumer eventConsumer) {
    DexMethod forwardingMethod = ensureForwardingMethod(descriptor, eventConsumer);
    DexMethod itfMethod = emulatedInterfaceDispatchMethod(itfClass, descriptor);
    assert descriptor.getDispatchCases().isEmpty();
    return new EmulateDispatchSyntheticCfCodeProvider(
            methodSig.getHolderType(), forwardingMethod, itfMethod, new LinkedHashMap<>(), appView)
        .generateCfCode();
  }
}
