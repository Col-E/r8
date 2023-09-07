// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.rewriting;

import static com.android.tools.r8.profile.rewriting.ProfileRewritingVarHandleDesugaringEventConsumerUtils.handleVarHandleDesugaringClassContext;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.LambdaClass;
import com.android.tools.r8.ir.desugar.LambdaClass.Target;
import com.android.tools.r8.ir.desugar.constantdynamic.ConstantDynamicClass;
import com.android.tools.r8.ir.desugar.invokespecial.InvokeSpecialBridgeInfo;
import com.android.tools.r8.ir.desugar.nest.NestBasedAccessDesugaringEventConsumer;
import java.util.List;

public class ProfileRewritingCfInstructionDesugaringEventConsumer
    extends CfInstructionDesugaringEventConsumer {

  private final AppView<?> appView;
  private final ConcreteProfileCollectionAdditions additionsCollection;
  private final CfInstructionDesugaringEventConsumer parent;

  private final NestBasedAccessDesugaringEventConsumer nestBasedAccessDesugaringEventConsumer;

  private ProfileRewritingCfInstructionDesugaringEventConsumer(
      AppView<?> appView,
      ConcreteProfileCollectionAdditions additionsCollection,
      CfInstructionDesugaringEventConsumer parent) {
    this.appView = appView;
    this.additionsCollection = additionsCollection;
    this.parent = parent;
    this.nestBasedAccessDesugaringEventConsumer =
        ProfileRewritingNestBasedAccessDesugaringEventConsumer.attach(
            additionsCollection, NestBasedAccessDesugaringEventConsumer.empty());
  }

  public static CfInstructionDesugaringEventConsumer attach(
      AppView<?> appView,
      ProfileCollectionAdditions profileCollectionAdditions,
      CfInstructionDesugaringEventConsumer eventConsumer) {
    if (profileCollectionAdditions.isNop()) {
      return eventConsumer;
    }
    return new ProfileRewritingCfInstructionDesugaringEventConsumer(
        appView, profileCollectionAdditions.asConcrete(), eventConsumer);
  }

  @Override
  public void acceptAPIConversionOutline(ProgramMethod method, ProgramMethod context) {
    additionsCollection.addMethodAndHolderIfContextIsInProfile(method, context);
    parent.acceptAPIConversionOutline(method, context);
  }

  @Override
  public void acceptBackportedClass(DexProgramClass backportedClass, ProgramMethod context) {
    if (appView.options().getArtProfileOptions().isIncludingBackportedClasses()) {
      additionsCollection.applyIfContextIsInProfile(
          context,
          additionsBuilder -> {
            additionsBuilder.addRule(backportedClass);
            backportedClass.forEachProgramMethod(additionsBuilder::addRule);
          });
    }
    parent.acceptBackportedClass(backportedClass, context);
  }

  @Override
  public void acceptBackportedMethod(ProgramMethod backportedMethod, ProgramMethod context) {
    additionsCollection.addMethodAndHolderIfContextIsInProfile(backportedMethod, context);
    parent.acceptBackportedMethod(backportedMethod, context);
  }

  @Override
  public void acceptClasspathEmulatedInterface(DexClasspathClass clazz) {
    parent.acceptClasspathEmulatedInterface(clazz);
  }

  @Override
  public void acceptCollectionConversion(ProgramMethod arrayConversion, ProgramMethod context) {
    additionsCollection.addMethodAndHolderIfContextIsInProfile(arrayConversion, context);
    parent.acceptCollectionConversion(arrayConversion, context);
  }

  @Override
  public void acceptCompanionClassClinit(ProgramMethod method, ProgramMethod companionMethod) {
    additionsCollection.addMethodAndHolderIfContextIsInProfile(companionMethod, method);
    parent.acceptCompanionClassClinit(method, companionMethod);
  }

  @Override
  public void acceptConstantDynamicClass(
      ConstantDynamicClass constantDynamicClass, ProgramMethod context) {
    if (appView.options().getArtProfileOptions().isIncludingConstantDynamicClass()) {
      additionsCollection.applyIfContextIsInProfile(
          context,
          additionsBuilder -> {
            DexProgramClass clazz = constantDynamicClass.getConstantDynamicProgramClass();
            additionsBuilder.addRule(clazz);
            clazz.forEachProgramMethod(additionsBuilder::addRule);
          });
    }
    parent.acceptConstantDynamicClass(constantDynamicClass, context);
  }

  @Override
  public void acceptConstantDynamicRewrittenBootstrapMethod(
      ProgramMethod bootstrapMethod, DexMethod oldSignature) {
    additionsCollection.applyIfContextIsInProfile(
        oldSignature,
        additionsBuilder ->
            additionsBuilder
                .addRule(bootstrapMethod)
                .removeMovedMethodRule(oldSignature, bootstrapMethod));
    parent.acceptConstantDynamicRewrittenBootstrapMethod(bootstrapMethod, oldSignature);
  }

  @Override
  public void acceptCovariantRetargetMethod(ProgramMethod method, ProgramMethod context) {
    additionsCollection.addMethodAndHolderIfContextIsInProfile(method, context);
    parent.acceptCovariantRetargetMethod(method, context);
  }

  @Override
  public void acceptDefaultAsCompanionMethod(ProgramMethod method, ProgramMethod companionMethod) {
    additionsCollection.applyIfContextIsInProfile(
        method,
        additionsBuilder -> {
          additionsBuilder.addRule(companionMethod).addRule(companionMethod.getHolder());
          companionMethod.getHolder().acceptProgramClassInitializer(additionsBuilder::addRule);
        });
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
    additionsCollection.addMethodIfContextIsInProfile(
        info.getNewDirectMethod(), info.getVirtualMethod());
    parent.acceptInvokeSpecialBridgeInfo(info);
  }

  @Override
  public void acceptInvokeStaticInterfaceOutliningMethod(
      ProgramMethod method, ProgramMethod context) {
    additionsCollection.addMethodAndHolderIfContextIsInProfile(method, context);
    parent.acceptInvokeStaticInterfaceOutliningMethod(method, context);
  }

  @Override
  public void acceptLambdaClass(LambdaClass lambdaClass, ProgramMethod context) {
    addLambdaClassAndInstanceInitializersIfSynthesizingContextIsInProfile(lambdaClass, context);
    addLambdaFactoryMethodIfSynthesizingContextIsInProfile(lambdaClass, context);
    addLambdaVirtualMethodsIfLambdaImplementationIsInProfile(lambdaClass, context);

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
          if (appView.options().testing.alwaysGenerateLambdaFactoryMethods) {
            lambdaProgramClass.forEachProgramStaticMethod(additionsBuilder::addRule);
          }
        });
  }

  @SuppressWarnings("ReferenceEquality")
  private void addLambdaFactoryMethodIfSynthesizingContextIsInProfile(
      LambdaClass lambdaClass, ProgramMethod context) {
    if (lambdaClass.hasFactoryMethod()) {
      additionsCollection.applyIfContextIsInProfile(
          context,
          additionsBuilder -> additionsBuilder.addMethodRule(lambdaClass.getFactoryMethod()));
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private void addLambdaVirtualMethodsIfLambdaImplementationIsInProfile(
      LambdaClass lambdaClass, ProgramMethod context) {
    Target target = lambdaClass.getTarget();
    if (shouldConservativelyAddLambdaVirtualMethodsIfLambdaInstantiated(lambdaClass, context)) {
      additionsCollection.applyIfContextIsInProfile(
          context,
          additionsBuilder -> {
            lambdaClass
                .getLambdaProgramClass()
                .forEachProgramVirtualMethod(additionsBuilder::addRule);
            if (target.getCallTarget() != target.getImplementationMethod()) {
              additionsBuilder.addRule(target.getCallTarget());
            }
          });
    } else {
      additionsCollection.applyIfContextIsInProfile(
          target.getImplementationMethod(),
          additionsBuilder -> {
            lambdaClass
                .getLambdaProgramClass()
                .forEachProgramVirtualMethod(additionsBuilder::addRule);
            if (target.getCallTarget() != target.getImplementationMethod()) {
              additionsBuilder.addRule(target.getCallTarget());
            }
          });
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean shouldConservativelyAddLambdaVirtualMethodsIfLambdaInstantiated(
      LambdaClass lambdaClass, ProgramMethod context) {
    Target target = lambdaClass.getTarget();
    if (target.getInvokeType().isInterface() || target.getInvokeType().isVirtual()) {
      return true;
    }
    if (target.getImplementationMethod().getHolderType() == context.getHolderType()) {
      // Direct call to the same class. Only add virtual methods if the callee is in the profile.
      return false;
    }
    if (appView.hasClassHierarchy()) {
      DexClassAndMethod resolutionResult =
          appView
              .appInfoWithClassHierarchy()
              .resolveMethod(target.getImplementationMethod(), target.isInterface())
              .getResolutionPair();
      if (resolutionResult != null && resolutionResult.isProgramMethod()) {
        // Direct call to other method in the app. Only add virtual methods if the callee is in the
        // profile.
        return false;
      }
      // The profile does not contain non-program items. Conservatively treat the call target as
      // being executed.
      return true;
    } else {
      // Should not lookup definitions outside the current context.
      return true;
    }
  }

  @Override
  public void acceptNestConstructorBridge(
      ProgramMethod target,
      ProgramMethod bridge,
      DexProgramClass argumentClass,
      DexClassAndMethod context) {
    nestBasedAccessDesugaringEventConsumer.acceptNestConstructorBridge(
        target, bridge, argumentClass, context);
    parent.acceptNestConstructorBridge(target, bridge, argumentClass, context);
  }

  @Override
  public void acceptNestFieldGetBridge(
      ProgramField target, ProgramMethod bridge, DexClassAndMethod context) {
    nestBasedAccessDesugaringEventConsumer.acceptNestFieldGetBridge(target, bridge, context);
    parent.acceptNestFieldGetBridge(target, bridge, context);
  }

  @Override
  public void acceptNestFieldPutBridge(
      ProgramField target, ProgramMethod bridge, DexClassAndMethod context) {
    nestBasedAccessDesugaringEventConsumer.acceptNestFieldPutBridge(target, bridge, context);
    parent.acceptNestFieldPutBridge(target, bridge, context);
  }

  @Override
  public void acceptNestMethodBridge(
      ProgramMethod target, ProgramMethod bridge, DexClassAndMethod context) {
    nestBasedAccessDesugaringEventConsumer.acceptNestMethodBridge(target, bridge, context);
    parent.acceptNestMethodBridge(target, bridge, context);
  }

  @Override
  public void acceptOutlinedMethod(ProgramMethod outlinedMethod, ProgramMethod context) {
    additionsCollection.addMethodAndHolderIfContextIsInProfile(outlinedMethod, context);
    parent.acceptOutlinedMethod(outlinedMethod, context);
  }

  @Override
  public void acceptPrivateAsCompanionMethod(ProgramMethod method, ProgramMethod companionMethod) {
    additionsCollection.applyIfContextIsInProfile(
        method,
        additionsBuilder -> {
          additionsBuilder
              .addRule(companionMethod)
              .addRule(companionMethod.getHolder())
              .removeMovedMethodRule(method, companionMethod);
          companionMethod.getHolder().acceptProgramClassInitializer(additionsBuilder::addRule);
        });
    parent.acceptPrivateAsCompanionMethod(method, companionMethod);
  }

  @Override
  public void acceptRecordClass(DexProgramClass recordClass) {
    parent.acceptRecordClass(recordClass);
  }

  @Override
  public void acceptRecordClassContext(DexProgramClass recordTagClass, ProgramMethod context) {
    parent.acceptRecordClassContext(recordTagClass, context);
  }

  @Override
  public void acceptRecordEqualsHelperMethod(ProgramMethod method, ProgramMethod context) {
    additionsCollection.addMethodIfContextIsInProfile(method, context);
    parent.acceptRecordEqualsHelperMethod(method, context);
  }

  @Override
  public void acceptRecordGetFieldsAsObjectsHelperMethod(
      ProgramMethod method, ProgramMethod context) {
    additionsCollection.addMethodIfContextIsInProfile(method, context);
    parent.acceptRecordGetFieldsAsObjectsHelperMethod(method, context);
  }

  @Override
  public void acceptRecordHashCodeHelperMethod(ProgramMethod method, ProgramMethod context) {
    additionsCollection.addMethodAndHolderIfContextIsInProfile(method, context);
    parent.acceptRecordHashCodeHelperMethod(method, context);
  }

  @Override
  public void acceptRecordToStringHelperMethod(ProgramMethod method, ProgramMethod context) {
    additionsCollection.addMethodAndHolderIfContextIsInProfile(method, context);
    parent.acceptRecordToStringHelperMethod(method, context);
  }

  @Override
  public void acceptStaticAsCompanionMethod(ProgramMethod method, ProgramMethod companionMethod) {
    additionsCollection.applyIfContextIsInProfile(
        method,
        additionsBuilder -> {
          additionsBuilder
              .addRule(companionMethod)
              .addRule(companionMethod.getHolder())
              .removeMovedMethodRule(method, companionMethod);
          companionMethod.getHolder().acceptProgramClassInitializer(additionsBuilder::addRule);
        });
    parent.acceptStaticAsCompanionMethod(method, companionMethod);
  }

  @Override
  public void acceptTwrCloseResourceMethod(ProgramMethod closeMethod, ProgramMethod context) {
    additionsCollection.addMethodAndHolderIfContextIsInProfile(closeMethod, context);
    parent.acceptTwrCloseResourceMethod(closeMethod, context);
  }

  @Override
  @SuppressWarnings("ArgumentSelectionDefectChecker")
  public void acceptUtilityToStringIfNotNullMethod(ProgramMethod method, ProgramMethod context) {
    additionsCollection.addMethodAndHolderIfContextIsInProfile(context, method);
    parent.acceptUtilityToStringIfNotNullMethod(method, context);
  }

  @Override
  public void acceptUtilityThrowClassCastExceptionIfNotNullMethod(
      ProgramMethod method, ProgramMethod context) {
    additionsCollection.addMethodAndHolderIfContextIsInProfile(method, context);
    parent.acceptUtilityThrowClassCastExceptionIfNotNullMethod(method, context);
  }

  @Override
  public void acceptUtilityThrowIllegalAccessErrorMethod(
      ProgramMethod method, ProgramMethod context) {
    additionsCollection.addMethodAndHolderIfContextIsInProfile(method, context);
    parent.acceptUtilityThrowIllegalAccessErrorMethod(method, context);
  }

  @Override
  public void acceptUtilityThrowIncompatibleClassChangeErrorMethod(
      ProgramMethod method, ProgramMethod context) {
    additionsCollection.addMethodAndHolderIfContextIsInProfile(method, context);
    parent.acceptUtilityThrowIncompatibleClassChangeErrorMethod(method, context);
  }

  @Override
  public void acceptUtilityThrowNoSuchMethodErrorMethod(
      ProgramMethod method, ProgramMethod context) {
    additionsCollection.addMethodAndHolderIfContextIsInProfile(method, context);
    parent.acceptUtilityThrowNoSuchMethodErrorMethod(method, context);
  }

  @Override
  public void acceptUtilityThrowRuntimeExceptionWithMessageMethod(
      ProgramMethod method, ProgramMethod context) {
    additionsCollection.addMethodAndHolderIfContextIsInProfile(method, context);
    parent.acceptUtilityThrowRuntimeExceptionWithMessageMethod(method, context);
  }

  @Override
  public void acceptVarHandleDesugaringClass(DexProgramClass clazz) {
    parent.acceptVarHandleDesugaringClass(clazz);
  }

  @Override
  public void acceptVarHandleDesugaringClassContext(
      DexProgramClass clazz, ProgramDefinition context) {
    handleVarHandleDesugaringClassContext(
        clazz, context, additionsCollection, appView.options().getArtProfileOptions());
    parent.acceptVarHandleDesugaringClassContext(clazz, context);
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

  @Override
  public void acceptDesugaredLibraryBridge(ProgramMethod method, ProgramMethod context) {
    additionsCollection.addMethodAndHolderIfContextIsInProfile(method, context);
    parent.acceptDesugaredLibraryBridge(method, context);
  }
}
