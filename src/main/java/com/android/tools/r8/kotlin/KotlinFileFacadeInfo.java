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
      DexItemFactory factory,
      Reporter reporter,
      Consumer<DexEncodedMethod> keepByteCode) {
    return new KotlinFileFacadeInfo(
        KotlinPackageInfo.create(
            kmFileFacade.toKmPackage(), clazz, factory, reporter, keepByteCode),
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
  public KotlinClassHeader rewrite(DexClass clazz, AppView<?> appView, NamingLens namingLens) {
    KotlinClassMetadata.FileFacade.Writer writer = new KotlinClassMetadata.FileFacade.Writer();
    KmPackage kmPackage = new KmPackage();
    packageInfo.rewrite(kmPackage, clazz, appView, namingLens);
    kmPackage.accept(writer);
    return writer.write().getHeader();
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
