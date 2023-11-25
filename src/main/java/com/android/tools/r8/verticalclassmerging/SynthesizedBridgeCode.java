package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.synthetic.AbstractSynthesizedCode;
import com.android.tools.r8.ir.synthetic.ForwardMethodSourceCode;
import java.util.function.Consumer;
import java.util.function.Function;

public class SynthesizedBridgeCode extends AbstractSynthesizedCode {

  private DexMethod method;
  private DexMethod invocationTarget;
  private InvokeType type;
  private final boolean isInterface;

  public SynthesizedBridgeCode(
      DexMethod method, DexMethod invocationTarget, InvokeType type, boolean isInterface) {
    this.method = method;
    this.invocationTarget = invocationTarget;
    this.type = type;
    this.isInterface = isInterface;
  }

  public DexMethod getMethod() {
    return method;
  }

  public DexMethod getTarget() {
    return invocationTarget;
  }

  // By the time the synthesized code object is created, vertical class merging still has not
  // finished. Therefore it is possible that the method signatures `method` and `invocationTarget`
  // will change as a result of additional class merging operations. To deal with this, the
  // vertical class merger explicitly invokes this method to update `method` and `invocation-
  // Target` when vertical class merging has finished.
  //
  // Note that, without this step, these method signatures might refer to intermediate signatures
  // that are only present in the middle of vertical class merging, which means that the graph
  // lens will not work properly (since the graph lens generated by vertical class merging only
  // expects to be applied to method signatures from *before* vertical class merging or *after*
  // vertical class merging).
  public void updateMethodSignatures(Function<DexMethod, DexMethod> transformer) {
    method = transformer.apply(method);
    invocationTarget = transformer.apply(invocationTarget);
  }

  @Override
  public SourceCodeProvider getSourceCodeProvider() {
    ForwardMethodSourceCode.Builder forwardSourceCodeBuilder =
        ForwardMethodSourceCode.builder(method);
    forwardSourceCodeBuilder
        .setReceiver(method.holder)
        .setTargetReceiver(type.isStatic() ? null : method.holder)
        .setTarget(invocationTarget)
        .setInvokeType(type)
        .setIsInterface(isInterface);
    return forwardSourceCodeBuilder::build;
  }

  @Override
  public Consumer<UseRegistry> getRegistryCallback(DexClassAndMethod method) {
    return registry -> {
      assert registry.getTraversalContinuation().shouldContinue();
      switch (type) {
        case DIRECT:
          registry.registerInvokeDirect(invocationTarget);
          break;
        case STATIC:
          registry.registerInvokeStatic(invocationTarget);
          break;
        case VIRTUAL:
          registry.registerInvokeVirtual(invocationTarget);
          break;
        default:
          throw new Unreachable("Unexpected invocation type: " + type);
      }
    };
  }
}
