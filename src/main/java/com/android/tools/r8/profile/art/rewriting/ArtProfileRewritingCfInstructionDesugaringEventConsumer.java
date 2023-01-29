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
    additionsCollection.addRulesIfContextIsInProfile(
        context, backportedMethod, backportedMethod.getHolder());
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
  public void acceptCompanionMethod(ProgramMethod method, ProgramMethod companionMethod) {
    parent.acceptCompanionMethod(method, companionMethod);
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
    additionsCollection.addRulesIfContextIsInProfile(
        info.getVirtualMethod(), info.getNewDirectMethod());
    parent.acceptInvokeSpecialBridgeInfo(info);
  }

  @Override
  public void acceptInvokeStaticInterfaceOutliningMethod(
      ProgramMethod method, ProgramMethod context) {
    parent.acceptInvokeStaticInterfaceOutliningMethod(method, context);
  }

  @Override
  public void acceptLambdaClass(LambdaClass lambdaClass, ProgramMethod context) {
    parent.acceptLambdaClass(lambdaClass, context);
  }

  @Override
  public void acceptNestConstructorBridge(
      ProgramMethod target,
      ProgramMethod bridge,
      DexProgramClass argumentClass,
      DexClassAndMethod context) {
    assert context.isProgramMethod();
    additionsCollection.addRulesIfContextIsInProfile(
        context.asProgramMethod(), argumentClass, bridge);
    parent.acceptNestConstructorBridge(target, bridge, argumentClass, context);
  }

  @Override
  public void acceptNestFieldGetBridge(
      ProgramField target, ProgramMethod bridge, DexClassAndMethod context) {
    assert context.isProgramMethod();
    additionsCollection.addRulesIfContextIsInProfile(context.asProgramMethod(), bridge);
    parent.acceptNestFieldGetBridge(target, bridge, context);
  }

  @Override
  public void acceptNestFieldPutBridge(
      ProgramField target, ProgramMethod bridge, DexClassAndMethod context) {
    assert context.isProgramMethod();
    additionsCollection.addRulesIfContextIsInProfile(context.asProgramMethod(), bridge);
    parent.acceptNestFieldPutBridge(target, bridge, context);
  }

  @Override
  public void acceptNestMethodBridge(
      ProgramMethod target, ProgramMethod bridge, DexClassAndMethod context) {
    assert context.isProgramMethod();
    additionsCollection.addRulesIfContextIsInProfile(context.asProgramMethod(), bridge);
    parent.acceptNestMethodBridge(target, bridge, context);
  }

  @Override
  public void acceptOutlinedMethod(ProgramMethod outlinedMethod, ProgramMethod context) {
    additionsCollection.addRulesIfContextIsInProfile(
        context, outlinedMethod, outlinedMethod.getHolder());
    parent.acceptOutlinedMethod(outlinedMethod, context);
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
  public void acceptThrowMethod(ProgramMethod method, ProgramMethod context) {
    parent.acceptThrowMethod(method, context);
  }

  @Override
  public void acceptTwrCloseResourceMethod(ProgramMethod closeMethod, ProgramMethod context) {
    additionsCollection.addRulesIfContextIsInProfile(context, closeMethod, closeMethod.getHolder());
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
