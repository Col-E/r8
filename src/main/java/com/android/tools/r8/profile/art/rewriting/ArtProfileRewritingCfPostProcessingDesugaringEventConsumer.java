// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.rewriting;

import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class ArtProfileRewritingCfPostProcessingDesugaringEventConsumer
    extends CfPostProcessingDesugaringEventConsumer {

  private final ConcreteArtProfileCollectionAdditions additionsCollection;
  private final CfPostProcessingDesugaringEventConsumer parent;

  private ArtProfileRewritingCfPostProcessingDesugaringEventConsumer(
      ConcreteArtProfileCollectionAdditions additionsCollection,
      CfPostProcessingDesugaringEventConsumer parent) {
    this.additionsCollection = additionsCollection;
    this.parent = parent;
  }

  public static CfPostProcessingDesugaringEventConsumer attach(
      ArtProfileCollectionAdditions artProfileCollectionAdditions,
      CfPostProcessingDesugaringEventConsumer eventConsumer) {
    if (artProfileCollectionAdditions.isNop()) {
      return eventConsumer;
    }
    return new ArtProfileRewritingCfPostProcessingDesugaringEventConsumer(
        artProfileCollectionAdditions.asConcrete(), eventConsumer);
  }

  @Override
  public void acceptAPIConversionCallback(
      ProgramMethod callbackMethod, ProgramMethod convertedMethod) {
    additionsCollection.addMethodIfContextIsInProfile(callbackMethod, convertedMethod);
    parent.acceptAPIConversionCallback(callbackMethod, convertedMethod);
  }

  @Override
  public void acceptCollectionConversion(ProgramMethod arrayConversion) {
    parent.acceptCollectionConversion(arrayConversion);
  }

  @Override
  public void acceptCovariantRetargetMethod(ProgramMethod method) {
    parent.acceptCovariantRetargetMethod(method);
  }

  @Override
  public void acceptDesugaredLibraryRetargeterDispatchClasspathClass(DexClasspathClass clazz) {
    parent.acceptDesugaredLibraryRetargeterDispatchClasspathClass(clazz);
  }

  @Override
  public void acceptDesugaredLibraryRetargeterForwardingMethod(ProgramMethod method) {
    parent.acceptDesugaredLibraryRetargeterForwardingMethod(method);
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
    additionsCollection.addMethodIfContextIsInProfile(method, baseMethod, emptyConsumer());
    parent.acceptInterfaceMethodDesugaringForwardingMethod(method, baseMethod);
  }

  @Override
  public void acceptThrowingMethod(ProgramMethod method, DexType errorType) {
    parent.acceptThrowingMethod(method, errorType);
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
