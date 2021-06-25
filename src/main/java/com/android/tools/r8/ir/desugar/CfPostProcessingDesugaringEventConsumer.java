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
import com.android.tools.r8.shaking.Enqueuer.SyntheticAdditions;
import java.util.function.Consumer;

/**
 * Specialized Event consumer for desugaring finalization. During finalization, it is not possible
 * to run any more instruction desugaring. If there are dependencies in between various desugaring,
 * explicit calls must be done here.
 */
public abstract class CfPostProcessingDesugaringEventConsumer
    implements DesugaredLibraryRetargeterPostProcessingEventConsumer {
  protected DesugaredLibraryAPIConverter desugaredLibraryAPIConverter;

  protected CfPostProcessingDesugaringEventConsumer(AppView<?> appView) {
    this.desugaredLibraryAPIConverter = new DesugaredLibraryAPIConverter(appView, null);
  }

  public static D8CfPostProcessingDesugaringEventConsumer createForD8(
      D8MethodProcessor methodProcessor, AppView<?> appView) {
    return new D8CfPostProcessingDesugaringEventConsumer(methodProcessor, appView);
  }

  public static R8PostProcessingDesugaringEventConsumer createForR8(
      AppView<?> appView, Consumer<ProgramMethod> methodConsumer, SyntheticAdditions additions) {
    return new R8PostProcessingDesugaringEventConsumer(appView, methodConsumer, additions);
  }

  public void finalizeDesugaring() {
    desugaredLibraryAPIConverter.generateTrackingWarnings();
  }

  public static class D8CfPostProcessingDesugaringEventConsumer
      extends CfPostProcessingDesugaringEventConsumer {
    private final D8MethodProcessor methodProcessor;

    private D8CfPostProcessingDesugaringEventConsumer(
        D8MethodProcessor methodProcessor, AppView<?> appView) {
      super(appView);
      this.methodProcessor = methodProcessor;
    }

    @Override
    public void acceptDesugaredLibraryRetargeterDispatchProgramClass(DexProgramClass clazz) {
      methodProcessor.scheduleDesugaredMethodsForProcessing(clazz.programMethods());
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
      methodProcessor.scheduleDesugaredMethodForProcessing(method);
      // TODO(b/189912077): Uncomment when API conversion is performed cf to cf in D8.
      // desugaredLibraryAPIConverter.generateCallbackIfRequired(method);
    }
  }

  public static class R8PostProcessingDesugaringEventConsumer
      extends CfPostProcessingDesugaringEventConsumer {
    private final Consumer<ProgramMethod> methodConsumer;
    private final SyntheticAdditions additions;

    protected R8PostProcessingDesugaringEventConsumer(
        AppView<?> appView, Consumer<ProgramMethod> methodConsumer, SyntheticAdditions additions) {
      super(appView);
      this.methodConsumer = methodConsumer;
      this.additions = additions;
    }

    @Override
    public void acceptDesugaredLibraryRetargeterDispatchProgramClass(DexProgramClass clazz) {
      clazz.programMethods().forEach(methodConsumer);
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
      methodConsumer.accept(method);
      ProgramMethod callback = desugaredLibraryAPIConverter.generateCallbackIfRequired(method);
      if (callback != null) {
        methodConsumer.accept(callback);
      }
    }
  }
}
