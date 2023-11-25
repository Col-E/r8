// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code.frame;

import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.naming.NamingLens;
import java.util.Objects;

public class UninitializedNew extends UninitializedFrameType {

  private final CfLabel label;
  private final DexType type;

  public UninitializedNew(CfLabel label, DexType type) {
    assert type == null || type.isClassType();
    this.label = label;
    this.type = type;
  }

  @Override
  public DexType getObjectType(DexItemFactory dexItemFactory, DexType context) {
    return type;
  }

  @Override
  public Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens) {
    return label.getLabel();
  }

  @Override
  public CfLabel getUninitializedLabel() {
    return label;
  }

  @Override
  public DexType getUninitializedNewType() {
    return type;
  }

  @Override
  public boolean isUninitializedNew() {
    return true;
  }

  @Override
  public UninitializedNew asUninitializedNew() {
    return this;
  }

  @Override
  public SingleFrameType join(
      AppView<? extends AppInfoWithClassHierarchy> appView, SingleFrameType frameType) {
    return equals(frameType) ? this : FrameType.oneWord();
  }

  @Override
  @SuppressWarnings({"EqualsGetClass", "ReferenceEquality"})
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    UninitializedNew uninitializedNew = (UninitializedNew) o;
    return label == uninitializedNew.label && type == uninitializedNew.type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(label, type);
  }

  @Override
  public String toString() {
    return "uninitialized new";
  }
}
