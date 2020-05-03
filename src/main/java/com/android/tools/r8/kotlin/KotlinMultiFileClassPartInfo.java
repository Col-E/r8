// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
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
import kotlinx.metadata.jvm.KotlinClassMetadata.MultiFileClassPart;

// Holds information about Metadata.MultiFileClassPartInfo
public class KotlinMultiFileClassPartInfo implements KotlinClassLevelInfo {

  private final String facadeClassName;
  private final KotlinPackageInfo packageInfo;

  private KotlinMultiFileClassPartInfo(String facadeClassName, KotlinPackageInfo packageInfo) {
    this.facadeClassName = facadeClassName;
    this.packageInfo = packageInfo;
  }

  static KotlinMultiFileClassPartInfo create(
      MultiFileClassPart classPart, DexClass clazz, AppView<?> appView) {
    return new KotlinMultiFileClassPartInfo(
        classPart.getFacadeClassName(),
        KotlinPackageInfo.create(classPart.toKmPackage(), clazz, appView));
  }

  @Override
  public boolean isMultiFileClassPart() {
    return true;
  }

  @Override
  public KotlinMultiFileClassPartInfo asMultiFileClassPart() {
    return this;
  }

  @Override
  public KotlinClassHeader rewrite(
      DexClass clazz, AppView<AppInfoWithLiveness> appView, NamingLens namingLens) {
    KotlinClassMetadata.MultiFileClassPart.Writer writer =
        new KotlinClassMetadata.MultiFileClassPart.Writer();
    KmPackage kmPackage = new KmPackage();
    packageInfo.rewrite(kmPackage, clazz, appView, namingLens);
    kmPackage.accept(writer);
    return writer.write(facadeClassName).getHeader();
  }
}
