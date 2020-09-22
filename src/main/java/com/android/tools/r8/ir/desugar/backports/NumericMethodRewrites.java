package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.cf.code.CfArithmeticBinop;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter.FullMethodInvokeRewriter;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter.MethodInvokeRewriter;
import java.util.ListIterator;
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
      return new CfArithmeticBinop(CfArithmeticBinop.Opcode.Add, numericType);
    };
  }

  public static MethodInvokeRewriter rewriteAsIdentity() {
    return new FullMethodInvokeRewriter() {
      @Override
      public void rewrite(
          CfInvoke invoke, ListIterator<CfInstruction> iterator, DexItemFactory factory) {
        // The invoke consumes the stack value and pushes another assumed to be the same.
        iterator.remove();
      }
    };
  }

  private NumericMethodRewrites() {
  }
}
