// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import kotlinx.metadata.KmPackage;
import kotlinx.metadata.jvm.KotlinClassHeader;
import kotlinx.metadata.jvm.KotlinClassMetadata;

public final class KotlinFile extends KotlinInfo<KotlinClassMetadata.FileFacade> {

  KmPackage kmPackage;

  static KotlinFile fromKotlinClassMetadata(
      KotlinClassMetadata kotlinClassMetadata, DexClass clazz) {
    assert kotlinClassMetadata instanceof KotlinClassMetadata.FileFacade;
    KotlinClassMetadata.FileFacade fileFacade =
        (KotlinClassMetadata.FileFacade) kotlinClassMetadata;
    return new KotlinFile(fileFacade, clazz);
  }

  private KotlinFile(KotlinClassMetadata.FileFacade metadata, DexClass clazz) {
    super(metadata, clazz);
  }

  @Override
  void processMetadata() {
    assert !isProcessed;
    isProcessed = true;
    kmPackage = metadata.toKmPackage();
  }

  @Override
  void rewrite(AppView<AppInfoWithLiveness> appView, NamingLens lens) {
    if (!appView.options().enableKotlinMetadataRewritingForMembers) {
      return;
    }
    rewriteDeclarationContainer(kmPackage, appView, lens);
  }

  @Override
  KotlinClassHeader createHeader() {
    KotlinClassMetadata.FileFacade.Writer writer = new KotlinClassMetadata.FileFacade.Writer();
    kmPackage.accept(writer);
    return writer.write().getHeader();
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
