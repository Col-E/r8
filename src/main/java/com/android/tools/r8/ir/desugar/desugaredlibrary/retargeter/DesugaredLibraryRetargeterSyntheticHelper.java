// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClasspathOrLibraryClass;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.DesugaredLibraryRetargeterSynthesizerEventConsumer.DesugaredLibraryRetargeterInstructionEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.DesugaredLibraryRetargeterSynthesizerEventConsumer.DesugaredLibraryRetargeterL8SynthesizerEventConsumer;
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
    assert eventConsumer != null;
    if (appView.options().isDesugaredLibraryCompilation()) {
      return appView
          .getSyntheticItems()
          .getExistingFixedClass(
              SyntheticKind.RETARGET_CLASS, emulatedDispatchMethod.getHolder(), appView);
    }
    DexClass interfaceClass =
        ensureEmulatedInterfaceDispatchMethod(emulatedDispatchMethod, eventConsumer);
    DexMethod itfMethod =
        interfaceClass.lookupMethod(emulatedDispatchMethod.getReference()).getReference();
    ClasspathOrLibraryClass context =
        emulatedDispatchMethod.getHolder().asClasspathOrLibraryClass();
    assert context != null;
    return appView
        .getSyntheticItems()
        .ensureFixedClasspathClass(
            SyntheticKind.RETARGET_CLASS,
            context,
            appView,
            classBuilder ->
                buildHolderDispatchMethod(classBuilder, emulatedDispatchMethod, itfMethod),
            clazz -> {
              eventConsumer.acceptDesugaredLibraryRetargeterDispatchClasspathClass(clazz);
              rewriteType(clazz.type);
            });
  }

  public void ensureProgramEmulatedHolderDispatchMethod(
      DexClassAndMethod emulatedDispatchMethod,
      DesugaredLibraryRetargeterL8SynthesizerEventConsumer eventConsumer) {
    assert eventConsumer != null;
    assert appView.options().isDesugaredLibraryCompilation();
    DexClass interfaceClass =
        ensureEmulatedInterfaceDispatchMethod(emulatedDispatchMethod, eventConsumer);
    DexMethod itfMethod =
        interfaceClass.lookupMethod(emulatedDispatchMethod.getReference()).getReference();
    appView
        .getSyntheticItems()
        .ensureFixedClass(
            SyntheticKind.RETARGET_CLASS,
            emulatedDispatchMethod.getHolder(),
            appView,
            classBuilder ->
                buildHolderDispatchMethod(classBuilder, emulatedDispatchMethod, itfMethod),
            clazz -> {
              eventConsumer.acceptDesugaredLibraryRetargeterDispatchProgramClass(clazz);
              rewriteType(clazz.type);
            });
  }

  public DexClass ensureEmulatedInterfaceDispatchMethod(
      DexClassAndMethod emulatedDispatchMethod,
      DesugaredLibraryRetargeterInstructionEventConsumer eventConsumer) {
    assert eventConsumer != null;
    if (appView.options().isDesugaredLibraryCompilation()) {
      return appView
          .getSyntheticItems()
          .getExistingFixedClass(
              SyntheticKind.RETARGET_INTERFACE, emulatedDispatchMethod.getHolder(), appView);
    }
    ClasspathOrLibraryClass context =
        emulatedDispatchMethod.getHolder().asClasspathOrLibraryClass();
    assert context != null;
    return appView
        .getSyntheticItems()
        .ensureFixedClasspathClass(
            SyntheticKind.RETARGET_INTERFACE,
            context,
            appView,
            classBuilder -> buildInterfaceDispatchMethod(classBuilder, emulatedDispatchMethod),
            clazz -> {
              eventConsumer.acceptDesugaredLibraryRetargeterDispatchClasspathClass(clazz);
              rewriteType(clazz.type);
            });
  }

  public DexClass ensureEmulatedInterfaceDispatchMethod(
      DexClassAndMethod emulatedDispatchMethod,
      DesugaredLibraryRetargeterL8SynthesizerEventConsumer eventConsumer) {
    assert appView.options().isDesugaredLibraryCompilation();
    assert eventConsumer != null;
    return appView
        .getSyntheticItems()
        .ensureFixedClass(
            SyntheticKind.RETARGET_INTERFACE,
            emulatedDispatchMethod.getHolder(),
            appView,
            classBuilder -> buildInterfaceDispatchMethod(classBuilder, emulatedDispatchMethod),
            clazz -> {
              eventConsumer.acceptDesugaredLibraryRetargeterDispatchProgramClass(clazz);
              rewriteType(clazz.type);
            });
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
                  // Will be traced by the enqueuer.
                  .disableAndroidApiLevelCheck()
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
                  .desugaredLibrarySpecification
                  .retargetMethod(emulatedDispatchMethod, appView);
          assert desugarMethod
              != null; // This method is reached only for retarget core lib members.
          methodBuilder
              .setName(emulatedDispatchMethod.getName())
              .setProto(desugarMethod.proto)
              .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
              // Will be traced by the enqueuer.
              .disableAndroidApiLevelCheck()
              .setCode(
                  methodSig ->
                      appView.options().isDesugaredLibraryCompilation()
                          ? new EmulateInterfaceSyntheticCfCodeProvider(
                                  methodSig.getHolderType(),
                                  emulatedDispatchMethod.getHolderType(),
                                  desugarMethod,
                                  itfMethod,
                                  Collections.emptyList(),
                                  appView)
                              .generateCfCode()
                          : null);
        });
  }

  private void rewriteType(DexType type) {
    String newName =
        appView.options().desugaredLibrarySpecification.convertJavaNameToDesugaredLibrary(type);
    DexType newType =
        appView.dexItemFactory().createType(DescriptorUtils.javaTypeToDescriptor(newName));
    appView.rewritePrefix.rewriteType(type, newType);
  }
}
