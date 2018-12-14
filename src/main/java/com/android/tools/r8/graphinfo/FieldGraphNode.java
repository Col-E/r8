// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graphinfo;

import com.android.tools.r8.Keep;
import com.android.tools.r8.graph.DexField;

@Keep
public final class FieldGraphNode extends GraphNode {

  private final DexField field;

  public FieldGraphNode(DexField field) {
    assert field != null;
    this.field = field;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof FieldGraphNode && ((FieldGraphNode) o).field == field);
  }

  @Override
  public int hashCode() {
    return field.hashCode();
  }

  /**
   * Get the class descriptor for the field holder as defined by the JVM specification.
   *
   * <p>For the field {@code x.y.Z a.b.C.foo}, this would be {@code La/b/C;}.
   */
  public String getHolderDescriptor() {
    return field.clazz.toDescriptorString();
  }

  /**
   * Get the field descriptor as defined by the JVM specification.
   *
   * <p>For the field {@code x.y.Z a.b.C.foo}, this would be {@code Lx/y/Z;}.
   */
  public String getFieldDescriptor() {
    return field.type.toDescriptorString();
  }

  /**
   * Get the (unqualified) field name.
   *
   * <p>For the field {@code x.y.Z a.b.C.foo} this would be {@code foo}.
   */
  public String getFieldName() {
    return field.name.toString();
  }

  /**
   * Get a unique identity string determining this field node.
   *
   * <p>The identity string follows the CF encoding of a field reference: {@code
   * <holder-descriptor>.<field-name>:<field-descriptor>}, e.g., for {@code x.y.Z a.b.C.foo} this
   * would be {@code La/b/C;foo:Lx/y/Z;}.
   */
  @Override
  public String identity() {
    return getHolderDescriptor() + getFieldName() + "\":" + getFieldDescriptor();
  }
}
