// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.dex.code.CfOrDexInstruction;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.utils.AndroidApiLevelUtils;
import java.util.ListIterator;

public class ComputeApiLevelUseRegistry extends UseRegistry<ProgramMethod> {

  private final AppInfoWithClassHierarchy appInfoWithClassHierarchy;
  private final AndroidApiLevelCompute apiLevelCompute;
  private final boolean isEnabled;
  private ComputedApiLevel maxApiReferenceLevel;

  public ComputeApiLevelUseRegistry(
      AppView<?> appView, ProgramMethod context, AndroidApiLevelCompute apiLevelCompute) {
    super(appView, context);
    this.appInfoWithClassHierarchy = appView.appInfoForDesugaring();
    this.apiLevelCompute = apiLevelCompute;
    isEnabled = apiLevelCompute.isEnabled();
    maxApiReferenceLevel = appView.computedMinApiLevel();
  }

  @Override
  public void registerInitClass(DexType clazz) {
    // Intentionally empty since init class as synthesized.
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
    setMaxApiReferenceLevel(type);
  }

  @Override
  public void registerCheckCast(DexType type, boolean ignoreCompatRules) {
    setMaxApiReferenceLevel(type);
  }

  @Override
  public void registerSafeCheckCast(DexType type) {
    setMaxApiReferenceLevel(type);
  }

  @Override
  public void registerTypeReference(DexType type) {
    // Type references are OK as long as we do not have a use on them
  }

  @Override
  public void registerInstanceOf(DexType type) {
    setMaxApiReferenceLevel(type);
  }

  @Override
  public void registerExceptionGuard(DexType guard) {
    setMaxApiReferenceLevel(guard);
  }

  @Override
  public void registerMethodHandle(DexMethodHandle methodHandle, MethodHandleUse use) {
    super.registerMethodHandle(methodHandle, use);
  }

  private void setMaxApiReferenceLevel(DexReference reference) {
    if (isEnabled) {
      if (reference.isDexType()) {
        maxApiReferenceLevel =
            maxApiReferenceLevel.max(apiLevelCompute.computeApiLevelForLibraryReference(reference));
      } else if (!reference.getContextType().isClassType()) {
        maxApiReferenceLevel = maxApiReferenceLevel.max(appView.computedMinApiLevel());
      } else {
        DexClass holder = appView.definitionFor(reference.getContextType());
        ComputedApiLevel referenceApiLevel = ComputedApiLevel.unknown();
        if (holder != null) {
          referenceApiLevel =
              AndroidApiLevelUtils.findAndComputeApiLevelForLibraryDefinition(
                      appView, appInfoWithClassHierarchy, holder, reference.asDexMember())
                  .getSecond();
        }
        maxApiReferenceLevel = maxApiReferenceLevel.max(referenceApiLevel);
      }
    }
  }

  @SuppressWarnings("HidingField")
  public ComputedApiLevel getMaxApiReferenceLevel() {
    return maxApiReferenceLevel;
  }
}
