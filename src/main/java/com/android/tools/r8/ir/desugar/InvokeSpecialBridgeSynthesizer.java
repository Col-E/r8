// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.analysis.EnqueuerInvokeAnalysis;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.synthetic.ForwardMethodBuilder;
import com.android.tools.r8.utils.collections.SortedProgramMethodSet;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;
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

  private final Map<DexMethod, DexMethod> bridges = new ConcurrentHashMap<>();
  private final Set<DexMethod> seenBridges = Sets.newIdentityHashSet();

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

  // In R8, insertBridgesForR8 is called multiple times until fixed point.
  // The bridges are inserted prior to IR conversion.
  public SortedProgramMethodSet insertBridgesForR8() {
    SortedProgramMethodSet insertedDirectMethods = SortedProgramMethodSet.create();
    bridges.forEach(
        (vMethod, dMethod) -> {
          if (seenBridges.add(vMethod)) {
            insertedDirectMethods.add(insertBridge(getVirtualMethod(vMethod), dMethod));
          }
        });
    return insertedDirectMethods;
  }

  // In D8, insertBridgesForD8 is called once.
  // The bridges are inserted after IR conversion hence the bridges need to be processed.
  public void insertBridgesForD8(IRConverter converter, ExecutorService executorService)
      throws ExecutionException {
    SortedProgramMethodSet insertedBridges = SortedProgramMethodSet.create();
    bridges.forEach(
        (virtualMethod, directMethod) -> {
          ProgramMethod programVirtualMethod = getVirtualMethod(virtualMethod);
          insertBridge(programVirtualMethod, directMethod);
          insertedBridges.add(programVirtualMethod);
        });
    converter.processMethodsConcurrently(insertedBridges, executorService);
  }

  private ProgramMethod getVirtualMethod(DexMethod virtualMethod) {
    DexProgramClass holder = appView.definitionFor(virtualMethod.holder).asProgramClass();
    assert holder.lookupVirtualMethod(virtualMethod) != null;
    DexEncodedMethod encodedVirtualMethod = holder.lookupVirtualMethod(virtualMethod);
    return new ProgramMethod(holder, encodedVirtualMethod);
  }

  private ProgramMethod insertBridge(ProgramMethod virtualMethod, DexMethod directMethod) {
    assert virtualMethod.getHolderType() == directMethod.holder;
    DexProgramClass holder = virtualMethod.getHolder();
    assert holder.lookupDirectMethod(directMethod) == null;
    DexEncodedMethod initialVirtualMethod = virtualMethod.getDefinition();
    DexEncodedMethod newDirectMethod = initialVirtualMethod.toPrivateSyntheticMethod(directMethod);
    CfCode forwardingCode =
        ForwardMethodBuilder.builder(appView.dexItemFactory())
            .setDirectTarget(directMethod, holder.isInterface())
            .setNonStaticSource(virtualMethod.getReference())
            .build();
    initialVirtualMethod.setCode(forwardingCode, appView);
    initialVirtualMethod.markNotProcessed();
    holder.addDirectMethod(newDirectMethod);
    return new ProgramMethod(holder, newDirectMethod);
  }

  public EnqueuerInvokeAnalysis getEnqueuerInvokeAnalysis() {
    return new InvokeSpecialBridgeAnalysis();
  }

  private class InvokeSpecialBridgeAnalysis implements EnqueuerInvokeAnalysis {

    @Override
    public void traceInvokeStatic(DexMethod invokedMethod, ProgramMethod context) {}

    @Override
    public void traceInvokeDirect(DexMethod invokedMethod, ProgramMethod context) {
      DexEncodedMethod lookup = context.getHolder().lookupMethod(invokedMethod);
      if (lookup != null
          && lookup.isNonPrivateVirtualMethod()
          && context.getHolderType() == invokedMethod.holder
          && !context.getHolder().isInterface()
          && !lookup.accessFlags.isFinal()) {
        registerBridgeForMethod(lookup);
      }
    }

    @Override
    public void traceInvokeInterface(DexMethod invokedMethod, ProgramMethod context) {}

    @Override
    public void traceInvokeSuper(DexMethod invokedMethod, ProgramMethod context) {}

    @Override
    public void traceInvokeVirtual(DexMethod invokedMethod, ProgramMethod context) {}
  }
}
