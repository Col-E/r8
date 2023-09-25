// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.getCompatibleKotlinInfo;
import static kotlinx.metadata.jvm.KotlinClassMetadata.Companion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.utils.Pair;
import java.util.function.Consumer;
import kotlin.Metadata;
import kotlinx.metadata.KmPackage;
import kotlinx.metadata.jvm.KotlinClassMetadata.MultiFileClassPart;

// Holds information about Metadata.MultiFileClassPartInfo
public class KotlinMultiFileClassPartInfo implements KotlinClassLevelInfo {

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
      AppView<?> appView,
      Consumer<DexEncodedMethod> keepByteCode) {
    KmPackage kmPackage = classPart.getKmPackage();
    return new KotlinMultiFileClassPartInfo(
        classPart.getFacadeClassName(),
        KotlinPackageInfo.create(kmPackage, clazz, appView, keepByteCode),
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
  public Pair<Metadata, Boolean> rewrite(DexClass clazz, AppView<?> appView) {
    KmPackage kmPackage = new KmPackage();
    boolean rewritten = packageInfo.rewrite(kmPackage, clazz, appView);
    return Pair.create(
        Companion.writeMultiFileClassPart(kmPackage, facadeClassName, getCompatibleKotlinInfo(), 0),
        rewritten);
  }

  @Override
  public String getPackageName() {
    return packageName;
  }

  public String getModuleName() {
    return packageInfo.getModuleName();
  }

  @Override
  public int[] getMetadataVersion() {
    return metadataVersion;
  }

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    packageInfo.trace(definitionSupplier);
  }

  public String getFacadeClassName() {
    return facadeClassName;
  }
}
