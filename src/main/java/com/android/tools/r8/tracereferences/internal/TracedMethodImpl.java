// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.tracereferences.internal;

import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.MethodAccessFlags;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.TracedMethod;

public class TracedMethodImpl extends TracedReferenceBase<MethodReference, MethodAccessFlags>
    implements TracedMethod {
  public TracedMethodImpl(DexMethod method, DefinitionContext referencedFrom) {
    this(method.asMethodReference(), referencedFrom, null);
  }

  public TracedMethodImpl(DexEncodedMethod method, DefinitionContext referencedFrom) {
    this(
        method.getReference().asMethodReference(),
        referencedFrom,
        new MethodAccessFlagsImpl(method.getAccessFlags()));
  }

  public TracedMethodImpl(
      MethodReference methodReference,
      DefinitionContext referencedFrom,
      MethodAccessFlags accessFlags) {
    super(methodReference, referencedFrom, accessFlags, accessFlags == null);
  }

  @Override
  public String toString() {
    return getReference().toString();
  }
}
