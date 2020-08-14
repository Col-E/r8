// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.Reporter;
import java.util.function.Consumer;
import kotlinx.metadata.KmPackage;
import kotlinx.metadata.jvm.KotlinClassHeader;
import kotlinx.metadata.jvm.KotlinClassMetadata;
import kotlinx.metadata.jvm.KotlinClassMetadata.MultiFileClassPart;

// Holds information about Metadata.MultiFileClassPartInfo
public class KotlinMultiFileClassPartInfo implements KotlinClassLevelInfo {

  // TODO(b/157630779): Maybe model facadeClassName.
  private final String facadeClassName;
  private final KotlinPackageInfo packageInfo;
  private final String packageName;
  private final int[] metadataVersion;

  private KotlinMultiFileClassPartInfo(
      String facadeClassName,
      KotlinPackageInfo packageInfo,
      String packageName,
      int[] metadataVersion) {
    this.facadeClassName = facadeClassName;
    this.packageInfo = packageInfo;
    this.packageName = packageName;
    this.metadataVersion = metadataVersion;
  }

  static KotlinMultiFileClassPartInfo create(
      MultiFileClassPart classPart,
      String packageName,
      int[] metadataVersion,
      DexClass clazz,
      DexItemFactory factory,
      Reporter reporter,
      Consumer<DexEncodedMethod> keepByteCode) {
    return new KotlinMultiFileClassPartInfo(
        classPart.getFacadeClassName(),
        KotlinPackageInfo.create(classPart.toKmPackage(), clazz, factory, reporter, keepByteCode),
        packageName,
        metadataVersion);
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
  public KotlinClassHeader rewrite(DexClass clazz, AppView<?> appView, NamingLens namingLens) {
    KotlinClassMetadata.MultiFileClassPart.Writer writer =
        new KotlinClassMetadata.MultiFileClassPart.Writer();
    KmPackage kmPackage = new KmPackage();
    packageInfo.rewrite(kmPackage, clazz, appView, namingLens);
    kmPackage.accept(writer);
    return writer.write(facadeClassName).getHeader();
  }

  @Override
  public String getPackageName() {
    return packageName;
  }

  @Override
  public int[] getMetadataVersion() {
    return metadataVersion;
  }

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    packageInfo.trace(definitionSupplier);
  }
}
