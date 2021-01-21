// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
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

public class NestMemberClassAttribute implements StructuralItem<NestMemberClassAttribute> {

  private final DexType nestMember;

  private static void specify(StructuralSpecification<NestMemberClassAttribute, ?> spec) {
    spec.withItem(a -> a.nestMember);
  }

  public NestMemberClassAttribute(DexType nestMember) {
    this.nestMember = nestMember;
  }

  public static List<NestMemberClassAttribute> emptyList() {
    return Collections.emptyList();
  }

  public DexType getNestMember() {
    return nestMember;
  }

  public void write(ClassWriter writer, NamingLens lens) {
    assert nestMember != null;
    writer.visitNestMember(lens.lookupInternalName(nestMember));
  }

  @Override
  public NestMemberClassAttribute self() {
    return this;
  }

  @Override
  public StructuralMapping<NestMemberClassAttribute> getStructuralMapping() {
    return NestMemberClassAttribute::specify;
  }
}
