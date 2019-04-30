package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import java.util.HashMap;
import java.util.ListIterator;

public class NestBasedAccessDesugaringRewriter extends NestBasedAccessDesugaring {

  private HashMap<DexMethod, DexMethod> methodToRewrite = new HashMap<DexMethod, DexMethod>();

  public NestBasedAccessDesugaringRewriter(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected void shouldRewriteCalls(DexMethod method, DexMethod bridge) {
    methodToRewrite.put(method, bridge);
  }

  public void rewriteNestBasedAccesses(DexEncodedMethod encodedMethod, IRCode code) {
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
        }
        // TODO(b/130529338): support fields and initializers
      }
    }
  }
}
