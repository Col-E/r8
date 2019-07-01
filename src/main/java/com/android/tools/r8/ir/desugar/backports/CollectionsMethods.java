// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.synthetic.TemplateMethodCode;
import com.android.tools.r8.utils.InternalOptions;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.ListIterator;

public final class CollectionsMethods extends TemplateMethodCode {
  public CollectionsMethods(InternalOptions options, DexMethod method, String methodName) {
    super(options, method, methodName, method.proto.toDescriptorString());
  }

  public static <T> Enumeration<T> emptyEnumeration() {
    return Collections.enumeration(Collections.emptyList());
  }

  public static <T> Iterator<T> emptyIterator() {
    return Collections.<T>emptyList().iterator();
  }

  public static <T> ListIterator<T> emptyListIterator() {
    return Collections.<T>emptyList().listIterator();
  }
}
