// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.synthetic.TemplateMethodCode;
import com.android.tools.r8.utils.InternalOptions;
import java.util.Iterator;

public final class StringMethods extends TemplateMethodCode {
  public StringMethods(InternalOptions options, DexMethod method, String methodName) {
    super(options, method, methodName, method.proto.toDescriptorString());
  }

  public static String joinArray(CharSequence delimiter, CharSequence... elements) {
    if (delimiter == null) throw new NullPointerException("delimiter");
    StringBuilder builder = new StringBuilder();
    if (elements.length > 0) {
      builder.append(elements[0]);
      for (int i = 1; i < elements.length; i++) {
        builder.append(delimiter);
        builder.append(elements[i]);
      }
    }
    return builder.toString();
  }

  public static String joinIterable(CharSequence delimiter,
      Iterable<? extends CharSequence> elements) {
    if (delimiter == null) throw new NullPointerException("delimiter");
    StringBuilder builder = new StringBuilder();
    Iterator<? extends CharSequence> iterator = elements.iterator();
    if (iterator.hasNext()) {
      builder.append(iterator.next());
      while (iterator.hasNext()) {
        builder.append(delimiter);
        builder.append(iterator.next());
      }
    }
    return builder.toString();
  }
}
