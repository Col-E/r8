// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import kotlinx.metadata.KmPackageVisitor;
import kotlinx.metadata.jvm.KotlinClassMetadata;

public final class KotlinClassPart extends KotlinInfo<KotlinClassMetadata.MultiFileClassPart> {

  static KotlinClassPart fromKotlinClassMetdata(KotlinClassMetadata kotlinClassMetadata) {
    assert kotlinClassMetadata instanceof KotlinClassMetadata.MultiFileClassPart;
    KotlinClassMetadata.MultiFileClassPart multiFileClassPart =
        (KotlinClassMetadata.MultiFileClassPart) kotlinClassMetadata;
    return new KotlinClassPart(multiFileClassPart);
  }

  private KotlinClassPart(KotlinClassMetadata.MultiFileClassPart metadata) {
    super(metadata);
  }

  @Override
  void validateMetadata(KotlinClassMetadata.MultiFileClassPart metadata) {
    // To avoid lazy parsing/verifying metadata.
    metadata.accept(new MultiFileClassPartMetadataVisitor());
  }

  private static class MultiFileClassPartMetadataVisitor extends KmPackageVisitor {
  }

  @Override
  public Kind getKind() {
    return Kind.Part;
  }

  @Override
  public boolean isClassPart() {
    return true;
  }

  @Override
  public KotlinClassPart asClassPart() {
    return this;
  }

}
