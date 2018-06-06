// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import kotlinx.metadata.KmClassVisitor;
import kotlinx.metadata.jvm.KotlinClassMetadata;

public class KotlinClass extends KotlinInfo<KotlinClassMetadata.Class> {

  static KotlinClass fromKotlinClassMetadata(KotlinClassMetadata kotlinClassMetadata) {
    assert kotlinClassMetadata instanceof KotlinClassMetadata.Class;
    KotlinClassMetadata.Class kClass = (KotlinClassMetadata.Class) kotlinClassMetadata;
    return new KotlinClass(kClass);
  }

  private KotlinClass(KotlinClassMetadata.Class metadata) {
    super(metadata);
  }

  @Override
  void validateMetadata(KotlinClassMetadata.Class metadata) {
    ClassMetadataVisitor visitor = new ClassMetadataVisitor();
    // To avoid lazy parsing/verifying metadata.
    metadata.accept(visitor);
  }

  private static class ClassMetadataVisitor extends KmClassVisitor {
  }

  @Override
  public Kind getKind() {
    return Kind.Class;
  }

  @Override
  public boolean isClass() {
    return true;
  }

  @Override
  public KotlinClass asClass() {
    return this;
  }

}
