// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.ClassConverterResult;
import com.android.tools.r8.ir.conversion.D8MethodProcessor;
import com.android.tools.r8.ir.desugar.apimodel.ApiInvokeOutlinerDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.backports.BackportedMethodDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.constantdynamic.ConstantDynamicClass;
import com.android.tools.r8.ir.desugar.constantdynamic.ConstantDynamicDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryWrapperSynthesizerEventConsumer.DesugaredLibraryAPIConverterEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.DesugaredLibRewriterEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.DesugaredLibraryRetargeterSynthesizerEventConsumer.DesugaredLibraryRetargeterInstructionEventConsumer;
import com.android.tools.r8.ir.desugar.invokespecial.InvokeSpecialBridgeInfo;
import com.android.tools.r8.ir.desugar.invokespecial.InvokeSpecialToSelfDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.itf.EmulatedInterfaceSynthesizerEventConsumer.ClasspathEmulatedInterfaceSynthesizerEventConsumer;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.lambda.LambdaDeserializationMethodRemover;
import com.android.tools.r8.ir.desugar.lambda.LambdaDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.nest.NestBasedAccessDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.records.RecordDesugaringEventConsumer.RecordInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.twr.TwrCloseResourceDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.varhandle.VarHandleDesugaringEventConsumer;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.profile.rewriting.ProfileRewritingCfInstructionDesugaringEventConsumer;
import com.android.tools.r8.shaking.Enqueuer.SyntheticAdditions;
import com.android.tools.r8.shaking.KeepMethodInfo.Joiner;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Class that gets notified for structural changes made as a result of desugaring (e.g., the
 * inserting of a new method).
 */
public abstract class CfInstructionDesugaringEventConsumer
    implements BackportedMethodDesugaringEventConsumer,
        InvokeSpecialToSelfDesugaringEventConsumer,
        LambdaDesugaringEventConsumer,
        ConstantDynamicDesugaringEventConsumer,
        NestBasedAccessDesugaringEventConsumer,
        RecordInstructionDesugaringEventConsumer,
        TwrCloseResourceDesugaringEventConsumer,
        InterfaceMethodDesugaringEventConsumer,
        DesugaredLibraryRetargeterInstructionEventConsumer,
        DesugaredLibraryAPIConverterEventConsumer,
        ClasspathEmulatedInterfaceSynthesizerEventConsumer,
        ApiInvokeOutlinerDesugaringEventConsumer,
        VarHandleDesugaringEventConsumer,
        DesugaredLibRewriterEventConsumer {

  public static CfInstructionDesugaringEventConsumer createForD8(
      AppView<?> appView,
      ProfileCollectionAdditions profileCollectionAdditions,
      ClassConverterResult.Builder classConverterResultBuilder,
      D8MethodProcessor methodProcessor) {
    D8CfInstructionDesugaringEventConsumer eventConsumer =
        new D8CfInstructionDesugaringEventConsumer(
            appView, classConverterResultBuilder, methodProcessor);
    CfInstructionDesugaringEventConsumer outermostEventConsumer =
        ProfileRewritingCfInstructionDesugaringEventConsumer.attach(
            appView, profileCollectionAdditions, eventConsumer);
    eventConsumer.setOutermostEventConsumer(outermostEventConsumer);
    return outermostEventConsumer;
  }

  public static CfInstructionDesugaringEventConsumer createForR8(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ProfileCollectionAdditions profileCollectionAdditions,
      BiConsumer<LambdaClass, ProgramMethod> lambdaClassConsumer,
      BiConsumer<ConstantDynamicClass, ProgramMethod> constantDynamicClassConsumer,
      BiConsumer<ProgramMethod, ProgramMethod> twrCloseResourceMethodConsumer,
      SyntheticAdditions additions,
      BiConsumer<ProgramMethod, ProgramMethod> companionMethodConsumer) {
    CfInstructionDesugaringEventConsumer eventConsumer =
        new R8CfInstructionDesugaringEventConsumer(
            appView,
            lambdaClassConsumer,
            constantDynamicClassConsumer,
            twrCloseResourceMethodConsumer,
            additions,
            companionMethodConsumer);
    return ProfileRewritingCfInstructionDesugaringEventConsumer.attach(
        appView, profileCollectionAdditions, eventConsumer);
  }

  public abstract List<ProgramMethod> finalizeDesugaring();

  public abstract boolean verifyNothingToFinalize();

  public static class D8CfInstructionDesugaringEventConsumer
      extends CfInstructionDesugaringEventConsumer {

    private final AppView<?> appView;
    private final ClassConverterResult.Builder classConverterResultBuilder;
    private final D8MethodProcessor methodProcessor;

    private final Map<DexReference, InvokeSpecialBridgeInfo> pendingInvokeSpecialBridges =
        new LinkedHashMap<>();
    private final List<LambdaClass> synthesizedLambdaClasses = new ArrayList<>();
    private final List<ConstantDynamicClass> synthesizedConstantDynamicClasses = new ArrayList<>();

    private CfInstructionDesugaringEventConsumer outermostEventConsumer = this;

    private D8CfInstructionDesugaringEventConsumer(
        AppView<?> appView,
        ClassConverterResult.Builder classConverterResultBuilder,
        D8MethodProcessor methodProcessor) {
      this.appView = appView;
      this.classConverterResultBuilder = classConverterResultBuilder;
      this.methodProcessor = methodProcessor;
    }

    public void setOutermostEventConsumer(
        CfInstructionDesugaringEventConsumer outermostEventConsumer) {
      this.outermostEventConsumer = outermostEventConsumer;
    }

    @Override
    public void acceptDefaultAsCompanionMethod(
        ProgramMethod method, ProgramMethod companionMethod) {
      // Intentionally empty. Methods are moved when processing the interface definition.
    }

    @Override
    public void acceptPrivateAsCompanionMethod(
        ProgramMethod method, ProgramMethod companionMethod) {
      // Intentionally empty. Methods are moved when processing the interface definition.
    }

    @Override
    public void acceptStaticAsCompanionMethod(ProgramMethod method, ProgramMethod companionMethod) {
      // Intentionally empty. Methods are moved when processing the interface definition.
    }

    @Override
    public void acceptDesugaredLibraryRetargeterDispatchClasspathClass(DexClasspathClass clazz) {
      // Intentionally empty.
    }

    @Override
    public void acceptClasspathEmulatedInterface(DexClasspathClass clazz) {
      // Intentionally empty.
    }

    @Override
    public void acceptCollectionConversion(ProgramMethod arrayConversion, ProgramMethod context) {
      methodProcessor.scheduleMethodForProcessing(arrayConversion, outermostEventConsumer);
    }

    @Override
    public void acceptCovariantRetargetMethod(ProgramMethod method, ProgramMethod context) {
      methodProcessor.scheduleMethodForProcessing(method, outermostEventConsumer);
    }

    @Override
    public void acceptBackportedMethod(ProgramMethod backportedMethod, ProgramMethod context) {
      methodProcessor.scheduleMethodForProcessing(backportedMethod, outermostEventConsumer);
    }

    @Override
    public void acceptBackportedClass(DexProgramClass backportedClass, ProgramMethod context) {
      backportedClass
          .programMethods()
          .forEach(
              method ->
                  methodProcessor.scheduleMethodForProcessing(method, outermostEventConsumer));
    }

    @Override
    public void acceptDesugaredLibraryBridge(ProgramMethod method, ProgramMethod context) {
      methodProcessor.scheduleMethodForProcessing(method, outermostEventConsumer);
    }

    @Override
    public void acceptRecordEqualsHelperMethod(ProgramMethod method, ProgramMethod context) {
      // Intentionally empty. Added to the program using ProgramAdditions.
    }

    @Override
    public void acceptRecordGetFieldsAsObjectsHelperMethod(
        ProgramMethod method, ProgramMethod context) {
      // Intentionally empty. Added to the program using ProgramAdditions.
    }

    @Override
    public void acceptRecordHashCodeHelperMethod(ProgramMethod method, ProgramMethod context) {
      methodProcessor.scheduleDesugaredMethodForProcessing(method);
    }

    @Override
    public void acceptRecordToStringHelperMethod(ProgramMethod method, ProgramMethod context) {
      methodProcessor.scheduleDesugaredMethodForProcessing(method);
    }

    @Override
    public void acceptInvokeSpecialBridgeInfo(InvokeSpecialBridgeInfo info) {
      synchronized (pendingInvokeSpecialBridges) {
        assert !pendingInvokeSpecialBridges.containsKey(info.getNewDirectMethod().getReference());
        pendingInvokeSpecialBridges.put(info.getNewDirectMethod().getReference(), info);
      }
    }

    @Override
    public void acceptRecordClass(DexProgramClass recordClass) {
      methodProcessor.scheduleDesugaredMethodsForProcessing(recordClass.programMethods());
    }

    @Override
    public void acceptRecordClassContext(DexProgramClass recordTagClass, ProgramMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptVarHandleDesugaringClass(DexProgramClass clazz) {
      clazz
          .programMethods()
          .forEach(
              method ->
                  methodProcessor.scheduleMethodForProcessing(method, outermostEventConsumer));
    }

    @Override
    public void acceptVarHandleDesugaringClassContext(
        DexProgramClass clazz, ProgramDefinition context) {
      // Intentionally empty.
    }

    @Override
    public void acceptLambdaClass(LambdaClass lambdaClass, ProgramMethod context) {
      synchronized (synthesizedLambdaClasses) {
        synthesizedLambdaClasses.add(lambdaClass);
      }
    }

    @Override
    public void acceptConstantDynamicClass(
        ConstantDynamicClass constantDynamicClass, ProgramMethod context) {
      synchronized (synthesizedConstantDynamicClasses) {
        synthesizedConstantDynamicClasses.add(constantDynamicClass);
      }
    }

    @Override
    public void acceptConstantDynamicRewrittenBootstrapMethod(
        ProgramMethod bootstrapMethod, DexMethod oldSignature) {
      // Intentionally empty.
    }

    @Override
    public void acceptNestConstructorBridge(
        ProgramMethod target,
        ProgramMethod bridge,
        DexProgramClass argumentClass,
        DexClassAndMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptNestFieldGetBridge(
        ProgramField target, ProgramMethod bridge, DexClassAndMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptNestFieldPutBridge(
        ProgramField target, ProgramMethod bridge, DexClassAndMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptNestMethodBridge(
        ProgramMethod target, ProgramMethod bridge, DexClassAndMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptTwrCloseResourceMethod(ProgramMethod closeMethod, ProgramMethod context) {
      methodProcessor.scheduleMethodForProcessing(closeMethod, outermostEventConsumer);
    }

    @Override
    public void acceptUtilityToStringIfNotNullMethod(ProgramMethod method, ProgramMethod context) {
      acceptUtilityMethod(method, context);
    }

    @Override
    public void acceptUtilityThrowClassCastExceptionIfNotNullMethod(
        ProgramMethod method, ProgramMethod context) {
      acceptUtilityMethod(method, context);
    }

    @Override
    public void acceptUtilityThrowIllegalAccessErrorMethod(
        ProgramMethod method, ProgramMethod context) {
      acceptUtilityMethod(method, context);
    }

    @Override
    public void acceptUtilityThrowIncompatibleClassChangeErrorMethod(
        ProgramMethod method, ProgramMethod context) {
      acceptUtilityMethod(method, context);
    }

    @Override
    public void acceptUtilityThrowNoSuchMethodErrorMethod(
        ProgramMethod method, ProgramMethod context) {
      acceptUtilityMethod(method, context);
    }

    @Override
    public void acceptUtilityThrowRuntimeExceptionWithMessageMethod(
        ProgramMethod method, ProgramMethod context) {
      acceptUtilityMethod(method, context);
    }

    private void acceptUtilityMethod(ProgramMethod method, ProgramMethod context) {
      methodProcessor.scheduleDesugaredMethodForProcessing(method);
    }

    @Override
    public void acceptInvokeStaticInterfaceOutliningMethod(
        ProgramMethod method, ProgramMethod context) {
      methodProcessor.scheduleDesugaredMethodForProcessing(method);
    }

    @Override
    public void acceptWrapperClasspathClass(DexClasspathClass clazz) {
      // Intentionally empty.
    }

    @Override
    public void acceptEnumConversionClasspathClass(DexClasspathClass clazz) {
      // Intentionally empty.
    }

    @Override
    public void acceptGenericApiConversionStub(DexClasspathClass dexClasspathClass) {
      // Intentionally empty.
    }

    @Override
    public void acceptAPIConversionOutline(ProgramMethod method, ProgramMethod context) {
      methodProcessor.scheduleDesugaredMethodForProcessing(method);
    }

    @Override
    public void acceptCompanionClassClinit(ProgramMethod method, ProgramMethod companionMethod) {
      methodProcessor.scheduleDesugaredMethodForProcessing(companionMethod);
    }

    @Override
    public List<ProgramMethod> finalizeDesugaring() {
      List<ProgramMethod> needsProcessing = new ArrayList<>();
      finalizeInvokeSpecialDesugaring(needsProcessing::add);
      finalizeLambdaDesugaring(classConverterResultBuilder, needsProcessing::add);
      finalizeConstantDynamicDesugaring(needsProcessing::add);
      return needsProcessing;
    }

    private void finalizeInvokeSpecialDesugaring(Consumer<ProgramMethod> needsProcessing) {
      // Fixup the code of the new private methods have that been synthesized.
      pendingInvokeSpecialBridges
          .values()
          .forEach(
              info -> {
                ProgramMethod newDirectMethod = info.getNewDirectMethod();
                newDirectMethod
                    .setCode(info.getVirtualMethod().getDefinition().getCode(), appView);
              });

      // Reprocess the methods that were subject to invoke-special desugaring (because their body
      // has been moved to a private method).
      pendingInvokeSpecialBridges
          .values()
          .forEach(
              info -> {
                info.getVirtualMethod()
                    .setCode(info.getVirtualMethodCode(), appView);
                needsProcessing.accept(info.getVirtualMethod());
              });

      pendingInvokeSpecialBridges.clear();
    }

    private void finalizeLambdaDesugaring(
        ClassConverterResult.Builder classConverterResultBuilder,
        Consumer<ProgramMethod> needsProcessing) {
      // Sort synthesized lambda classes to ensure deterministic insertion of the synthesized
      // $r8$lambda$ target methods.
      synthesizedLambdaClasses.sort(Comparator.comparing(LambdaClass::getType));
      for (LambdaClass lambdaClass : synthesizedLambdaClasses) {
        lambdaClass.target.ensureAccessibilityIfNeeded(
            classConverterResultBuilder, needsProcessing);
        lambdaClass.getLambdaProgramClass().forEachProgramMethod(needsProcessing);
      }
      synthesizedLambdaClasses.clear();
    }

    private void finalizeConstantDynamicDesugaring(Consumer<ProgramMethod> needsProcessing) {
      for (ConstantDynamicClass constantDynamicClass : synthesizedConstantDynamicClasses) {
        constantDynamicClass.rewriteBootstrapMethodSignatureIfNeeded(outermostEventConsumer);
        constantDynamicClass.getConstantDynamicProgramClass().forEachProgramMethod(needsProcessing);
      }
      synthesizedConstantDynamicClasses.clear();
    }

    @Override
    public boolean verifyNothingToFinalize() {
      assert pendingInvokeSpecialBridges.isEmpty();
      assert synthesizedLambdaClasses.isEmpty();
      assert synthesizedConstantDynamicClasses.isEmpty();
      return true;
    }

    @Override
    public void acceptOutlinedMethod(ProgramMethod outlinedMethod, ProgramMethod context) {
      methodProcessor.scheduleDesugaredMethodForProcessing(outlinedMethod);
    }
  }

  public static class R8CfInstructionDesugaringEventConsumer
      extends CfInstructionDesugaringEventConsumer {

    private final AppView<? extends AppInfoWithClassHierarchy> appView;

    // TODO(b/180091213): Remove these three consumers when synthesizing contexts are accessible
    // from
    //  synthetic items.
    private final BiConsumer<LambdaClass, ProgramMethod> lambdaClassConsumer;
    private final BiConsumer<ConstantDynamicClass, ProgramMethod> constantDynamicClassConsumer;
    private final BiConsumer<ProgramMethod, ProgramMethod> twrCloseResourceMethodConsumer;
    private final SyntheticAdditions additions;

    private final Map<LambdaClass, ProgramMethod> synthesizedLambdaClasses =
        new IdentityHashMap<>();
    private final List<InvokeSpecialBridgeInfo> pendingInvokeSpecialBridges = new ArrayList<>();
    private final List<ConstantDynamicClass> synthesizedConstantDynamicClasses = new ArrayList<>();

    private final BiConsumer<ProgramMethod, ProgramMethod> onCompanionMethodCallback;

    public R8CfInstructionDesugaringEventConsumer(
        AppView<? extends AppInfoWithClassHierarchy> appView,
        BiConsumer<LambdaClass, ProgramMethod> lambdaClassConsumer,
        BiConsumer<ConstantDynamicClass, ProgramMethod> constantDynamicClassConsumer,
        BiConsumer<ProgramMethod, ProgramMethod> twrCloseResourceMethodConsumer,
        SyntheticAdditions additions,
        BiConsumer<ProgramMethod, ProgramMethod> onCompanionMethodCallback) {
      this.appView = appView;
      this.lambdaClassConsumer = lambdaClassConsumer;
      this.constantDynamicClassConsumer = constantDynamicClassConsumer;
      this.twrCloseResourceMethodConsumer = twrCloseResourceMethodConsumer;
      this.additions = additions;
      this.onCompanionMethodCallback = onCompanionMethodCallback;
    }

    @Override
    public void acceptDefaultAsCompanionMethod(
        ProgramMethod method, ProgramMethod companionMethod) {
      onCompanionMethodCallback.accept(method, companionMethod);
    }

    @Override
    public void acceptPrivateAsCompanionMethod(
        ProgramMethod method, ProgramMethod companionMethod) {
      onCompanionMethodCallback.accept(method, companionMethod);
    }

    @Override
    public void acceptStaticAsCompanionMethod(ProgramMethod method, ProgramMethod companionMethod) {
      onCompanionMethodCallback.accept(method, companionMethod);
    }

    @Override
    public void acceptDesugaredLibraryRetargeterDispatchClasspathClass(DexClasspathClass clazz) {
      additions.addLiveClasspathClass(clazz);
    }

    @Override
    public void acceptClasspathEmulatedInterface(DexClasspathClass clazz) {
      additions.addLiveClasspathClass(clazz);
    }

    @Override
    public void acceptCompanionClassClinit(ProgramMethod method, ProgramMethod companionMethod) {
      // Intentionally empty. The method will be hit by tracing if required.
    }

    @Override
    public void acceptRecordClass(DexProgramClass recordClass) {
      // Intentionally empty. The class will be hit by tracing if required.
    }

    @Override
    public void acceptRecordClassContext(DexProgramClass recordTagClass, ProgramMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptVarHandleDesugaringClass(DexProgramClass clazz) {
      // Intentionally empty. The class will be hit by tracing if required.
    }

    @Override
    public void acceptVarHandleDesugaringClassContext(
        DexProgramClass clazz, ProgramDefinition context) {
      // Intentionally empty.
    }

    @Override
    public void acceptCollectionConversion(ProgramMethod arrayConversion, ProgramMethod context) {
      // Intentionally empty. The method will be hit by tracing if required.
    }

    @Override
    public void acceptDesugaredLibraryBridge(ProgramMethod method, ProgramMethod context) {
      // Intentionally empty. The method will be hit by tracing if required.
    }

    @Override
    public void acceptRecordEqualsHelperMethod(ProgramMethod method, ProgramMethod context) {
      // Intentionally empty. The method will be hit by tracing if required.
    }

    @Override
    public void acceptRecordGetFieldsAsObjectsHelperMethod(
        ProgramMethod method, ProgramMethod context) {
      // Intentionally empty. The method will be hit by tracing if required.
    }

    @Override
    public void acceptRecordHashCodeHelperMethod(ProgramMethod method, ProgramMethod context) {
      // Intentionally empty. The method will be hit by tracing if required.
    }

    @Override
    public void acceptRecordToStringHelperMethod(ProgramMethod method, ProgramMethod context) {
      // Intentionally empty. The method will be hit by tracing if required.
    }

    @Override
    public void acceptCovariantRetargetMethod(ProgramMethod method, ProgramMethod context) {
      // Intentionally empty. The method will be hit by tracing if required.
    }

    @Override
    public void acceptUtilityToStringIfNotNullMethod(ProgramMethod method, ProgramMethod context) {
      acceptUtilityMethod(method, context);
    }

    @Override
    public void acceptUtilityThrowClassCastExceptionIfNotNullMethod(
        ProgramMethod method, ProgramMethod context) {
      acceptUtilityMethod(method, context);
    }

    @Override
    public void acceptUtilityThrowIllegalAccessErrorMethod(
        ProgramMethod method, ProgramMethod context) {
      acceptUtilityMethod(method, context);
    }

    @Override
    public void acceptUtilityThrowIncompatibleClassChangeErrorMethod(
        ProgramMethod method, ProgramMethod context) {
      acceptUtilityMethod(method, context);
    }

    @Override
    public void acceptUtilityThrowNoSuchMethodErrorMethod(
        ProgramMethod method, ProgramMethod context) {
      acceptUtilityMethod(method, context);
    }

    @Override
    public void acceptUtilityThrowRuntimeExceptionWithMessageMethod(
        ProgramMethod method, ProgramMethod context) {
      acceptUtilityMethod(method, context);
    }

    private void acceptUtilityMethod(ProgramMethod method, ProgramMethod context) {
      // Intentionally empty. The method will be hit by tracing if required.
    }

    @Override
    public void acceptInvokeStaticInterfaceOutliningMethod(
        ProgramMethod method, ProgramMethod context) {
      // Intentionally empty. The method will be hit by tracing if required.
      additions.addMinimumKeepInfo(method, Joiner::disallowInlining);
    }

    @Override
    public void acceptWrapperClasspathClass(DexClasspathClass clazz) {
      additions.addLiveClasspathClass(clazz);
    }

    @Override
    public void acceptEnumConversionClasspathClass(DexClasspathClass clazz) {
      additions.addLiveClasspathClass(clazz);
    }

    @Override
    public void acceptGenericApiConversionStub(DexClasspathClass clazz) {
      additions.addLiveClasspathClass(clazz);
    }

    @Override
    public void acceptAPIConversionOutline(ProgramMethod method, ProgramMethod context) {
      // Intentionally empty. The method will be hit by tracing if required.
    }

    @Override
    public void acceptBackportedMethod(ProgramMethod backportedMethod, ProgramMethod context) {
      // Intentionally empty. The method will be hit by tracing if required.
    }

    @Override
    public void acceptBackportedClass(DexProgramClass backportedClass, ProgramMethod context) {
      // Intentionally empty. The method will be hit by tracing if required.
    }

    @Override
    public void acceptInvokeSpecialBridgeInfo(InvokeSpecialBridgeInfo info) {
      synchronized (pendingInvokeSpecialBridges) {
        pendingInvokeSpecialBridges.add(info);
      }
    }

    @Override
    public void acceptLambdaClass(LambdaClass lambdaClass, ProgramMethod context) {
      synchronized (synthesizedLambdaClasses) {
        synthesizedLambdaClasses.put(lambdaClass, context);
      }
      // TODO(b/180091213): Remove the recording of the synthesizing context when this is accessible
      //  from synthetic items.
      lambdaClassConsumer.accept(lambdaClass, context);
    }

    @Override
    public void acceptConstantDynamicClass(
        ConstantDynamicClass constantDynamicClass, ProgramMethod context) {
      synchronized (synthesizedConstantDynamicClasses) {
        synthesizedConstantDynamicClasses.add(constantDynamicClass);
      }
      // TODO(b/180091213): Remove the recording of the synthesizing context when this is accessible
      //  from synthetic items.
      constantDynamicClassConsumer.accept(constantDynamicClass, context);
    }

    @Override
    public void acceptConstantDynamicRewrittenBootstrapMethod(
        ProgramMethod bootstrapMethod, DexMethod oldSignature) {
      // Intentionally empty.
    }

    @Override
    public void acceptNestConstructorBridge(
        ProgramMethod target,
        ProgramMethod bridge,
        DexProgramClass argumentClass,
        DexClassAndMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptNestFieldGetBridge(
        ProgramField target, ProgramMethod bridge, DexClassAndMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptNestFieldPutBridge(
        ProgramField target, ProgramMethod bridge, DexClassAndMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptNestMethodBridge(
        ProgramMethod target, ProgramMethod bridge, DexClassAndMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptTwrCloseResourceMethod(ProgramMethod closeMethod, ProgramMethod context) {
      // TODO(b/180091213): Remove the recording of the synthesizing context when this is accessible
      //  from synthetic items.
      twrCloseResourceMethodConsumer.accept(closeMethod, context);
    }

    @Override
    public List<ProgramMethod> finalizeDesugaring() {
      finalizeInvokeSpecialDesugaring();
      finalizeLambdaDesugaring();
      // TODO(b/210485236): Finalize constant dynamic desugaring by rewriting signature if needed.
      return Collections.emptyList();
    }

    @Override
    public boolean verifyNothingToFinalize() {
      // Currently only used in D8.
      assert false;
      return true;
    }

    private void finalizeInvokeSpecialDesugaring() {
      Collections.sort(pendingInvokeSpecialBridges);
      pendingInvokeSpecialBridges.forEach(
          info ->
              info.getVirtualMethod()
                  .setCode(info.getVirtualMethodCode(), appView));
    }

    private void finalizeLambdaDesugaring() {
      // Sort synthesized lambda classes to ensure deterministic insertion of the synthesized
      // $r8$lambda$ target methods.
      List<Entry<LambdaClass, ProgramMethod>> sortedSynthesizedLambdaClasses =
          new ArrayList<>(synthesizedLambdaClasses.entrySet());
      sortedSynthesizedLambdaClasses.sort(Comparator.comparing(entry -> entry.getKey().getType()));

      Set<DexProgramClass> classesWithSerializableLambdas = Sets.newIdentityHashSet();
      sortedSynthesizedLambdaClasses.forEach(
          entry -> {
            LambdaClass lambdaClass = entry.getKey();
            ProgramMethod context = entry.getValue();

            lambdaClass.target.ensureAccessibilityIfNeeded();

            // Populate set of types with serialized lambda method for removal.
            if (lambdaClass.descriptor.interfaces.contains(
                appView.dexItemFactory().serializableType)) {
              classesWithSerializableLambdas.add(context.getHolder());
            }
          });

      // Remove all '$deserializeLambda$' methods which are not supported by desugaring.
      assert appView.options().isGeneratingDex() || classesWithSerializableLambdas.isEmpty();
      LambdaDeserializationMethodRemover.run(appView, classesWithSerializableLambdas);
    }

    @Override
    public void acceptOutlinedMethod(ProgramMethod outlinedMethod, ProgramMethod context) {
      // Intentionally empty. The method will be hit by tracing if required.
    }
  }
}
