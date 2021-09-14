// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.ClassConverterResult;
import com.android.tools.r8.ir.conversion.D8MethodProcessor;
import com.android.tools.r8.ir.desugar.backports.BackportedMethodDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.constantdynamic.ConstantDynamicClass;
import com.android.tools.r8.ir.desugar.constantdynamic.ConstantDynamicDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryRetargeterSynthesizerEventConsumer.DesugaredLibraryRetargeterInstructionEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryWrapperSynthesizerEventConsumer.DesugaredLibraryAPIConverterEventConsumer;
import com.android.tools.r8.ir.desugar.invokespecial.InvokeSpecialBridgeInfo;
import com.android.tools.r8.ir.desugar.invokespecial.InvokeSpecialToSelfDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.itf.EmulatedInterfaceSynthesizerEventConsumer.ClasspathEmulatedInterfaceSynthesizerEventConsumer;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.lambda.LambdaDeserializationMethodRemover;
import com.android.tools.r8.ir.desugar.lambda.LambdaDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.nest.NestBasedAccessDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.records.RecordDesugaringEventConsumer.RecordInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.twr.TwrCloseResourceDesugaringEventConsumer;
import com.android.tools.r8.shaking.Enqueuer.SyntheticAdditions;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        ClasspathEmulatedInterfaceSynthesizerEventConsumer {

  public static D8CfInstructionDesugaringEventConsumer createForD8(
      D8MethodProcessor methodProcessor) {
    return new D8CfInstructionDesugaringEventConsumer(methodProcessor);
  }

  public static R8CfInstructionDesugaringEventConsumer createForR8(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      BiConsumer<LambdaClass, ProgramMethod> lambdaClassConsumer,
      BiConsumer<ConstantDynamicClass, ProgramMethod> constantDynamicClassConsumer,
      BiConsumer<ProgramMethod, ProgramMethod> twrCloseResourceMethodConsumer,
      SyntheticAdditions additions,
      BiConsumer<ProgramMethod, ProgramMethod> companionMethodConsumer) {
    return new R8CfInstructionDesugaringEventConsumer(
        appView,
        lambdaClassConsumer,
        constantDynamicClassConsumer,
        twrCloseResourceMethodConsumer,
        additions,
        companionMethodConsumer);
  }

  // TODO(b/183998768): Remove this event consumer. It should be unneeded for R8 and for D8 the
  //  desugaring of interface methods should be able to happen up front too avoiding the companion
  //  callback on nest accessors.
  public static CfInstructionDesugaringEventConsumer createForDesugaredCode() {
    return new CfInstructionDesugaringEventConsumer() {

      @Override
      public void acceptCompanionMethod(ProgramMethod method, ProgramMethod companionMethod) {
        // A synthesized nest based accessor may itself be defined on an interface, in which case
        // desugaring the accessor will result in a rewrite to the companion method.
      }

      @Override
      public void acceptClasspathEmulatedInterface(DexClasspathClass clazz) {
        assert false;
      }

      @Override
      public void acceptWrapperClasspathClass(DexClasspathClass clazz) {
        assert false;
      }

      @Override
      public void acceptAPIConversion(ProgramMethod method) {
        assert false;
      }

      @Override
      public void acceptDesugaredLibraryRetargeterDispatchClasspathClass(DexClasspathClass clazz) {
        assert false;
      }

      @Override
      public void acceptThrowMethod(ProgramMethod method, ProgramMethod context) {
        assert false;
      }

      @Override
      public void acceptInvokeStaticInterfaceOutliningMethod(
          ProgramMethod method, ProgramMethod context) {
        assert false;
      }

      @Override
      public void acceptRecordClass(DexProgramClass recordClass) {
        assert false;
      }

      @Override
      public void acceptRecordMethod(ProgramMethod method) {
        assert false;
      }

      @Override
      public void acceptBackportedMethod(ProgramMethod backportedMethod, ProgramMethod context) {
        assert false;
      }

      @Override
      public void acceptInvokeSpecialBridgeInfo(InvokeSpecialBridgeInfo info) {
        assert false;
      }

      @Override
      public void acceptLambdaClass(LambdaClass lambdaClass, ProgramMethod context) {
        assert false;
      }

      @Override
      public void acceptConstantDynamicClass(
          ConstantDynamicClass constantDynamicClass, ProgramMethod context) {
        assert false;
      }

      @Override
      public void acceptNestFieldGetBridge(ProgramField target, ProgramMethod bridge) {
        assert false;
      }

      @Override
      public void acceptNestFieldPutBridge(ProgramField target, ProgramMethod bridge) {
        assert false;
      }

      @Override
      public void acceptNestMethodBridge(ProgramMethod target, ProgramMethod bridge) {
        assert false;
      }

      @Override
      public void acceptTwrCloseResourceMethod(ProgramMethod closeMethod, ProgramMethod context) {
        assert false;
      }

      @Override
      public void acceptCompanionClassClinit(ProgramMethod method) {
        assert false;
      }
    };
  }

  public static class D8CfInstructionDesugaringEventConsumer
      extends CfInstructionDesugaringEventConsumer {

    private final D8MethodProcessor methodProcessor;

    private final Map<DexReference, InvokeSpecialBridgeInfo> pendingInvokeSpecialBridges =
        new LinkedHashMap<>();
    private final List<LambdaClass> synthesizedLambdaClasses = new ArrayList<>();
    private final List<ConstantDynamicClass> synthesizedConstantDynamicClasses = new ArrayList<>();

    private D8CfInstructionDesugaringEventConsumer(D8MethodProcessor methodProcessor) {
      this.methodProcessor = methodProcessor;
    }

    @Override
    public void acceptCompanionMethod(ProgramMethod method, ProgramMethod companionMethod) {
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
    public void acceptBackportedMethod(ProgramMethod backportedMethod, ProgramMethod context) {
      methodProcessor.scheduleMethodForProcessing(backportedMethod, this);
    }

    @Override
    public void acceptRecordMethod(ProgramMethod method) {
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
    public void acceptNestFieldGetBridge(ProgramField target, ProgramMethod bridge) {
      assert false;
    }

    @Override
    public void acceptNestFieldPutBridge(ProgramField target, ProgramMethod bridge) {
      assert false;
    }

    @Override
    public void acceptNestMethodBridge(ProgramMethod target, ProgramMethod bridge) {
      assert false;
    }

    @Override
    public void acceptTwrCloseResourceMethod(ProgramMethod closeMethod, ProgramMethod context) {
      methodProcessor.scheduleDesugaredMethodForProcessing(closeMethod);
    }

    @Override
    public void acceptThrowMethod(ProgramMethod method, ProgramMethod context) {
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
    public void acceptAPIConversion(ProgramMethod method) {
      methodProcessor.scheduleDesugaredMethodForProcessing(method);
    }

    @Override
    public void acceptCompanionClassClinit(ProgramMethod method) {
      methodProcessor.scheduleDesugaredMethodForProcessing(method);
    }

    public List<ProgramMethod> finalizeDesugaring(
        AppView<?> appView, ClassConverterResult.Builder classConverterResultBuilder) {
      List<ProgramMethod> needsProcessing = new ArrayList<>();
      finalizeInvokeSpecialDesugaring(appView, needsProcessing::add);
      finalizeLambdaDesugaring(classConverterResultBuilder, needsProcessing::add);
      finalizeConstantDynamicDesugaring(needsProcessing::add);
      return needsProcessing;
    }

    private void finalizeInvokeSpecialDesugaring(
        AppView<?> appView, Consumer<ProgramMethod> needsProcessing) {
      // Fixup the code of the new private methods have that been synthesized.
      pendingInvokeSpecialBridges
          .values()
          .forEach(
              info -> {
                ProgramMethod newDirectMethod = info.getNewDirectMethod();
                newDirectMethod
                    .getDefinition()
                    .setCode(info.getVirtualMethod().getDefinition().getCode(), appView);
              });

      // Reprocess the methods that were subject to invoke-special desugaring (because their body
      // has been moved to a private method).
      pendingInvokeSpecialBridges
          .values()
          .forEach(
              info -> {
                info.getVirtualMethod()
                    .getDefinition()
                    .setCode(info.getVirtualMethodCode(), appView);
                needsProcessing.accept(info.getVirtualMethod());
              });

      pendingInvokeSpecialBridges.clear();
    }

    private void finalizeLambdaDesugaring(
        ClassConverterResult.Builder classConverterResultBuilder,
        Consumer<ProgramMethod> needsProcessing) {
      for (LambdaClass lambdaClass : synthesizedLambdaClasses) {
        lambdaClass.target.ensureAccessibilityIfNeeded(
            classConverterResultBuilder, needsProcessing);
        lambdaClass.getLambdaProgramClass().forEachProgramMethod(needsProcessing);
      }
      synthesizedLambdaClasses.clear();
    }

    private void finalizeConstantDynamicDesugaring(Consumer<ProgramMethod> needsProcessing) {
      for (ConstantDynamicClass constantDynamicClass : synthesizedConstantDynamicClasses) {
        constantDynamicClass.getConstantDynamicProgramClass().forEachProgramMethod(needsProcessing);
      }
      synthesizedConstantDynamicClasses.clear();
    }

    public boolean verifyNothingToFinalize() {
      assert pendingInvokeSpecialBridges.isEmpty();
      assert synthesizedLambdaClasses.isEmpty();
      assert synthesizedConstantDynamicClasses.isEmpty();
      return true;
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
    public void acceptCompanionMethod(ProgramMethod method, ProgramMethod companionMethod) {
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
    public void acceptCompanionClassClinit(ProgramMethod method) {
      // Intentionally empty. The method will be hit by tracing if required.
    }

    @Override
    public void acceptRecordClass(DexProgramClass recordClass) {
      // Intentionally empty. The class will be hit by tracing if required.
    }

    @Override
    public void acceptRecordMethod(ProgramMethod method) {
      // Intentionally empty. The method will be hit by tracing if required.
    }

    @Override
    public void acceptThrowMethod(ProgramMethod method, ProgramMethod context) {
      // Intentionally empty. The method will be hit by tracing if required.
    }

    @Override
    public void acceptInvokeStaticInterfaceOutliningMethod(
        ProgramMethod method, ProgramMethod context) {
      // Intentionally empty. The method will be hit by tracing if required.
      additions.addNeverInlineMethod(method);
    }

    @Override
    public void acceptWrapperClasspathClass(DexClasspathClass clazz) {
      additions.addLiveClasspathClass(clazz);
    }

    @Override
    public void acceptAPIConversion(ProgramMethod method) {
      // Intentionally empty. The method will be hit by tracing if required.
    }

    @Override
    public void acceptBackportedMethod(ProgramMethod backportedMethod, ProgramMethod context) {
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
    public void acceptNestFieldGetBridge(ProgramField target, ProgramMethod bridge) {
      assert false;
    }

    @Override
    public void acceptNestFieldPutBridge(ProgramField target, ProgramMethod bridge) {
      assert false;
    }

    @Override
    public void acceptNestMethodBridge(ProgramMethod target, ProgramMethod bridge) {
      assert false;
    }

    @Override
    public void acceptTwrCloseResourceMethod(ProgramMethod closeMethod, ProgramMethod context) {
      // TODO(b/180091213): Remove the recording of the synthesizing context when this is accessible
      //  from synthetic items.
      twrCloseResourceMethodConsumer.accept(closeMethod, context);
    }

    public void finalizeDesugaring() {
      finalizeInvokeSpecialDesugaring();
      finalizeLambdaDesugaring();
    }

    private void finalizeInvokeSpecialDesugaring() {
      Collections.sort(pendingInvokeSpecialBridges);
      pendingInvokeSpecialBridges.forEach(
          info ->
              info.getVirtualMethod()
                  .getDefinition()
                  .setCode(info.getVirtualMethodCode(), appView));
    }

    private void finalizeLambdaDesugaring() {
      Set<DexProgramClass> classesWithSerializableLambdas = Sets.newIdentityHashSet();
      synthesizedLambdaClasses.forEach(
          (lambdaClass, context) -> {
            lambdaClass.target.ensureAccessibilityIfNeeded();

            // Populate set of types with serialized lambda method for removal.
            if (lambdaClass.descriptor.interfaces.contains(
                appView.dexItemFactory().serializableType)) {
              classesWithSerializableLambdas.add(context.getHolder());
            }
          });

      // Remove all '$deserializeLambda$' methods which are not supported by desugaring.
      LambdaDeserializationMethodRemover.run(appView, classesWithSerializableLambdas);
    }
  }
}
