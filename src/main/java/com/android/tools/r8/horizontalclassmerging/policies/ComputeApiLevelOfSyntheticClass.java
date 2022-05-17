// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.dex.code.CfOrDexInstruction;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import com.android.tools.r8.synthesis.SyntheticItems;
import java.util.ListIterator;

public class ComputeApiLevelOfSyntheticClass extends SingleClassPolicy {

  private final AppView<?> appView;
  private final SyntheticItems syntheticItems;

  public ComputeApiLevelOfSyntheticClass(AppView<?> appView) {
    this.appView = appView;
    this.syntheticItems = appView.getSyntheticItems();
  }

  @Override
  public boolean canMerge(DexProgramClass clazz) {
    assert syntheticItems.isSyntheticClass(clazz);
    clazz.forEachProgramMethod(
        programMethod -> {
          DexEncodedMethod definition = programMethod.getDefinition();
          if (definition.getApiLevelForCode().isNotSetApiLevel()) {
            ComputeApiLevelUseRegistry computeApiLevel =
                new ComputeApiLevelUseRegistry(appView, programMethod, appView.apiLevelCompute());
            computeApiLevel.accept(programMethod);
            ComputedApiLevel maxApiReferenceLevel = computeApiLevel.getMaxApiReferenceLevel();
            assert !maxApiReferenceLevel.isNotSetApiLevel();
            definition.setApiLevelForCode(maxApiReferenceLevel);
            definition.setApiLevelForDefinition(computeApiLevel.getMaxApiReferenceLevel());
          }
        });
    return true;
  }

  @Override
  public String getName() {
    return "ComputeApiLevelOfSyntheticClass";
  }

  private static class ComputeApiLevelUseRegistry extends UseRegistry<ProgramMethod> {

    private final AppView<?> appView;
    private final AndroidApiLevelCompute apiLevelCompute;
    private ComputedApiLevel maxApiReferenceLevel;

    public ComputeApiLevelUseRegistry(
        AppView<?> appView, ProgramMethod context, AndroidApiLevelCompute apiLevelCompute) {
      super(appView, context);
      this.appView = appView;
      this.apiLevelCompute = apiLevelCompute;
      maxApiReferenceLevel = appView.computedMinApiLevel();
    }

    @Override
    public void registerInitClass(DexType clazz) {
      assert false : "Unexpected call to an instruction that should not exist on DEX";
    }

    @Override
    public void registerRecordFieldValues(DexField[] fields) {
      assert false : "Unexpected call to an instruction that should not exist on DEX";
    }

    @Override
    public void registerInvokeVirtual(DexMethod invokedMethod) {
      setMaxApiReferenceLevel(invokedMethod);
    }

    @Override
    public void registerInvokeDirect(DexMethod invokedMethod) {
      setMaxApiReferenceLevel(invokedMethod);
    }

    @Override
    public void registerInvokeStatic(DexMethod invokedMethod) {
      setMaxApiReferenceLevel(invokedMethod);
    }

    @Override
    public void registerInvokeInterface(DexMethod invokedMethod) {
      setMaxApiReferenceLevel(invokedMethod);
    }

    @Override
    public void registerInvokeSuper(DexMethod invokedMethod) {
      setMaxApiReferenceLevel(invokedMethod);
    }

    @Override
    public void registerInstanceFieldRead(DexField field) {
      setMaxApiReferenceLevel(field);
    }

    @Override
    public void registerInstanceFieldReadFromMethodHandle(DexField field) {
      setMaxApiReferenceLevel(field);
    }

    @Override
    public void registerInstanceFieldWrite(DexField field) {
      setMaxApiReferenceLevel(field);
    }

    @Override
    public void registerInstanceFieldWriteFromMethodHandle(DexField field) {
      setMaxApiReferenceLevel(field);
    }

    @Override
    public void registerNewInstance(DexType type) {
      setMaxApiReferenceLevel(type);
    }

    @Override
    public void registerStaticFieldRead(DexField field) {
      setMaxApiReferenceLevel(field);
    }

    @Override
    public void registerStaticFieldReadFromMethodHandle(DexField field) {
      setMaxApiReferenceLevel(field);
    }

    @Override
    public void registerStaticFieldWrite(DexField field) {
      setMaxApiReferenceLevel(field);
    }

    @Override
    public void registerStaticFieldWriteFromMethodHandle(DexField field) {
      setMaxApiReferenceLevel(field);
    }

    @Override
    public void registerConstClass(
        DexType type,
        ListIterator<? extends CfOrDexInstruction> iterator,
        boolean ignoreCompatRules) {
      // Intentionally empty.
    }

    @Override
    public void registerCheckCast(DexType type, boolean ignoreCompatRules) {
      // Intentionally empty.
    }

    @Override
    public void registerSafeCheckCast(DexType type) {
      // Intentionally empty.
    }

    @Override
    public void registerTypeReference(DexType type) {
      // Intentionally empty.
    }

    @Override
    public void registerInstanceOf(DexType type) {
      // Intentionally empty.
    }

    @Override
    public void registerExceptionGuard(DexType guard) {
      setMaxApiReferenceLevel(guard);
    }

    @Override
    public void registerMethodHandle(DexMethodHandle methodHandle, MethodHandleUse use) {
      assert false : "Unexpected call to an instruction that should not exist on DEX";
    }

    @Override
    public void registerCallSite(DexCallSite callSite) {
      assert false : "Unexpected call to an instruction that should not exist on DEX";
    }

    private void setMaxApiReferenceLevel(DexReference reference) {
      maxApiReferenceLevel =
          maxApiReferenceLevel.max(
              apiLevelCompute.computeApiLevelForLibraryReference(
                  reference, apiLevelCompute.getPlatformApiLevelOrUnknown(appView)));
    }

    public ComputedApiLevel getMaxApiReferenceLevel() {
      return maxApiReferenceLevel;
    }
  }
}
