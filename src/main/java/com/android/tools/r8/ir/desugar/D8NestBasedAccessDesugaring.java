package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
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

  private final Map<DexMethod, DexMethod> methodMap = new ConcurrentHashMap<>();
  private final Map<DexMethod, DexMethod> initializerMap = new ConcurrentHashMap<>();
  private final Map<DexField, DexMethod> staticGetToMethodMap = new ConcurrentHashMap<>();
  private final Map<DexField, DexMethod> staticPutToMethodMap = new ConcurrentHashMap<>();
  private final Map<DexField, DexMethod> instanceGetToMethodMap = new ConcurrentHashMap<>();
  private final Map<DexField, DexMethod> instancePutToMethodMap = new ConcurrentHashMap<>();

  // Map the nest host to its nest members, including the nest host itself.
  private final Map<DexType, List<DexType>> metNests = new ConcurrentHashMap<>();

  public D8NestBasedAccessDesugaring(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected void shouldRewriteCalls(DexMethod method, DexMethod bridge) {
    methodMap.put(method, bridge);
  }

  @Override
  protected void shouldRewriteInitializers(DexMethod method, DexMethod bridge) {
    initializerMap.put(method, bridge);
  }

  @Override
  protected void shouldRewriteStaticGetFields(DexField field, DexMethod bridge) {
    staticGetToMethodMap.put(field, bridge);
  }

  @Override
  protected void shouldRewriteStaticPutFields(DexField field, DexMethod bridge) {
    staticPutToMethodMap.put(field, bridge);
  }

  @Override
  protected void shouldRewriteInstanceGetFields(DexField field, DexMethod bridge) {
    instanceGetToMethodMap.put(field, bridge);
  }

  @Override
  protected void shouldRewriteInstancePutFields(DexField field, DexMethod bridge) {
    instancePutToMethodMap.put(field, bridge);
  }

  private List<DexType> getNestFor(DexProgramClass programClass) {
    if (!programClass.isInANest()) {
      return null;
    }
    DexType nestHost = programClass.getNestHost();
    return metNests.computeIfAbsent(
        nestHost, host -> extractNest(appView.definitionFor(nestHost), programClass));
  }

  private void rewriteFieldAccess(
      FieldInstruction fieldInstruction,
      InstructionListIterator instructions,
      DexMethod method,
      Map<DexField, DexMethod> fieldToMethodMap) {
    DexMethod newTarget = fieldToMethodMap.get(fieldInstruction.getField());
    if (newTarget != null && method.holder != newTarget.holder) {
      instructions.replaceCurrentInstruction(
          new InvokeStatic(newTarget, fieldInstruction.outValue(), fieldInstruction.inValues()));
    }
  }

  public void rewriteNestBasedAccesses(
      DexEncodedMethod encodedMethod, IRCode code, AppView<?> appView) {
    // We are compiling its code so it has to be a non-null program class.
    DexProgramClass currentClass =
        appView.definitionFor(encodedMethod.method.holder).asProgramClass();
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
          registerInvoke(methodCalled, nest, currentClass);
          DexMethod newTarget = methodMap.get(methodCalled);
          if (newTarget != null && encodedMethod.method.holder != newTarget.holder) {
            instructions.replaceCurrentInstruction(
                new InvokeStatic(newTarget, invokeMethod.outValue(), invokeMethod.arguments()));
          } else {
            newTarget = initializerMap.get(methodCalled);
            if (newTarget != null && encodedMethod.method.holder != newTarget.holder) {
              // insert extra null value and replace call.
              instructions.previous();
              Value extraNullValue =
                  instructions.insertConstNullInstruction(code, appView.options());
              instructions.next();
              List<Value> parameters = new ArrayList<>(invokeMethod.arguments());
              parameters.add(extraNullValue);
              instructions.replaceCurrentInstruction(
                  new InvokeDirect(newTarget, invokeMethod.outValue(), parameters));
            }
          }
        } else if (instruction.isFieldInstruction()) {
          FieldInstruction fieldInstruction = instruction.asFieldInstruction();
          if (instruction.isInstanceGet()) {
            registerFieldAccess(fieldInstruction.getField(), true, nest, currentClass);
            rewriteFieldAccess(
                fieldInstruction, instructions, encodedMethod.method, instanceGetToMethodMap);
          } else if (instruction.isInstancePut()) {
            registerFieldAccess(fieldInstruction.getField(), false, nest, currentClass);
            rewriteFieldAccess(
                fieldInstruction, instructions, encodedMethod.method, instancePutToMethodMap);
          } else if (instruction.isStaticGet()) {
            registerFieldAccess(fieldInstruction.getField(), true, nest, currentClass);
            rewriteFieldAccess(
                fieldInstruction, instructions, encodedMethod.method, staticGetToMethodMap);
          } else if (instruction.isStaticPut()) {
            registerFieldAccess(fieldInstruction.getField(), false, nest, currentClass);
            rewriteFieldAccess(
                fieldInstruction, instructions, encodedMethod.method, staticPutToMethodMap);
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
    converter.optimizeSynthesizedMethodsConcurrently(
        deferredBridgesToAdd.keySet(), executorService);
  }

  // In D8, programClass are processed on the fly so they do not need to be processed again here.
  @Override
  protected boolean shouldProcessClassInNest(DexClass clazz, List<DexType> nest) {
    return clazz.isNotProgramClass();
  }
}
