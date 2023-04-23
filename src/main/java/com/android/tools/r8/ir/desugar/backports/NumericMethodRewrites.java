package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.cf.code.CfArithmeticBinop;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter.FullMethodInvokeRewriter;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter.MethodInvokeRewriter;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import java.util.Collection;
import java.util.Collections;
import org.objectweb.asm.Opcodes;

public final class NumericMethodRewrites {

  public static MethodInvokeRewriter rewriteToInvokeMath() {
    return (invoke, factory) -> {
      DexMethod method = invoke.getMethod();
      return new CfInvoke(
          Opcodes.INVOKESTATIC,
          factory.createMethod(factory.mathType, method.proto, method.name),
          false);
    };
  }

  public static MethodInvokeRewriter rewriteToAddInstruction() {
    return (invoke, factory) -> {
      NumericType numericType = NumericType.fromDexType(invoke.getMethod().getReturnType());
      return CfArithmeticBinop.operation(CfArithmeticBinop.Opcode.Add, numericType);
    };
  }

  public static MethodInvokeRewriter rewriteAsIdentity() {
    return new FullMethodInvokeRewriter() {
      @Override
      public Collection<CfInstruction> rewrite(
          CfInvoke invoke, DexItemFactory factory, LocalStackAllocator localStackAllocator) {
        // The invoke consumes the stack value and pushes another assumed to be the same.
        return Collections.emptyList();
      }
    };
  }

  private NumericMethodRewrites() {
  }
}
