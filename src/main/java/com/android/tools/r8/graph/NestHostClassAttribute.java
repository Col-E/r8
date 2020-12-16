// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import org.objectweb.asm.ClassWriter;

public class NestHostClassAttribute implements StructuralItem<NestHostClassAttribute> {

  private final DexType nestHost;

  private static void specify(StructuralSpecification<NestHostClassAttribute, ?> spec) {
    spec.withItem(a -> a.nestHost);
  }

  public NestHostClassAttribute(DexType nestHost) {
    this.nestHost = nestHost;
  }

  public DexType getNestHost() {
    return nestHost;
  }

  public static NestHostClassAttribute none() {
    return null;
  }

  public void write(ClassWriter writer, NamingLens lens) {
    assert nestHost != null;
    writer.visitNestHost(lens.lookupInternalName(nestHost));
  }

  @Override
  public NestHostClassAttribute self() {
    return this;
  }

  @Override
  public StructuralMapping<NestHostClassAttribute> getStructuralMapping() {
    return NestHostClassAttribute::specify;
  }
}
