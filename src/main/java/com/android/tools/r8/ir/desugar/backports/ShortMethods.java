// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.synthetic.TemplateMethodCode;
import com.android.tools.r8.utils.InternalOptions;

public final class ShortMethods extends TemplateMethodCode {
  public ShortMethods(InternalOptions options, DexMethod method, String methodName) {
    super(options, method, methodName, method.proto.toDescriptorString());
  }

  public static int hashCode(short i) {
    return Short.valueOf(i).hashCode();
  }

  public static int compare(short a, short b) {
    return Short.valueOf(a).compareTo(Short.valueOf(b));
  }

  public static int toUnsignedInt(short value) {
    return value & 0xffff;
  }

  public static long toUnsignedLong(short value) {
    return value & 0xffffL;
  }
}
