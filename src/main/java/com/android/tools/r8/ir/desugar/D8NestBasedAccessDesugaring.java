package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.ArrayList;
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

  // Map the nest host to its nest members, including the nest host itself.
  private final Map<DexType, List<DexType>> metNests = new ConcurrentHashMap<>();

  public D8NestBasedAccessDesugaring(AppView<?> appView) {
    super(appView);
  }

  private List<DexType> getNestFor(DexClass clazz) {
    DexType nestHostType = clazz.getNestHost();
    return metNests.computeIfAbsent(nestHostType, host -> extractNest(clazz));
  }

  public void rewriteNestBasedAccesses(
      DexEncodedMethod encodedMethod, IRCode code, AppView<?> appView) {
    DexClass currentClass = appView.definitionFor(encodedMethod.method.holder);
    assert currentClass != null;
    if (!currentClass.isInANest()) {
      return;
    }
    List<DexType> nest = getNestFor(currentClass);

    ListIterator<BasicBlock> blocks = code.listIterator();
    while (blocks.hasNext()) {
      BasicBlock block = blocks.next();
      InstructionListIterator instructions = block.listIterator();
      while (instructions.hasNext()) {
        Instruction instruction = instructions.next();
        if (instruction.isInvokeMethod() && !instruction.isInvokeSuper()) {
          InvokeMethod invokeMethod = instruction.asInvokeMethod();
          DexMethod methodCalled = invokeMethod.getInvokedMethod();
          DexEncodedMethod encodedMethodCalled = appView.definitionFor(methodCalled);
          if (encodedMethodCalled != null
              && invokeRequiresRewriting(encodedMethodCalled, nest, encodedMethod.method.holder)) {
            DexMethod bridge = ensureInvokeBridge(encodedMethodCalled);
            if (encodedMethodCalled.isInstanceInitializer()) {
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
          DexEncodedField encodedField =
              appView.definitionFor(instruction.asFieldInstruction().getField());
          if (encodedField != null
              && fieldAccessRequiresRewriting(encodedField, nest, encodedMethod.method.holder)) {
            if (instruction.isInstanceGet() || instruction.isStaticGet()) {
              DexMethod bridge = ensureFieldAccessBridge(encodedField, true);
              instructions.replaceCurrentInstruction(
                  new InvokeStatic(bridge, instruction.outValue(), instruction.inValues()));
            } else {
              assert instruction.isInstancePut() || instruction.isStaticPut();
              DexMethod bridge = ensureFieldAccessBridge(encodedField, false);
              instructions.replaceCurrentInstruction(
                  new InvokeStatic(bridge, instruction.outValue(), instruction.inValues()));
            }
          }
        }
      }
    }
  }

  private void processNestsConcurrently(
      List<List<DexType>> liveNests, ExecutorService executorService) throws ExecutionException {
    List<Future<?>> futures = new ArrayList<>();
    for (List<DexType> nest : liveNests) {
      futures.add(asyncProcessNest(nest, executorService));
    }
    ThreadUtils.awaitFutures(futures);
  }

  public void desugarNestBasedAccess(
      DexApplication.Builder<?> builder, ExecutorService executorService, IRConverter converter)
      throws ExecutionException {
    List<List<DexType>> metNests = new ArrayList<>(this.metNests.values());
    processNestsConcurrently(metNests, executorService);
    addDeferredBridges();
    synthetizeNestConstructor(builder);
    optimizeDeferredBridgesConcurrently(executorService, converter);
  }

  // In D8, programClass are processed on the fly so they do not need to be processed again here.
  @Override
  protected boolean shouldProcessClassInNest(DexClass clazz, List<DexType> nest) {
    return clazz.isNotProgramClass();
  }
}
