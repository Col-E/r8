// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.synthetic.TemplateMethodCode;
import com.android.tools.r8.utils.InternalOptions;

public final class ByteMethods extends TemplateMethodCode {
  public ByteMethods(InternalOptions options, DexMethod method, String methodName) {
    super(options, method, methodName, method.proto.toDescriptorString());
  }

  public static int hashCode(byte i) {
    return Byte.valueOf(i).hashCode();
  }

  public static int compare(byte a, byte b) {
    return Byte.valueOf(a).compareTo(Byte.valueOf(b));
  }

  public static int toUnsignedInt(byte value) {
    return value & 0xff;
  }

  public static long toUnsignedLong(byte value) {
    return value & 0xffL;
  }
}
