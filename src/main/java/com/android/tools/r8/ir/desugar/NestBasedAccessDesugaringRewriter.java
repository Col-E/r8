package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
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

  private HashMap<DexMethod, DexMethod> methodToRewrite = new HashMap<>();
  private HashMap<DexFieldWithAccess, DexMethod> fieldToRewrite = new HashMap<>();

  public NestBasedAccessDesugaringRewriter(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected void shouldRewriteCalls(DexMethod method, DexMethod bridge) {
    methodToRewrite.put(method, bridge);
  }

  @Override
  protected void shouldRewriteFields(DexFieldWithAccess fieldKey, DexMethod bridge) {
    fieldToRewrite.put(fieldKey, bridge);
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
        } else if (instruction.isFieldInstruction()) {
          DexEncodedField field =
              appView.definitionFor(instruction.asFieldInstruction().getField());
          if (field != null) {
            DexMethod newTarget =
                fieldToRewrite.get(
                    new DexFieldWithAccess(
                        field, instruction.isInstanceGet() || instruction.isStaticGet()));
            if (newTarget != null && encodedMethod.method != newTarget) {
              instructions.replaceCurrentInstruction(
                  new InvokeStatic(newTarget, instruction.outValue(), instruction.inValues()));
            }
          }
        }
        // TODO(b/130529338): support initializers
      }
    }
  }


}
