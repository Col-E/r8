// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.ClasspathOrLibraryClass;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.DerivedMethod;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.EmulatedDispatchMethodDescriptor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.DesugaredLibraryRetargeterSynthesizerEventConsumer.DesugaredLibraryRetargeterInstructionEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.DesugaredLibraryRetargeterSynthesizerEventConsumer.DesugaredLibraryRetargeterL8SynthesizerEventConsumer;
import com.android.tools.r8.ir.synthetic.EmulateDispatchSyntheticCfCodeProvider;
import com.android.tools.r8.synthesis.SyntheticClassBuilder;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import java.util.LinkedHashMap;

public class DesugaredLibraryRetargeterSyntheticHelper {

  private final AppView<?> appView;

  public DesugaredLibraryRetargeterSyntheticHelper(AppView<?> appView) {
    this.appView = appView;
  }

  public DexMethod ensureForwardingMethod(EmulatedDispatchMethodDescriptor descriptor) {
    // TODO(b/184026720): We may synthesize a stub on the classpath if absent.
    assert descriptor.getForwardingMethod().getHolderKind() == null;
    return descriptor.getForwardingMethod().getMethod();
  }

  private DexMethod emulatedHolderDispatchMethod(DexType holder, DerivedMethod method) {
    assert method.getHolderKind() == SyntheticKind.RETARGET_CLASS;
    DexProto newProto = appView.dexItemFactory().prependHolderToProto(method.getMethod());
    return appView.dexItemFactory().createMethod(holder, newProto, method.getName());
  }

  DexMethod emulatedInterfaceDispatchMethod(DexType holder, DerivedMethod method) {
    assert method.getHolderKind() == SyntheticKind.RETARGET_INTERFACE;
    return appView.dexItemFactory().createMethod(holder, method.getProto(), method.getName());
  }

  public DexMethod getEmulatedInterfaceDispatchMethod(
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
                  emulatedDispatchMethod.getHolderKind(), holderContext, appView);
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
                  SyntheticKind.RETARGET_CLASS,
                  context,
                  appView,
                  classBuilder -> buildHolderDispatchMethod(classBuilder, itfClass, descriptor),
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
            emulatedDispatchMethod.getHolderKind(),
            holderContext,
            appView,
            classBuilder -> buildHolderDispatchMethod(classBuilder, itfClass, descriptor),
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
          .getExistingFixedClass(itfMethod.getHolderKind(), itfContext, appView);
    }
    ClasspathOrLibraryClass context = itfContext.asClasspathOrLibraryClass();
    assert context != null;
    return appView
        .getSyntheticItems()
        .ensureFixedClasspathClass(
            SyntheticKind.RETARGET_INTERFACE,
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
            itfMethod.getHolderKind(),
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
      SCB classBuilder, DexClass itfClass, EmulatedDispatchMethodDescriptor descriptor) {
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
                          ? generateEmulatedDispatchCfCode(descriptor, itfClass, methodSig)
                          : null);
        });
  }

  private CfCode generateEmulatedDispatchCfCode(
      EmulatedDispatchMethodDescriptor descriptor, DexClass itfClass, DexMethod methodSig) {
    DexMethod forwardingMethod = ensureForwardingMethod(descriptor);
    DexMethod itfMethod = getEmulatedInterfaceDispatchMethod(itfClass, descriptor);
    assert descriptor.getDispatchCases().isEmpty();
    return new EmulateDispatchSyntheticCfCodeProvider(
            methodSig.getHolderType(), forwardingMethod, itfMethod, new LinkedHashMap<>(), appView)
        .generateCfCode();
  }
}
