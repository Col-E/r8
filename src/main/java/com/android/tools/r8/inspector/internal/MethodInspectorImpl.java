// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.inspector.internal;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.inspector.MethodInspector;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.ListUtils;
import java.util.Arrays;

public class MethodInspectorImpl implements MethodInspector {

  private final ClassInspectorImpl parent;
  private final DexEncodedMethod method;
  private MethodReference reference;

  public MethodInspectorImpl(ClassInspectorImpl parent, DexEncodedMethod method) {
    this.parent = parent;
    this.method = method;
  }

  @Override
  public MethodReference getMethodReference() {
    if (reference == null) {
      reference =
          Reference.method(
              parent.getClassReference(),
              method.method.name.toString(),
              ListUtils.map(
                  Arrays.asList(method.method.proto.parameters.values),
                  param -> Reference.typeFromDescriptor(param.toDescriptorString())),
              method.method.proto.returnType.isVoidType()
                  ? null
                  : Reference.typeFromDescriptor(
                      method.method.proto.returnType.toDescriptorString()));
    }
    return reference;
  }
}
