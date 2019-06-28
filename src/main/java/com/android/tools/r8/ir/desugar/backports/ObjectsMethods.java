// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.synthetic.TemplateMethodCode;
import com.android.tools.r8.utils.InternalOptions;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

public final class ObjectsMethods extends TemplateMethodCode {
  public ObjectsMethods(InternalOptions options, DexMethod method, String methodName) {
    super(options, method, methodName, method.proto.toDescriptorString());
  }

  public static <T> int compare(T a, T b, Comparator<? super T> c) {
    return a == b ? 0 : c.compare(a, b);
  }

  public static boolean deepEquals(Object a, Object b) {
    if (a == b) return true;
    if (a == null) return false;
    if (a instanceof boolean[]) {
      return b instanceof boolean[] && Arrays.equals((boolean[]) a, (boolean[]) b);
    }
    if (a instanceof byte[]) {
      return b instanceof byte[] && Arrays.equals((byte[]) a, (byte[]) b);
    }
    if (a instanceof char[]) {
      return b instanceof char[] && Arrays.equals((char[]) a, (char[]) b);
    }
    if (a instanceof double[]) {
      return b instanceof double[] && Arrays.equals((double[]) a, (double[]) b);
    }
    if (a instanceof float[]) {
      return b instanceof float[] && Arrays.equals((float[]) a, (float[]) b);
    }
    if (a instanceof int[]) {
      return b instanceof int[] && Arrays.equals((int[]) a, (int[]) b);
    }
    if (a instanceof long[]) {
      return b instanceof long[] && Arrays.equals((long[]) a, (long[]) b);
    }
    if (a instanceof short[]) {
      return b instanceof short[] && Arrays.equals((short[]) a, (short[]) b);
    }
    if (a instanceof Object[]) {
      return b instanceof Object[] && Arrays.deepEquals((Object[]) a, (Object[]) b);
    }
    return a.equals(b);
  }

  public static boolean equals(Object a, Object b) {
    return a == b || (a != null && a.equals(b));
  }

  public static int hash(Object[] o) {
    return Arrays.hashCode(o);
  }

  public static int hashCode(Object o) {
    return o == null ? 0 : o.hashCode();
  }

  public static boolean isNull(Object o) {
    return o == null;
  }

  public static boolean nonNull(Object o) {
    return o != null;
  }

  public static <T> T requireNonNullMessage(T obj, String message) {
    if (obj == null) {
      throw new NullPointerException(message);
    }
    return obj;
  }

  public static String toString(Object o) {
    return Objects.toString(o, "null");
  }

  public static String toStringDefault(Object o, String nullDefault) {
    return o == null ? nullDefault : o.toString();
  }
}
