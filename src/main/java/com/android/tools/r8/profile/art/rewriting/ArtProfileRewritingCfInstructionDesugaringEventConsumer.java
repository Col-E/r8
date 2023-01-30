// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.rewriting;

import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.LambdaClass;
import com.android.tools.r8.ir.desugar.constantdynamic.ConstantDynamicClass;
import com.android.tools.r8.ir.desugar.invokespecial.InvokeSpecialBridgeInfo;
import java.util.List;

public class ArtProfileRewritingCfInstructionDesugaringEventConsumer
    extends CfInstructionDesugaringEventConsumer {

  private final ConcreteArtProfileCollectionAdditions additionsCollection;
  private final CfInstructionDesugaringEventConsumer parent;

  private ArtProfileRewritingCfInstructionDesugaringEventConsumer(
      ConcreteArtProfileCollectionAdditions additionsCollection,
      CfInstructionDesugaringEventConsumer parent) {
    this.additionsCollection = additionsCollection;
    this.parent = parent;
  }

  public static CfInstructionDesugaringEventConsumer attach(
      ArtProfileCollectionAdditions artProfileCollectionAdditions,
      CfInstructionDesugaringEventConsumer eventConsumer) {
    if (artProfileCollectionAdditions.isNop()) {
      return eventConsumer;
    }
    return new ArtProfileRewritingCfInstructionDesugaringEventConsumer(
        artProfileCollectionAdditions.asConcrete(), eventConsumer);
  }

  @Override
  public void acceptAPIConversion(ProgramMethod method) {
    parent.acceptAPIConversion(method);
  }

  @Override
  public void acceptBackportedClass(DexProgramClass backportedClass, ProgramMethod context) {
    parent.acceptBackportedClass(backportedClass, context);
  }

  @Override
  public void acceptBackportedMethod(ProgramMethod backportedMethod, ProgramMethod context) {
    additionsCollection.applyIfContextIsInProfile(
        context,
        additionsBuilder ->
            additionsBuilder.addRule(backportedMethod).addRule(backportedMethod.getHolder()));
    parent.acceptBackportedMethod(backportedMethod, context);
  }

  @Override
  public void acceptClasspathEmulatedInterface(DexClasspathClass clazz) {
    parent.acceptClasspathEmulatedInterface(clazz);
  }

  @Override
  public void acceptCollectionConversion(ProgramMethod arrayConversion) {
    parent.acceptCollectionConversion(arrayConversion);
  }

  @Override
  public void acceptCompanionClassClinit(ProgramMethod method) {
    parent.acceptCompanionClassClinit(method);
  }

  @Override
  public void acceptConstantDynamicClass(ConstantDynamicClass lambdaClass, ProgramMethod context) {
    parent.acceptConstantDynamicClass(lambdaClass, context);
  }

  @Override
  public void acceptCovariantRetargetMethod(ProgramMethod method) {
    parent.acceptCovariantRetargetMethod(method);
  }

  @Override
  public void acceptDefaultAsCompanionMethod(ProgramMethod method, ProgramMethod companionMethod) {
    additionsCollection.applyIfContextIsInProfile(
        method,
        additionsBuilder ->
            additionsBuilder.addRule(companionMethod).addRule(companionMethod.getHolder()));
    parent.acceptDefaultAsCompanionMethod(method, companionMethod);
  }

  @Override
  public void acceptDesugaredLibraryRetargeterDispatchClasspathClass(DexClasspathClass clazz) {
    parent.acceptDesugaredLibraryRetargeterDispatchClasspathClass(clazz);
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
  public void acceptInvokeSpecialBridgeInfo(InvokeSpecialBridgeInfo info) {
    additionsCollection.applyIfContextIsInProfile(
        info.getVirtualMethod(),
        additionsBuilder -> additionsBuilder.addRule(info.getNewDirectMethod()));
    parent.acceptInvokeSpecialBridgeInfo(info);
  }

  @Override
  public void acceptInvokeStaticInterfaceOutliningMethod(
      ProgramMethod method, ProgramMethod context) {
    parent.acceptInvokeStaticInterfaceOutliningMethod(method, context);
  }

  @Override
  public void acceptLambdaClass(LambdaClass lambdaClass, ProgramMethod context) {
    addLambdaClassAndInstanceInitializersIfSynthesizingContextIsInProfile(lambdaClass, context);
    addLambdaVirtualMethodsIfLambdaImplementationIsInProfile(lambdaClass);
    parent.acceptLambdaClass(lambdaClass, context);
  }

  private void addLambdaClassAndInstanceInitializersIfSynthesizingContextIsInProfile(
      LambdaClass lambdaClass, ProgramMethod context) {
    additionsCollection.applyIfContextIsInProfile(
        context,
        additionsBuilder -> {
          DexProgramClass lambdaProgramClass = lambdaClass.getLambdaProgramClass();
          additionsBuilder.addRule(lambdaProgramClass);
          if (lambdaProgramClass.hasClassInitializer()) {
            additionsBuilder.addRule(lambdaProgramClass.getProgramClassInitializer());
          }
          lambdaProgramClass.forEachProgramInstanceInitializer(additionsBuilder::addRule);
        });
  }

  private void addLambdaVirtualMethodsIfLambdaImplementationIsInProfile(LambdaClass lambdaClass) {
    additionsCollection.applyIfContextIsInProfile(
        lambdaClass.getTarget().getImplementationMethod(),
        additionsBuilder -> {
          lambdaClass
              .getLambdaProgramClass()
              .forEachProgramVirtualMethod(additionsBuilder::addRule);
          if (lambdaClass.getTarget().getCallTarget()
              != lambdaClass.getTarget().getImplementationMethod()) {
            additionsBuilder.addRule(lambdaClass.getTarget().getCallTarget());
          }
        });
  }

  @Override
  public void acceptNestConstructorBridge(
      ProgramMethod target,
      ProgramMethod bridge,
      DexProgramClass argumentClass,
      DexClassAndMethod context) {
    additionsCollection.applyIfContextIsInProfile(
        context, additionsBuilder -> additionsBuilder.addRule(argumentClass).addRule(bridge));
    parent.acceptNestConstructorBridge(target, bridge, argumentClass, context);
  }

  @Override
  public void acceptNestFieldGetBridge(
      ProgramField target, ProgramMethod bridge, DexClassAndMethod context) {
    additionsCollection.applyIfContextIsInProfile(
        context, additionsBuilder -> additionsBuilder.addRule(bridge));
    parent.acceptNestFieldGetBridge(target, bridge, context);
  }

  @Override
  public void acceptNestFieldPutBridge(
      ProgramField target, ProgramMethod bridge, DexClassAndMethod context) {
    additionsCollection.applyIfContextIsInProfile(
        context, additionsBuilder -> additionsBuilder.addRule(bridge));
    parent.acceptNestFieldPutBridge(target, bridge, context);
  }

  @Override
  public void acceptNestMethodBridge(
      ProgramMethod target, ProgramMethod bridge, DexClassAndMethod context) {
    additionsCollection.applyIfContextIsInProfile(
        context, additionsBuilder -> additionsBuilder.addRule(bridge));
    parent.acceptNestMethodBridge(target, bridge, context);
  }

  @Override
  public void acceptOutlinedMethod(ProgramMethod outlinedMethod, ProgramMethod context) {
    additionsCollection.applyIfContextIsInProfile(
        context,
        additionsBuilder ->
            additionsBuilder.addRule(outlinedMethod).addRule(outlinedMethod.getHolder()));
    parent.acceptOutlinedMethod(outlinedMethod, context);
  }

  @Override
  public void acceptPrivateAsCompanionMethod(ProgramMethod method, ProgramMethod companionMethod) {
    additionsCollection.applyIfContextIsInProfile(
        method,
        additionsBuilder ->
            additionsBuilder.addRule(companionMethod).addRule(companionMethod.getHolder()));
    parent.acceptPrivateAsCompanionMethod(method, companionMethod);
  }

  @Override
  public void acceptRecordClass(DexProgramClass recordClass) {
    parent.acceptRecordClass(recordClass);
  }

  @Override
  public void acceptRecordMethod(ProgramMethod method) {
    parent.acceptRecordMethod(method);
  }

  @Override
  public void acceptStaticAsCompanionMethod(ProgramMethod method, ProgramMethod companionMethod) {
    additionsCollection.applyIfContextIsInProfile(
        method,
        additionsBuilder ->
            additionsBuilder.addRule(companionMethod).addRule(companionMethod.getHolder()));
    parent.acceptStaticAsCompanionMethod(method, companionMethod);
  }

  @Override
  public void acceptThrowMethod(ProgramMethod method, ProgramMethod context) {
    parent.acceptThrowMethod(method, context);
  }

  @Override
  public void acceptTwrCloseResourceMethod(ProgramMethod closeMethod, ProgramMethod context) {
    additionsCollection.applyIfContextIsInProfile(
        context,
        additionsBuilder -> additionsBuilder.addRule(closeMethod).addRule(closeMethod.getHolder()));
    parent.acceptTwrCloseResourceMethod(closeMethod, context);
  }

  @Override
  public void acceptVarHandleDesugaringClass(DexProgramClass varHandleClass) {
    parent.acceptVarHandleDesugaringClass(varHandleClass);
  }

  @Override
  public void acceptWrapperClasspathClass(DexClasspathClass clazz) {
    parent.acceptWrapperClasspathClass(clazz);
  }

  @Override
  public List<ProgramMethod> finalizeDesugaring() {
    return parent.finalizeDesugaring();
  }

  @Override
  public boolean verifyNothingToFinalize() {
    assert parent.verifyNothingToFinalize();
    return true;
  }
}
