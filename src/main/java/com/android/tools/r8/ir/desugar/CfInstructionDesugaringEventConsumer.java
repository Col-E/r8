// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.D8MethodProcessor;
import com.android.tools.r8.ir.desugar.invokespecial.InvokeSpecialBridgeInfo;
import com.android.tools.r8.ir.desugar.invokespecial.InvokeSpecialToSelfDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.lambda.LambdaDeserializationMethodRemover;
import com.android.tools.r8.ir.desugar.lambda.LambdaDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.lambda.LambdaDesugaringLens;
import com.android.tools.r8.ir.desugar.nest.NestBasedAccessDesugaringEventConsumer;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Class that gets notified for structural changes made as a result of desugaring (e.g., the
 * inserting of a new method).
 */
public abstract class CfInstructionDesugaringEventConsumer
    implements InvokeSpecialToSelfDesugaringEventConsumer,
        LambdaDesugaringEventConsumer,
        NestBasedAccessDesugaringEventConsumer {

  public static D8CfInstructionDesugaringEventConsumer createForD8(
      Consumer<LambdaClass> lambdaClassConsumer, D8MethodProcessor methodProcessor) {
    return new D8CfInstructionDesugaringEventConsumer(lambdaClassConsumer, methodProcessor);
  }

  public static R8CfInstructionDesugaringEventConsumer createForR8(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return new R8CfInstructionDesugaringEventConsumer(appView);
  }

  public static CfInstructionDesugaringEventConsumer createForDesugaredCode() {
    return new CfInstructionDesugaringEventConsumer() {

      @Override
      public void acceptInvokeSpecialBridgeInfo(InvokeSpecialBridgeInfo info) {
        assert false;
      }

      @Override
      public void acceptLambdaClass(LambdaClass lambdaClass, ProgramMethod context) {
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
    };
  }

  public static class D8CfInstructionDesugaringEventConsumer
      extends CfInstructionDesugaringEventConsumer {

    private final Consumer<LambdaClass> lambdaClassConsumer;
    private final D8MethodProcessor methodProcessor;

    private final Map<DexReference, InvokeSpecialBridgeInfo> pendingInvokeSpecialBridges =
        new LinkedHashMap<>();

    private D8CfInstructionDesugaringEventConsumer(
        Consumer<LambdaClass> lambdaClassConsumer, D8MethodProcessor methodProcessor) {
      this.lambdaClassConsumer = lambdaClassConsumer;
      this.methodProcessor = methodProcessor;
    }

    @Override
    public void acceptInvokeSpecialBridgeInfo(InvokeSpecialBridgeInfo info) {
      synchronized (pendingInvokeSpecialBridges) {
        assert !pendingInvokeSpecialBridges.containsKey(info.getNewDirectMethod().getReference());
        pendingInvokeSpecialBridges.put(info.getNewDirectMethod().getReference(), info);
      }
    }

    @Override
    public void acceptLambdaClass(LambdaClass lambdaClass, ProgramMethod context) {
      lambdaClassConsumer.accept(lambdaClass);
    }

    @Override
    public void acceptNestFieldGetBridge(ProgramField target, ProgramMethod bridge) {
      methodProcessor.scheduleDesugaredMethodForProcessing(bridge);
    }

    @Override
    public void acceptNestFieldPutBridge(ProgramField target, ProgramMethod bridge) {
      methodProcessor.scheduleDesugaredMethodForProcessing(bridge);
    }

    @Override
    public void acceptNestMethodBridge(ProgramMethod target, ProgramMethod bridge) {
      methodProcessor.scheduleDesugaredMethodForProcessing(bridge);
    }

    public List<ProgramMethod> finalizeDesugaring(AppView<?> appView) {
      List<ProgramMethod> needsReprocessing = new ArrayList<>();
      finalizeInvokeSpecialDesugaring(appView, needsReprocessing::add);
      return needsReprocessing;
    }

    private void finalizeInvokeSpecialDesugaring(
        AppView<?> appView, Consumer<ProgramMethod> needsReprocessing) {
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
                needsReprocessing.accept(info.getVirtualMethod());
              });

      pendingInvokeSpecialBridges.clear();
    }

    public boolean verifyNothingToFinalize() {
      assert pendingInvokeSpecialBridges.isEmpty();
      return true;
    }
  }

  public static class R8CfInstructionDesugaringEventConsumer
      extends CfInstructionDesugaringEventConsumer {

    private final AppView<? extends AppInfoWithClassHierarchy> appView;

    private final Map<LambdaClass, ProgramMethod> synthesizedLambdaClasses =
        new IdentityHashMap<>();
    private final List<InvokeSpecialBridgeInfo> pendingInvokeSpecialBridges = new ArrayList<>();

    public R8CfInstructionDesugaringEventConsumer(
        AppView<? extends AppInfoWithClassHierarchy> appView) {
      this.appView = appView;
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
    }

    @Override
    public void acceptNestFieldGetBridge(ProgramField target, ProgramMethod bridge) {
      // Intentionally empty. These bridges will be hit by the tracing in R8 as if they were present
      // in the input code, and thus nothing needs to be done.
    }

    @Override
    public void acceptNestFieldPutBridge(ProgramField target, ProgramMethod bridge) {
      // Intentionally empty. These bridges will be hit by the tracing in R8 as if they were present
      // in the input code, and thus nothing needs to be done.
    }

    @Override
    public void acceptNestMethodBridge(ProgramMethod target, ProgramMethod bridge) {
      // Intentionally empty. These bridges will be hit by the tracing in R8 as if they were present
      // in the input code, and thus nothing needs to be done.
    }

    public void finalizeDesugaring(LambdaDesugaringLens.Builder lambdaDesugaringLensBuilder) {
      finalizeInvokeSpecialDesugaring();
      finalizeLambdaDesugaring(lambdaDesugaringLensBuilder);
    }

    private void finalizeInvokeSpecialDesugaring() {
      Collections.sort(pendingInvokeSpecialBridges);
      pendingInvokeSpecialBridges.forEach(
          info ->
              info.getVirtualMethod()
                  .getDefinition()
                  .setCode(info.getVirtualMethodCode(), appView));
    }

    private void finalizeLambdaDesugaring(
        LambdaDesugaringLens.Builder lambdaDesugaringLensBuilder) {
      Set<DexProgramClass> classesWithSerializableLambdas = Sets.newIdentityHashSet();
      synthesizedLambdaClasses.forEach(
          (lambdaClass, context) -> {
            lambdaClass.target.ensureAccessibilityIfNeeded(false, lambdaDesugaringLensBuilder);

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
