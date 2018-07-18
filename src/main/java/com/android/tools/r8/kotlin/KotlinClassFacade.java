// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import kotlinx.metadata.jvm.KotlinClassMetadata;

public final class KotlinClassFacade extends KotlinInfo<KotlinClassMetadata.MultiFileClassFacade> {

  static KotlinClassFacade fromKotlinClassMetadata(KotlinClassMetadata kotlinClassMetadata) {
    assert kotlinClassMetadata instanceof KotlinClassMetadata.MultiFileClassFacade;
    KotlinClassMetadata.MultiFileClassFacade multiFileClassFacade =
        (KotlinClassMetadata.MultiFileClassFacade) kotlinClassMetadata;
    return new KotlinClassFacade(multiFileClassFacade);
  }

  private KotlinClassFacade(KotlinClassMetadata.MultiFileClassFacade metadata) {
    super(metadata);
  }

  @Override
  void processMetadata(KotlinClassMetadata.MultiFileClassFacade metadata) {
    // No worries about lazy parsing/verifying, since no API to explore metadata details.
  }

  @Override
  public Kind getKind() {
    return Kind.Facade;
  }

  @Override
  public boolean isClassFacade() {
    return true;
  }

  @Override
  public KotlinClassFacade asClassFacade() {
    return this;
  }

}
