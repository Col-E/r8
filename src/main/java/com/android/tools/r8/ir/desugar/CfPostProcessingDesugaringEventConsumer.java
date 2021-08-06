// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.D8MethodProcessor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryAPIConverter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryRetargeterInstructionEventConsumer.DesugaredLibraryRetargeterPostProcessingEventConsumer;
import com.android.tools.r8.ir.desugar.itf.InterfaceProcessingDesugaringEventConsumer;
import com.android.tools.r8.shaking.Enqueuer.SyntheticAdditions;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.concurrent.ExecutionException;

/**
 * Specialized Event consumer for desugaring finalization. During finalization, it is not possible
 * to run any more instruction desugaring. If there are dependencies in between various desugaring,
 * explicit calls must be done here.
 */
public abstract class CfPostProcessingDesugaringEventConsumer
    implements DesugaredLibraryRetargeterPostProcessingEventConsumer,
        InterfaceProcessingDesugaringEventConsumer {
  protected DesugaredLibraryAPIConverter desugaredLibraryAPIConverter;

  protected CfPostProcessingDesugaringEventConsumer(AppView<?> appView) {
    this.desugaredLibraryAPIConverter = new DesugaredLibraryAPIConverter(appView, null);
  }

  public static D8CfPostProcessingDesugaringEventConsumer createForD8(
      D8MethodProcessor methodProcessor, AppView<?> appView) {
    return new D8CfPostProcessingDesugaringEventConsumer(methodProcessor, appView);
  }

  public static R8PostProcessingDesugaringEventConsumer createForR8(
      AppView<?> appView, SyntheticAdditions additions) {
    return new R8PostProcessingDesugaringEventConsumer(appView, additions);
  }

  public abstract void finalizeDesugaring() throws ExecutionException;

  public static class D8CfPostProcessingDesugaringEventConsumer
      extends CfPostProcessingDesugaringEventConsumer {
    private final D8MethodProcessor methodProcessor;
    // Methods cannot be processed directly because we cannot add method to classes while
    // concurrently processing other methods.
    private final ProgramMethodSet methodsToReprocess = ProgramMethodSet.createConcurrent();

    private D8CfPostProcessingDesugaringEventConsumer(
        D8MethodProcessor methodProcessor, AppView<?> appView) {
      super(appView);
      this.methodProcessor = methodProcessor;
    }

    @Override
    public void acceptDesugaredLibraryRetargeterDispatchProgramClass(DexProgramClass clazz) {
      methodsToReprocess.addAll(clazz.programMethods());
    }

    @Override
    public void acceptDesugaredLibraryRetargeterDispatchClasspathClass(DexClasspathClass clazz) {
      // Intentionally empty.
    }

    @Override
    public void acceptInterfaceInjection(DexProgramClass clazz, DexClass newInterface) {
      // Intentionally empty.
    }

    @Override
    public void acceptForwardingMethod(ProgramMethod method) {
      methodsToReprocess.add(method);
    }

    @Override
    public void acceptCompanionClassClinit(ProgramMethod method) {
      methodsToReprocess.add(method);
    }

    @Override
    public void acceptEmulatedInterfaceMethod(ProgramMethod method) {
      methodsToReprocess.add(method);
    }

    @Override
    public void finalizeDesugaring() throws ExecutionException {
      assert methodProcessor.verifyNoPendingMethodProcessing();
      methodProcessor.newWave();
      methodProcessor.scheduleDesugaredMethodsForProcessing(methodsToReprocess);
      methodProcessor.awaitMethodProcessing();
    }
  }

  public static class R8PostProcessingDesugaringEventConsumer
      extends CfPostProcessingDesugaringEventConsumer {
    private final SyntheticAdditions additions;

    protected R8PostProcessingDesugaringEventConsumer(
        AppView<?> appView, SyntheticAdditions additions) {
      super(appView);
      this.additions = additions;
    }

    @Override
    public void finalizeDesugaring() throws ExecutionException {
      desugaredLibraryAPIConverter.generateTrackingWarnings();
    }

    @Override
    public void acceptDesugaredLibraryRetargeterDispatchProgramClass(DexProgramClass clazz) {
      additions.addLiveMethods(clazz.programMethods());
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
    public void acceptForwardingMethod(ProgramMethod method) {
      additions.addLiveMethod(method);
      ProgramMethod callback = desugaredLibraryAPIConverter.generateCallbackIfRequired(method);
      if (callback != null) {
        additions.addLiveMethod(callback);
      }
    }

    @Override
    public void acceptCompanionClassClinit(ProgramMethod method) {
      assert false : "TODO(b/183998768): Support Interface processing in R8";
    }

    @Override
    public void acceptEmulatedInterfaceMethod(ProgramMethod method) {
      assert false : "TODO(b/183998768): Support Interface processing in R8";
    }
  }
}
