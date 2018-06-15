// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import kotlinx.metadata.KmPackageVisitor;
import kotlinx.metadata.jvm.KotlinClassMetadata;

public final class KotlinFile extends KotlinInfo<KotlinClassMetadata.FileFacade> {

  static KotlinFile fromKotlinClassMetadata(KotlinClassMetadata kotlinClassMetadata) {
    assert kotlinClassMetadata instanceof KotlinClassMetadata.FileFacade;
    KotlinClassMetadata.FileFacade fileFacade =
        (KotlinClassMetadata.FileFacade) kotlinClassMetadata;
    return new KotlinFile(fileFacade);
  }

  private KotlinFile(KotlinClassMetadata.FileFacade metadata) {
    super(metadata);
  }

  @Override
  void validateMetadata(KotlinClassMetadata.FileFacade metadata) {
    // To avoid lazy parsing/verifying metadata.
    metadata.accept(new FileFacadeMetadataVisitor());
  }

  private static class FileFacadeMetadataVisitor extends KmPackageVisitor {
  }

  @Override
  public Kind getKind() {
    return Kind.File;
  }

  @Override
  public boolean isFile() {
    return true;
  }

  @Override
  public KotlinFile asFile() {
    return this;
  }

}
