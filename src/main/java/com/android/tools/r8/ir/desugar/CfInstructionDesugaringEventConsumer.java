// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.invokespecial.InvokeSpecialBridgeInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Class that gets notified for structural changes made as a result of desugaring (e.g., the
 * inserting of a new method).
 */
public abstract class CfInstructionDesugaringEventConsumer {

  public static D8CfInstructionDesugaringEventConsumer createForD8() {
    return new D8CfInstructionDesugaringEventConsumer();
  }

  public static R8CfInstructionDesugaringEventConsumer createForR8() {
    return new R8CfInstructionDesugaringEventConsumer();
  }

  public static CfInstructionDesugaringEventConsumer createForDesugaredCode() {
    return new CfInstructionDesugaringEventConsumer() {
      @Override
      public void acceptInvokeSpecialBridgeInfo(InvokeSpecialBridgeInfo info) {
        assert false;
      }
    };
  }

  public abstract void acceptInvokeSpecialBridgeInfo(InvokeSpecialBridgeInfo info);

  public static class D8CfInstructionDesugaringEventConsumer
      extends CfInstructionDesugaringEventConsumer {

    private final Map<DexReference, InvokeSpecialBridgeInfo> pendingInvokeSpecialBridges =
        new LinkedHashMap<>();

    @Override
    public void acceptInvokeSpecialBridgeInfo(InvokeSpecialBridgeInfo info) {
      synchronized (pendingInvokeSpecialBridges) {
        assert !pendingInvokeSpecialBridges.containsKey(info.getNewDirectMethod().getReference());
        pendingInvokeSpecialBridges.put(info.getNewDirectMethod().getReference(), info);
      }
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

    private final List<InvokeSpecialBridgeInfo> pendingInvokeSpecialBridges = new ArrayList<>();

    @Override
    public void acceptInvokeSpecialBridgeInfo(InvokeSpecialBridgeInfo info) {
      synchronized (pendingInvokeSpecialBridges) {
        pendingInvokeSpecialBridges.add(info);
      }
    }

    public void finalizeDesugaring(AppView<? extends AppInfoWithClassHierarchy> appView) {
      Collections.sort(pendingInvokeSpecialBridges);
      pendingInvokeSpecialBridges.forEach(
          info ->
              info.getVirtualMethod()
                  .getDefinition()
                  .setCode(info.getVirtualMethodCode(), appView));
    }
  }
}
