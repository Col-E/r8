// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.utils.collections.SortedProgramMethodSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * It is possible in class files to have an invoke-special to a virtual method in the same class
 * than the method holding the invoke-special. Such invoke-special are executed correctly on the
 * JVM, but cannot be expressed in terms of invoke-direct or invoke-super in dex. This class
 * introduces bridges to support the case described: the virtual method code is moved to a private
 * synthetic method, and a bridging virtual method with the initial method name and flags is
 * inserted.
 */
public class InvokeSpecialBridgeSynthesizer {

  private static final String INVOKE_SPECIAL_BRIDGE_PREFIX = "$invoke$special$";

  private final AppView<?> appView;

  Map<DexMethod, DexMethod> bridges = new ConcurrentHashMap<>();

  public InvokeSpecialBridgeSynthesizer(AppView<?> appView) {
    this.appView = appView;
  }

  public DexMethod registerBridgeForMethod(DexEncodedMethod method) {
    assert method.isVirtualMethod();
    assert !method.getAccessFlags().isFinal();
    return bridges.computeIfAbsent(
        method.getReference(),
        vMethod ->
            vMethod.withName(
                appView
                    .dexItemFactory()
                    .createString(INVOKE_SPECIAL_BRIDGE_PREFIX + vMethod.name.toString()),
                appView.dexItemFactory()));
  }

  public void insertBridges(IRConverter converter, ExecutorService executorService)
      throws ExecutionException {
    SortedProgramMethodSet insertedBridges = SortedProgramMethodSet.create();
    bridges.forEach(
        (virtualMethod, directMethod) -> {
          insertedBridges.add(insertBridge(virtualMethod, directMethod));
        });
    converter.processMethodsConcurrently(insertedBridges, executorService);
  }

  private ProgramMethod insertBridge(DexMethod virtualMethod, DexMethod directMethod) {
    assert virtualMethod.holder == directMethod.holder;
    DexProgramClass holder = appView.definitionFor(virtualMethod.holder).asProgramClass();
    assert holder.lookupVirtualMethod(virtualMethod) != null;
    assert holder.lookupDirectMethod(directMethod) == null;
    DexEncodedMethod initialVirtualMethod = holder.lookupVirtualMethod(virtualMethod);
    DexEncodedMethod newDirectMethod = initialVirtualMethod.toPrivateSyntheticMethod(directMethod);
    DexEncodedMethod bridge =
        newDirectMethod.toInvokeSpecialForwardingMethod(
            holder, virtualMethod, initialVirtualMethod.accessFlags, appView.dexItemFactory());
    holder.replaceVirtualMethod(virtualMethod, oldMethod -> bridge);
    holder.addDirectMethod(newDirectMethod);
    return new ProgramMethod(holder, bridge);
  }
}
