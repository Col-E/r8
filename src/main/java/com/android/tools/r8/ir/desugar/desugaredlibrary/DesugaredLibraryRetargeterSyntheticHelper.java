// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClasspathOrLibraryClass;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.ir.synthetic.EmulateInterfaceSyntheticCfCodeProvider;
import com.android.tools.r8.synthesis.SyntheticClassBuilder;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.DescriptorUtils;
import java.util.Collections;

public class DesugaredLibraryRetargeterSyntheticHelper {

  private final AppView<?> appView;

  public DesugaredLibraryRetargeterSyntheticHelper(AppView<?> appView) {
    this.appView = appView;
  }

  public DexClass ensureEmulatedHolderDispatchMethod(
      DexClassAndMethod emulatedDispatchMethod,
      DesugaredLibraryRetargeterInstructionEventConsumer eventConsumer) {
    assert eventConsumer != null || appView.enableWholeProgramOptimizations();
    DexClass interfaceClass =
        ensureEmulatedInterfaceDispatchMethod(emulatedDispatchMethod, eventConsumer);
    DexMethod itfMethod =
        interfaceClass.lookupMethod(emulatedDispatchMethod.getReference()).getReference();
    DexClass holderDispatch;
    if (appView.options().isDesugaredLibraryCompilation()) {
      holderDispatch =
          appView
              .getSyntheticItems()
              .ensureFixedClass(
                  SyntheticKind.RETARGET_CLASS,
                  emulatedDispatchMethod.getHolder(),
                  appView,
                  classBuilder ->
                      buildHolderDispatchMethod(classBuilder, emulatedDispatchMethod, itfMethod),
                  clazz -> {
                    if (eventConsumer != null) {
                      eventConsumer.acceptDesugaredLibraryRetargeterDispatchProgramClass(clazz);
                    }
                  });
    } else {
      ClasspathOrLibraryClass context =
          emulatedDispatchMethod.getHolder().asClasspathOrLibraryClass();
      assert context != null;
      holderDispatch =
          appView
              .getSyntheticItems()
              .ensureFixedClasspathClass(
                  SyntheticKind.RETARGET_CLASS,
                  context,
                  appView,
                  classBuilder ->
                      buildHolderDispatchMethod(classBuilder, emulatedDispatchMethod, itfMethod),
                  clazz -> {
                    if (eventConsumer != null) {
                      eventConsumer.acceptDesugaredLibraryRetargeterDispatchClasspathClass(clazz);
                    }
                  });
    }
    rewriteType(holderDispatch.type);
    return holderDispatch;
  }

  public DexClass ensureEmulatedInterfaceDispatchMethod(
      DexClassAndMethod emulatedDispatchMethod,
      DesugaredLibraryRetargeterInstructionEventConsumer eventConsumer) {
    assert eventConsumer != null || appView.enableWholeProgramOptimizations();
    DexClass interfaceDispatch;
    if (appView.options().isDesugaredLibraryCompilation()) {
      interfaceDispatch =
          appView
              .getSyntheticItems()
              .ensureFixedClass(
                  SyntheticKind.RETARGET_INTERFACE,
                  emulatedDispatchMethod.getHolder(),
                  appView,
                  classBuilder ->
                      buildInterfaceDispatchMethod(classBuilder, emulatedDispatchMethod),
                  clazz -> {
                    if (eventConsumer != null) {
                      eventConsumer.acceptDesugaredLibraryRetargeterDispatchProgramClass(clazz);
                    }
                  });
    } else {
      ClasspathOrLibraryClass context =
          emulatedDispatchMethod.getHolder().asClasspathOrLibraryClass();
      assert context != null;
      interfaceDispatch =
          appView
              .getSyntheticItems()
              .ensureFixedClasspathClass(
                  SyntheticKind.RETARGET_INTERFACE,
                  context,
                  appView,
                  classBuilder ->
                      buildInterfaceDispatchMethod(classBuilder, emulatedDispatchMethod),
                  clazz -> {
                    if (eventConsumer != null) {
                      eventConsumer.acceptDesugaredLibraryRetargeterDispatchClasspathClass(clazz);
                    }
                  });
    }
    rewriteType(interfaceDispatch.type);
    return interfaceDispatch;
  }

  private void buildInterfaceDispatchMethod(
      SyntheticClassBuilder<?, ?> classBuilder, DexClassAndMethod emulatedDispatchMethod) {
    classBuilder
        .setInterface()
        .addMethod(
            methodBuilder -> {
              MethodAccessFlags flags =
                  MethodAccessFlags.fromSharedAccessFlags(
                      Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SYNTHETIC,
                      false);
              methodBuilder
                  .setName(emulatedDispatchMethod.getName())
                  .setProto(emulatedDispatchMethod.getProto())
                  .setAccessFlags(flags);
            });
  }

  private <SCB extends SyntheticClassBuilder<?, ?>> void buildHolderDispatchMethod(
      SCB classBuilder, DexClassAndMethod emulatedDispatchMethod, DexMethod itfMethod) {
    classBuilder.addMethod(
        methodBuilder -> {
          DexMethod desugarMethod =
              appView
                  .options()
                  .desugaredLibraryConfiguration
                  .retargetMethod(emulatedDispatchMethod, appView);
          assert desugarMethod
              != null; // This method is reached only for retarget core lib members.
          methodBuilder
              .setName(emulatedDispatchMethod.getName())
              .setProto(desugarMethod.proto)
              .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
              .setCode(
                  methodSig ->
                      new EmulateInterfaceSyntheticCfCodeProvider(
                              methodSig.getHolderType(),
                              emulatedDispatchMethod.getHolderType(),
                              desugarMethod,
                              itfMethod,
                              Collections.emptyList(),
                              appView)
                          .generateCfCode());
        });
  }

  private void rewriteType(DexType type) {
    String newName =
        appView.options().desugaredLibraryConfiguration.convertJavaNameToDesugaredLibrary(type);
    DexType newType =
        appView.dexItemFactory().createType(DescriptorUtils.javaTypeToDescriptor(newName));
    appView.rewritePrefix.rewriteType(type, newType);
  }
}
