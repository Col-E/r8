// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.util.Collections;
import java.util.List;
import org.objectweb.asm.ClassWriter;

public class PermittedSubclassAttribute implements StructuralItem<PermittedSubclassAttribute> {

  private final DexType permittedSubclass;

  private static void specify(StructuralSpecification<PermittedSubclassAttribute, ?> spec) {
    spec.withItem(a -> a.permittedSubclass);
  }

  public PermittedSubclassAttribute(DexType nestMember) {
    this.permittedSubclass = nestMember;
  }

  public static List<PermittedSubclassAttribute> emptyList() {
    return Collections.emptyList();
  }

  public DexType getPermittedSubclass() {
    return permittedSubclass;
  }

  public void write(ClassWriter writer, NamingLens lens) {
    assert permittedSubclass != null;
    writer.visitPermittedSubclass(lens.lookupInternalName(permittedSubclass));
  }

  @Override
  public PermittedSubclassAttribute self() {
    return this;
  }

  @Override
  public StructuralMapping<PermittedSubclassAttribute> getStructuralMapping() {
    return PermittedSubclassAttribute::specify;
  }
}
