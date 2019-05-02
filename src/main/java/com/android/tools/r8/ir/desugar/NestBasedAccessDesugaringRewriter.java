package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.ListIterator;
import java.util.Map;

public class NestBasedAccessDesugaringRewriter extends NestBasedAccessDesugaring {

  private HashMap<DexMethod, DexMethod> methodToRewrite = new HashMap<>();
  private final Map<DexField, DexMethod> staticGetToMethodMap = new IdentityHashMap<>();
  private final Map<DexField, DexMethod> staticPutToMethodMap = new IdentityHashMap<>();
  private final Map<DexField, DexMethod> instanceGetToMethodMap = new IdentityHashMap<>();
  private final Map<DexField, DexMethod> instancePutToMethodMap = new IdentityHashMap<>();

  public NestBasedAccessDesugaringRewriter(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected void shouldRewriteCalls(DexMethod method, DexMethod bridge) {
    methodToRewrite.put(method, bridge);
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

  private void rewriteFieldAccess(
      Instruction instruction,
      InstructionListIterator instructions,
      DexMethod method,
      Map<DexField, DexMethod> fieldToMethodMap) {
    DexField field = instruction.asFieldInstruction().getField();
    DexMethod newTarget = fieldToMethodMap.get(field);
    if (newTarget != null && method != newTarget) {
      instructions.replaceCurrentInstruction(
          new InvokeStatic(newTarget, instruction.outValue(), instruction.inValues()));
    }
  }

  public void rewriteNestBasedAccesses(
      DexEncodedMethod encodedMethod, IRCode code, AppView<?> appView) {
    if (methodToRewrite.isEmpty()) {
      return;
    }
    ListIterator<BasicBlock> blocks = code.listIterator();
    while (blocks.hasNext()) {
      BasicBlock block = blocks.next();
      InstructionListIterator instructions = block.listIterator();
      while (instructions.hasNext()) {
        Instruction instruction = instructions.next();
        if (instruction.isInvokeMethod() && !instruction.isInvokeSuper()) {
          InvokeMethod invokeMethod = instruction.asInvokeMethod();
          DexMethod methodCalled = invokeMethod.getInvokedMethod();
          DexMethod newTarget = methodToRewrite.get(methodCalled);
          if (newTarget != null && encodedMethod.method != newTarget) {
            instructions.replaceCurrentInstruction(
                new InvokeStatic(newTarget, invokeMethod.outValue(), invokeMethod.arguments()));
          }
        } else if (instruction.isInstanceGet()) {
          rewriteFieldAccess(
              instruction, instructions, encodedMethod.method, instanceGetToMethodMap);
        } else if (instruction.isInstancePut()) {
          rewriteFieldAccess(
              instruction, instructions, encodedMethod.method, instancePutToMethodMap);
        } else if (instruction.isStaticGet()) {
          rewriteFieldAccess(instruction, instructions, encodedMethod.method, staticGetToMethodMap);
        } else if (instruction.isStaticPut()) {
          rewriteFieldAccess(instruction, instructions, encodedMethod.method, staticPutToMethodMap);
        }
        // TODO(b/130529338): support initializers
      }
    }
  }


}
