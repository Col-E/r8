package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistryWithResult;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class InvokeSpecialToDefaultLibraryMethodUseRegistry
    extends UseRegistryWithResult<Boolean, ProgramMethod> {

  InvokeSpecialToDefaultLibraryMethodUseRegistry(
      AppView<AppInfoWithLiveness> appView, ProgramMethod context) {
    super(appView, context, false);
    assert context.getHolder().isInterface();
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public void registerInvokeSpecial(DexMethod method) {
    ProgramMethod context = getContext();
    if (method.getHolderType() != context.getHolderType()) {
      return;
    }

    DexEncodedMethod definition = context.getHolder().lookupMethod(method);
    if (definition != null && definition.belongsToVirtualPool()) {
      setResult(true);
    }
  }

  @Override
  public void registerInitClass(DexType type) {}

  @Override
  public void registerInvokeDirect(DexMethod method) {}

  @Override
  public void registerInvokeInterface(DexMethod method) {}

  @Override
  public void registerInvokeStatic(DexMethod method) {}

  @Override
  public void registerInvokeSuper(DexMethod method) {}

  @Override
  public void registerInvokeVirtual(DexMethod method) {}

  @Override
  public void registerInstanceFieldRead(DexField field) {}

  @Override
  public void registerInstanceFieldWrite(DexField field) {}

  @Override
  public void registerStaticFieldRead(DexField field) {}

  @Override
  public void registerStaticFieldWrite(DexField field) {}

  @Override
  public void registerTypeReference(DexType type) {}
}
