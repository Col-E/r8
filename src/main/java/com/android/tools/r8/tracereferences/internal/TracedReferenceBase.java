// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.tracereferences.internal;

import com.android.tools.r8.tracereferences.TraceReferencesConsumer.TracedReference;

abstract class TracedReferenceBase<T, F> implements TracedReference<T, F> {
  private final T reference;
  private final F accessFlags;
  private final boolean missingDefinition;

  TracedReferenceBase(T reference, F accessFlags, boolean missingDefinition) {
    assert accessFlags != null || missingDefinition;
    this.reference = reference;
    this.accessFlags = accessFlags;
    this.missingDefinition = missingDefinition;
  }

  @Override
  public T getReference() {
    return reference;
  }

  @Override
  public boolean isMissingDefinition() {
    return missingDefinition;
  }

  @Override
  public F getAccessFlags() {
    return accessFlags;
  }

  @Override
  public int hashCode() {
    // Equality is only based on the reference.
    return reference.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    // Equality is only based on the reference.
    if (!(other instanceof TracedReferenceBase)) {
      return false;
    }
    return reference.equals(((TracedReferenceBase<?, ?>) other).reference);
  }
}
