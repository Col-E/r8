// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.D8MethodProcessor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryWrapperSynthesizerEventConsumer.DesugaredLibraryAPICallbackSynthesizorEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.DesugaredLibraryRetargeterSynthesizerEventConsumer.DesugaredLibraryRetargeterPostProcessingEventConsumer;
import com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper;
import com.android.tools.r8.ir.desugar.itf.InterfaceProcessingDesugaringEventConsumer;
import com.android.tools.r8.shaking.Enqueuer.SyntheticAdditions;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

/**
 * Specialized Event consumer for desugaring finalization. During finalization, it is not possible
 * to run any more instruction desugaring. If there are dependencies in between various desugaring,
 * explicit calls must be done here.
 */
public abstract class CfPostProcessingDesugaringEventConsumer
    implements DesugaredLibraryRetargeterPostProcessingEventConsumer,
        InterfaceProcessingDesugaringEventConsumer,
        DesugaredLibraryAPICallbackSynthesizorEventConsumer {

  public static D8CfPostProcessingDesugaringEventConsumer createForD8(
      D8MethodProcessor methodProcessor, CfInstructionDesugaringCollection instructionDesugaring) {
    return new D8CfPostProcessingDesugaringEventConsumer(methodProcessor, instructionDesugaring);
  }

  public static R8PostProcessingDesugaringEventConsumer createForR8(
      SyntheticAdditions additions,
      CfInstructionDesugaringCollection desugaring,
      BiConsumer<DexProgramClass, DexType> missingClassConsumer) {
    return new R8PostProcessingDesugaringEventConsumer(additions, desugaring, missingClassConsumer);
  }

  public abstract void finalizeDesugaring() throws ExecutionException;

  public static class D8CfPostProcessingDesugaringEventConsumer
      extends CfPostProcessingDesugaringEventConsumer {

    private final D8MethodProcessor methodProcessor;
    // Methods cannot be processed directly because we cannot add method to classes while
    // concurrently processing other methods.
    private final ProgramMethodSet methodsToReprocess = ProgramMethodSet.createConcurrent();
    private final CfInstructionDesugaringCollection instructionDesugaring;

    private D8CfPostProcessingDesugaringEventConsumer(
        D8MethodProcessor methodProcessor,
        CfInstructionDesugaringCollection instructionDesugaring) {
      this.methodProcessor = methodProcessor;
      this.instructionDesugaring = instructionDesugaring;
    }

    private void addMethodToReprocess(ProgramMethod method) {
      assert !instructionDesugaring.needsDesugaring(method);
      assert method.getDefinition().getCode().isCfCode();
      methodsToReprocess.add(method);
    }

    @Override
    public void warnMissingInterface(
        DexProgramClass context, DexType missing, InterfaceDesugaringSyntheticHelper helper) {
      helper.warnMissingInterface(context, context, missing);
    }

    @Override
    public void acceptDesugaredLibraryRetargeterDispatchClasspathClass(DexClasspathClass clazz) {
      // Intentionally empty.
    }

    @Override
    public void acceptCovariantRetargetMethod(ProgramMethod method) {
      addMethodToReprocess(method);
    }

    @Override
    public void acceptInterfaceInjection(DexProgramClass clazz, DexClass newInterface) {
      // Intentionally empty.
    }

    @Override
    public void acceptEmulatedInterfaceMarkerInterface(
        DexProgramClass clazz, DexClasspathClass newInterface) {
      // Intentionally empty.
    }

    @Override
    public void acceptForwardingMethod(ProgramMethod method) {
      addMethodToReprocess(method);
    }

    @Override
    public void acceptCollectionConversion(ProgramMethod method) {
      addMethodToReprocess(method);
    }

    @Override
    public void finalizeDesugaring() throws ExecutionException {
      assert methodProcessor.verifyNoPendingMethodProcessing();
      methodProcessor.newWave();
      methodProcessor.scheduleDesugaredMethodsForProcessing(methodsToReprocess);
      methodProcessor.awaitMethodProcessing();
    }

    @Override
    public void acceptAPIConversionCallback(ProgramMethod method) {
      addMethodToReprocess(method);
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
  }

  public static class R8PostProcessingDesugaringEventConsumer
      extends CfPostProcessingDesugaringEventConsumer {

    private final SyntheticAdditions additions;
    private final CfInstructionDesugaringCollection desugaring;
    private final BiConsumer<DexProgramClass, DexType> missingClassConsumer;

    R8PostProcessingDesugaringEventConsumer(
        SyntheticAdditions additions,
        CfInstructionDesugaringCollection desugaring,
        BiConsumer<DexProgramClass, DexType> missingClassConsumer) {
      this.additions = additions;
      this.desugaring = desugaring;
      this.missingClassConsumer = missingClassConsumer;
    }

    @Override
    public void warnMissingInterface(
        DexProgramClass context, DexType missing, InterfaceDesugaringSyntheticHelper helper) {
      missingClassConsumer.accept(context, missing);
    }

    @Override
    public void finalizeDesugaring() {
      // Intentionally empty.
    }

    @Override
    public void acceptEmulatedInterfaceMarkerInterface(
        DexProgramClass clazz, DexClasspathClass newInterface) {
      additions.injectInterface(clazz, newInterface);
      additions.addLiveClasspathClass(newInterface);
    }

    @Override
    public void acceptInterfaceInjection(DexProgramClass clazz, DexClass newInterface) {
      additions.injectInterface(clazz, newInterface);
    }

    @Override
    public void acceptDesugaredLibraryRetargeterDispatchClasspathClass(DexClasspathClass clazz) {
      additions.addLiveClasspathClass(clazz);
    }

    @Override
    public void acceptCovariantRetargetMethod(ProgramMethod method) {
      additions.addLiveMethod(method);
    }

    @Override
    public void acceptForwardingMethod(ProgramMethod method) {
      additions.addLiveMethod(method);
    }

    @Override
    public void acceptCollectionConversion(ProgramMethod method) {
      additions.addLiveMethod(method);
    }

    @Override
    public void acceptAPIConversionCallback(ProgramMethod method) {
      assert !desugaring.needsDesugaring(method);
      additions.addLiveMethod(method);
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
  }
}
