// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.rewriting;

import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult.FailedResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.EmulatedDispatchMethodDescriptor;
import com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper;
import com.android.tools.r8.profile.AbstractProfileMethodRule;
import com.android.tools.r8.profile.art.ArtProfileOptions;
import com.android.tools.r8.utils.BooleanBox;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class ProfileRewritingCfPostProcessingDesugaringEventConsumer
    extends CfPostProcessingDesugaringEventConsumer {

  private final ConcreteProfileCollectionAdditions additionsCollection;
  private final ArtProfileOptions options;
  private final CfPostProcessingDesugaringEventConsumer parent;

  private ProfileRewritingCfPostProcessingDesugaringEventConsumer(
      ConcreteProfileCollectionAdditions additionsCollection,
      ArtProfileOptions options,
      CfPostProcessingDesugaringEventConsumer parent) {
    this.additionsCollection = additionsCollection;
    this.options = options;
    this.parent = parent;
  }

  public static CfPostProcessingDesugaringEventConsumer attach(
      AppView<?> appView,
      ProfileCollectionAdditions profileCollectionAdditions,
      CfPostProcessingDesugaringEventConsumer eventConsumer) {
    if (profileCollectionAdditions.isNop()) {
      return eventConsumer;
    }
    return new ProfileRewritingCfPostProcessingDesugaringEventConsumer(
        profileCollectionAdditions.asConcrete(),
        appView.options().getArtProfileOptions(),
        eventConsumer);
  }

  @Override
  public void acceptAPIConversionCallback(
      ProgramMethod callbackMethod, ProgramMethod convertedMethod) {
    additionsCollection.addMethodIfContextIsInProfile(callbackMethod, convertedMethod);
    parent.acceptAPIConversionCallback(callbackMethod, convertedMethod);
  }

  @Override
  public void acceptCollectionConversion(ProgramMethod arrayConversion, ProgramMethod context) {
    additionsCollection.addMethodAndHolderIfContextIsInProfile(arrayConversion, context);
    parent.acceptCollectionConversion(arrayConversion, context);
  }

  @Override
  @SuppressWarnings("ArgumentSelectionDefectChecker")
  public void acceptCovariantRetargetMethod(ProgramMethod method, ProgramMethod context) {
    additionsCollection.addMethodAndHolderIfContextIsInProfile(context, method);
    parent.acceptCovariantRetargetMethod(method, context);
  }

  @Override
  public void acceptDesugaredLibraryRetargeterDispatchClasspathClass(DexClasspathClass clazz) {
    parent.acceptDesugaredLibraryRetargeterDispatchClasspathClass(clazz);
  }

  @Override
  public void acceptDesugaredLibraryRetargeterForwardingMethod(
      ProgramMethod method, EmulatedDispatchMethodDescriptor descriptor) {
    if (options.isIncludingDesugaredLibraryRetargeterForwardingMethodsUnconditionally()) {
      additionsCollection.accept(
          additions ->
              additions.addMethodRule(method, AbstractProfileMethodRule.Builder::setIsStartup));
    }
    parent.acceptDesugaredLibraryRetargeterForwardingMethod(method, descriptor);
  }

  @Override
  public void acceptEmulatedInterfaceMarkerInterface(
      DexProgramClass clazz, DexClasspathClass newInterface) {
    parent.acceptEmulatedInterfaceMarkerInterface(clazz, newInterface);
  }

  @Override
  public void acceptEnumConversionClasspathClass(DexClasspathClass clazz) {
    parent.acceptEnumConversionClasspathClass(clazz);
  }

  @Override
  public void acceptGenericApiConversionStub(DexClasspathClass dexClasspathClass) {
    parent.acceptGenericApiConversionStub(dexClasspathClass);
  }

  @Override
  public void acceptInterfaceInjection(DexProgramClass clazz, DexClass newInterface) {
    parent.acceptInterfaceInjection(clazz, newInterface);
  }

  @Override
  public void acceptInterfaceMethodDesugaringForwardingMethod(
      ProgramMethod method, DexClassAndMethod baseMethod) {
    additionsCollection.addMethodIfContextIsInProfile(method, baseMethod);
    parent.acceptInterfaceMethodDesugaringForwardingMethod(method, baseMethod);
  }

  @Override
  public void acceptThrowingMethod(
      ProgramMethod method, DexType errorType, FailedResolutionResult resolutionResult) {
    if (options.isIncludingThrowingMethods()) {
      BooleanBox seenMethodCausingError = new BooleanBox();
      resolutionResult.forEachFailureDependency(
          emptyConsumer(),
          methodCausingError -> {
            additionsCollection.applyIfContextIsInProfile(
                methodCausingError.getReference(),
                additionsBuilder -> additionsBuilder.addRule(method));
            seenMethodCausingError.set();
          });
      if (seenMethodCausingError.isFalse()) {
        additionsCollection.applyIfContextIsInProfile(
            method.getHolder(),
            additionsBuilder -> additionsBuilder.addMethodRule(method.getReference()));
      }
    }
    parent.acceptThrowingMethod(method, errorType, resolutionResult);
  }

  @Override
  public void acceptWrapperClasspathClass(DexClasspathClass clazz) {
    parent.acceptWrapperClasspathClass(clazz);
  }

  @Override
  public Set<DexMethod> getNewlyLiveMethods() {
    return parent.getNewlyLiveMethods();
  }

  @Override
  public void finalizeDesugaring() throws ExecutionException {
    parent.finalizeDesugaring();
  }

  @Override
  public void warnMissingInterface(
      DexProgramClass context, DexType missing, InterfaceDesugaringSyntheticHelper helper) {
    parent.warnMissingInterface(context, missing, helper);
  }
}
