// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.synthetic.TemplateMethodCode;
import com.android.tools.r8.utils.InternalOptions;

public final class FloatMethods extends TemplateMethodCode {
  public FloatMethods(InternalOptions options, DexMethod method, String methodName) {
    super(options, method, methodName, method.proto.toDescriptorString());
  }

  public static int hashCode(float d) {
    return Float.valueOf(d).hashCode();
  }

  public static float max(float a, float b) {
    return Math.max(a, b);
  }

  public static float min(float a, float b) {
    return Math.min(a, b);
  }

  public static float sum(float a, float b) {
    return a + b;
  }

  public static boolean isFinite(float d) {
    Float boxed = Float.valueOf(d);
    return !boxed.isInfinite() && !boxed.isNaN();
  }
}
