package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DefaultUseRegistryWithResult;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class InvokeSpecialToDefaultLibraryMethodUseRegistry
    extends DefaultUseRegistryWithResult<Boolean, ProgramMethod> {

  InvokeSpecialToDefaultLibraryMethodUseRegistry(
      AppView<AppInfoWithLiveness> appView, ProgramMethod context) {
    super(appView, context, false);
    assert context.getHolder().isInterface();
  }

  @Override
  public void registerInvokeSpecial(DexMethod method) {
    ProgramMethod context = getContext();
    if (!method.getHolderType().isIdenticalTo(context.getHolderType())) {
      return;
    }

    DexEncodedMethod definition = context.getHolder().lookupMethod(method);
    if (definition != null && definition.belongsToVirtualPool()) {
      setResult(true);
    }
  }
}
