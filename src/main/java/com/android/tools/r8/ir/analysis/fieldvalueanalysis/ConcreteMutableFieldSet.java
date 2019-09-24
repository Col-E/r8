// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldvalueanalysis;

import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.Sets;
import java.util.Set;

public class ConcreteMutableFieldSet extends AbstractFieldSet {

  private final Set<DexEncodedField> fields;

  public ConcreteMutableFieldSet() {
    fields = Sets.newIdentityHashSet();
  }

  public ConcreteMutableFieldSet(DexEncodedField field) {
    fields = SetUtils.newIdentityHashSet(field);
  }

  public void add(DexEncodedField field) {
    fields.add(field);
  }

  public void addAll(ConcreteMutableFieldSet other) {
    fields.addAll(other.fields);
  }

  public int size() {
    return fields.size();
  }

  @Override
  public boolean isConcreteFieldSet() {
    return true;
  }

  @Override
  public ConcreteMutableFieldSet asConcreteFieldSet() {
    return this;
  }

  @Override
  public boolean contains(DexEncodedField field) {
    return fields.contains(field);
  }
}
