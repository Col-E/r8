// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.KeepInfo.Joiner;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;

/**
 * Workaround for situations where an interface in the framework has been changed into a class. When
 * compiling against a recent android.jar, virtual invokes to such classes may resolve to interface
 * methods on older API levels, which can lead to verification errors (see b/206891715).
 *
 * <p>To mitigate this issue, we pin methods that have virtual invokes to such classes. This ensures
 * that we don't move these virtual invokes elsewhere (e.g., due to inlining or class merging),
 * which would otherwise lead to verification errors in other classes.
 */
// TODO(b/206891715): This only mitigates the issue. The user may still need to manually outline
//  virtual invokes to classes that was once an interface. To avoid this in general (including D8)
//  the compiler should outline the problematic invokes.
public class InvokeVirtualToInterfaceVerifyErrorWorkaround implements EnqueuerInvokeAnalysis {

  private final DexType androidHardwareCamera2CameraDeviceType;
  private final Enqueuer enqueuer;
  private final InternalOptions options;

  public InvokeVirtualToInterfaceVerifyErrorWorkaround(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    this.androidHardwareCamera2CameraDeviceType =
        appView.dexItemFactory().createType("Landroid/hardware/camera2/CameraDevice;");
    this.enqueuer = enqueuer;
    this.options = appView.options();
  }

  public static void register(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    if (!isNoop(appView)) {
      enqueuer.registerInvokeAnalysis(
          new InvokeVirtualToInterfaceVerifyErrorWorkaround(appView, enqueuer));
    }
  }

  private static boolean isNoop(AppView<? extends AppInfoWithClassHierarchy> appView) {
    return appView.options().getMinApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.L);
  }

  @Override
  public void traceInvokeVirtual(DexMethod invokedMethod, ProgramMethod context) {
    if (isInterfaceInSomeApiLevel(invokedMethod.getHolderType())) {
      enqueuer.getKeepInfo().joinMethod(context, Joiner::disallowOptimization);
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean isInterfaceInSomeApiLevel(DexType type) {
    // CameraDevice was added as a class in API 21 (L), but was defined as an interface in the
    // framework before then.
    return type == androidHardwareCamera2CameraDeviceType
        && (options.isGeneratingClassFiles()
            || options.getMinApiLevel().isLessThan(AndroidApiLevel.L));
  }

  @Override
  public void traceInvokeDirect(DexMethod invokedMethod, ProgramMethod context) {
    // Intentionally empty.
  }

  @Override
  public void traceInvokeInterface(DexMethod invokedMethod, ProgramMethod context) {
    // Intentionally empty.
  }

  @Override
  public void traceInvokeStatic(DexMethod invokedMethod, ProgramMethod context) {
    // Intentionally empty.
  }

  @Override
  public void traceInvokeSuper(DexMethod invokedMethod, ProgramMethod context) {
    // Intentionally empty.
  }
}
