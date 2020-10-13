// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.SortedProgramMethodSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

// Summary:
// - Process all methods compiled rewriting nest based access (Methods processed concurrently).
// - Process classes on class path in reachable nests to find bridges to add
//    in Program classes (Nests processed concurrently).
// - Add bridges and nest constructor class (Sequential).
// - Optimize bridges (Bridges processed concurrently).
public class D8NestBasedAccessDesugaring extends NestBasedAccessDesugaring {

  // Maps a nest host to a class met which has that nest host.
  // The value is used because the nest host might be missing.
  private final Map<DexType, DexProgramClass> metNestHosts = new ConcurrentHashMap<>();

  public D8NestBasedAccessDesugaring(AppView<?> appView) {
    super(appView);
  }

  public void rewriteNestBasedAccesses(ProgramMethod method, IRCode code, AppView<?> appView) {
    if (!method.getHolder().isInANest()) {
      return;
    }
    metNestHosts.put(method.getHolder().getNestHost(), method.getHolder());

    ListIterator<BasicBlock> blocks = code.listIterator();
    while (blocks.hasNext()) {
      BasicBlock block = blocks.next();
      InstructionListIterator instructions = block.listIterator(code);
      while (instructions.hasNext()) {
        Instruction instruction = instructions.next();
        if (instruction.isInvokeMethod()) {
          InvokeMethod invokeMethod = instruction.asInvokeMethod();
          DexMethod invokedMethod = invokeMethod.getInvokedMethod();
          if (!invokedMethod.holder.isClassType()) {
            continue;
          }
          // Since we only need to desugar accesses to private methods, and all accesses to private
          // methods must be accessing the private method directly on its holder, we can lookup the
          // method on the holder instead of resolving the method.
          DexClass holder = appView.definitionForHolder(invokedMethod);
          DexEncodedMethod definition = invokedMethod.lookupOnClass(holder);
          if (definition != null && invokeRequiresRewriting(definition, method)) {
            DexMethod bridge = ensureInvokeBridge(definition);
            if (definition.isInstanceInitializer()) {
              instructions.previous();
              Value extraNullValue =
                  instructions.insertConstNullInstruction(code, appView.options());
              instructions.next();
              List<Value> parameters = new ArrayList<>(invokeMethod.arguments());
              parameters.add(extraNullValue);
              instructions.replaceCurrentInstruction(
                  new InvokeDirect(bridge, invokeMethod.outValue(), parameters));
            } else {
              instructions.replaceCurrentInstruction(
                  new InvokeStatic(bridge, invokeMethod.outValue(), invokeMethod.arguments()));
            }
          }
        } else if (instruction.isFieldInstruction()) {
          // Since we only need to desugar accesses to private fields, and all accesses to private
          // fields must be accessing the private field directly on its holder, we can lookup the
          // field on the holder instead of resolving the field.
          FieldInstruction fieldInstruction = instruction.asFieldInstruction();
          DexClass holder = appView.definitionForHolder(fieldInstruction.getField());
          DexEncodedField field = fieldInstruction.getField().lookupOnClass(holder);
          if (field != null && fieldAccessRequiresRewriting(field, method)) {
            if (instruction.isInstanceGet() || instruction.isStaticGet()) {
              DexMethod bridge = ensureFieldAccessBridge(field, true);
              instructions.replaceCurrentInstruction(
                  new InvokeStatic(bridge, instruction.outValue(), instruction.inValues()));
            } else {
              assert instruction.isInstancePut() || instruction.isStaticPut();
              DexMethod bridge = ensureFieldAccessBridge(field, false);
              instructions.replaceCurrentInstruction(
                  new InvokeStatic(bridge, instruction.outValue(), instruction.inValues()));
            }
          }
        }
      }
    }
  }

  private void processNestsConcurrently(ExecutorService executorService) throws ExecutionException {
    List<Future<?>> futures = new ArrayList<>();
    for (DexProgramClass clazz : metNestHosts.values()) {
      futures.add(asyncProcessNest(clazz, executorService));
    }
    ThreadUtils.awaitFutures(futures);
  }

  private void addDeferredBridges() {
    addDeferredBridges(bridges.values());
    addDeferredBridges(getFieldBridges.values());
    addDeferredBridges(putFieldBridges.values());
  }

  private void addDeferredBridges(Collection<ProgramMethod> bridges) {
    for (ProgramMethod bridge : bridges) {
      bridge.getHolder().addMethod(bridge.getDefinition());
    }
  }

  private void optimizeDeferredBridgesConcurrently(
      ExecutorService executorService, IRConverter converter) throws ExecutionException {
    SortedProgramMethodSet methods = SortedProgramMethodSet.create();
    methods.addAll(bridges.values());
    methods.addAll(getFieldBridges.values());
    methods.addAll(putFieldBridges.values());
    converter.processMethodsConcurrently(methods, executorService);
  }

  public void desugarNestBasedAccess(
      DexApplication.Builder<?> builder, ExecutorService executorService, IRConverter converter)
      throws ExecutionException {
    processNestsConcurrently(executorService);
    addDeferredBridges();
    synthesizeNestConstructor(builder);
    optimizeDeferredBridgesConcurrently(executorService, converter);
  }

  // In D8, programClass are processed on the fly so they do not need to be processed again here.
  @Override
  protected boolean shouldProcessClassInNest(DexClass clazz, List<DexType> nest) {
    return clazz.isClasspathClass();
  }

  @Override
  void reportMissingNestHost(DexClass clazz) {
    appView.options().nestDesugaringWarningMissingNestHost(clazz);
  }

  @Override
  void reportIncompleteNest(List<DexType> nest) {
    appView.options().nestDesugaringWarningIncompleteNest(nest, appView);
  }
}
