// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataSynthesizer.toRenamedKmFunctionAsExtension;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kotlinx.metadata.KmFunction;
import kotlinx.metadata.KmPackage;
import kotlinx.metadata.jvm.KotlinClassHeader;
import kotlinx.metadata.jvm.KotlinClassMetadata;

public final class KotlinClassPart extends KotlinInfo<KotlinClassMetadata.MultiFileClassPart> {

  private KmPackage kmPackage;

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
  void processMetadata() {
    assert !isProcessed;
    isProcessed = true;
    kmPackage = metadata.toKmPackage();
  }

  @Override
  void rewrite(AppView<AppInfoWithLiveness> appView, NamingLens lens) {
    List<KmFunction> functions = kmPackage.getFunctions();
    List<KmFunction> originalExtensions =
        functions.stream()
            .filter(KotlinMetadataSynthesizer::isExtension)
            .collect(Collectors.toList());
    functions.clear();

    for (Map.Entry<DexEncodedMethod, KmFunction> entry :
        clazz.kotlinExtensions(originalExtensions, appView).entrySet()) {
      KmFunction extension =
          toRenamedKmFunctionAsExtension(entry.getKey(), entry.getValue(), appView, lens);
      if (extension != null) {
        functions.add(extension);
      }
    }
  }

  @Override
  KotlinClassHeader createHeader() {
    KotlinClassMetadata.MultiFileClassPart.Writer writer =
        new KotlinClassMetadata.MultiFileClassPart.Writer();
    kmPackage.accept(writer);
    return writer.write(metadata.getFacadeClassName()).getHeader();
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
