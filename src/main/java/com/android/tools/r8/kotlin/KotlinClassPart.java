// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DescriptorUtils;
import kotlinx.metadata.KmPackage;
import kotlinx.metadata.jvm.KotlinClassHeader;
import kotlinx.metadata.jvm.KotlinClassMetadata;

public final class KotlinClassPart extends KotlinInfo<KotlinClassMetadata.MultiFileClassPart> {

  KmPackage kmPackage;
  // TODO(b/151194869): is it better to maintain DexType?
  String facadeClassName;

  static KotlinClassPart fromKotlinClassMetadata(
      KotlinClassMetadata kotlinClassMetadata, DexClass clazz) {
    assert kotlinClassMetadata instanceof KotlinClassMetadata.MultiFileClassPart;
    KotlinClassMetadata.MultiFileClassPart multiFileClassPart =
        (KotlinClassMetadata.MultiFileClassPart) kotlinClassMetadata;
    return new KotlinClassPart(multiFileClassPart, clazz);
  }

  private KotlinClassPart(KotlinClassMetadata.MultiFileClassPart metadata, DexClass clazz) {
    super(metadata, clazz);
  }

  @Override
  void processMetadata(KotlinClassMetadata.MultiFileClassPart metadata) {
    kmPackage = metadata.toKmPackage();
    facadeClassName = metadata.getFacadeClassName();
  }

  @Override
  void rewrite(AppView<AppInfoWithLiveness> appView, SubtypingInfo subtypingInfo, NamingLens lens) {
    DexType facadeClassType = appView.dexItemFactory().createType(
        DescriptorUtils.getDescriptorFromClassBinaryName(facadeClassName));
    KotlinMetadataSynthesizer synthesizer = new KotlinMetadataSynthesizer(appView, lens, this);
    facadeClassName = synthesizer.toRenamedBinaryName(facadeClassType);
    if (!appView.options().enableKotlinMetadataRewritingForMembers) {
      return;
    }
    rewriteDeclarationContainer(synthesizer);
  }

  @Override
  KotlinClassHeader createHeader() {
    if (facadeClassName != null) {
      KotlinClassMetadata.MultiFileClassPart.Writer writer =
          new KotlinClassMetadata.MultiFileClassPart.Writer();
      kmPackage.accept(writer);
      return writer.write(facadeClassName).getHeader();
    } else {
      // It's no longer part of multi-file class.
      KotlinClassMetadata.FileFacade.Writer writer = new KotlinClassMetadata.FileFacade.Writer();
      kmPackage.accept(writer);
      return writer.write().getHeader();
    }
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

  @Override
  public String toString(String indent) {
    StringBuilder sb = new StringBuilder(indent);
    appendKmSection(
        indent,
        "Metadata.MultiFileClassPart",
        sb,
        newIndent -> {
          appendKeyValue(newIndent, "facadeClassName", sb, facadeClassName);
          appendKmPackage(newIndent, sb, kmPackage);
        });
    return sb.toString();
  }
}
