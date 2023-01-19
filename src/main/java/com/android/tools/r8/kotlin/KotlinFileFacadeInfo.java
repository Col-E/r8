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
import kotlinx.metadata.jvm.KotlinClassMetadata.FileFacade;

// Holds information about Metadata.FileFacade
public class KotlinFileFacadeInfo implements KotlinClassLevelInfo {

  private final KotlinPackageInfo packageInfo;
  private final String packageName;
  private final int[] metadataVersion;

  private KotlinFileFacadeInfo(
      KotlinPackageInfo packageInfo, String packageName, int[] metadataVersion) {
    this.packageInfo = packageInfo;
    this.packageName = packageName;
    this.metadataVersion = metadataVersion;
  }

  public static KotlinFileFacadeInfo create(
      FileFacade kmFileFacade,
      String packageName,
      int[] metadataVersion,
      DexClass clazz,
      AppView<?> appView,
      Consumer<DexEncodedMethod> keepByteCode) {
    KmPackage kmPackage = kmFileFacade.toKmPackage();
    KotlinJvmSignatureExtensionInformation extensionInformation =
        KotlinJvmSignatureExtensionInformation.readInformationFromMessage(
            kmFileFacade, appView.options());
    return new KotlinFileFacadeInfo(
        KotlinPackageInfo.create(kmPackage, clazz, appView, keepByteCode, extensionInformation),
        packageName,
        metadataVersion);
  }

  @Override
  public boolean isFileFacade() {
    return true;
  }

  @Override
  public KotlinFileFacadeInfo asFileFacade() {
    return this;
  }

  @Override
  public Pair<Metadata, Boolean> rewrite(DexClass clazz, AppView<?> appView) {
    KmPackage kmPackage = new KmPackage();
    boolean rewritten = packageInfo.rewrite(kmPackage, clazz, appView);
    return Pair.create(
        Companion.writeFileFacade(kmPackage, getCompatibleKotlinInfo(), 0).getAnnotationData(),
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
}
