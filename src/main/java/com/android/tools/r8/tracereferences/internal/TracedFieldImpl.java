// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.tracereferences.internal;

import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.FieldAccessFlags;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.TracedField;

public class TracedFieldImpl extends TracedReferenceBase<FieldReference, FieldAccessFlags>
    implements TracedField {
  public TracedFieldImpl(DexField field) {
    this(field.asFieldReference(), null);
  }

  public TracedFieldImpl(DexClassAndField field) {
    this(field.getFieldReference(), new FieldAccessFlagsImpl(field.getAccessFlags()));
  }

  public TracedFieldImpl(FieldReference fieldReference, FieldAccessFlags accessFlags) {
    super(fieldReference, accessFlags, accessFlags == null);
  }

  @Override
  public String toString() {
    return getReference().toString();
  }
}
