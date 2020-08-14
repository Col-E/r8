// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import kotlinx.metadata.jvm.KotlinClassHeader;

public interface KotlinClassLevelInfo extends EnqueuerMetadataTraceable {

  default boolean isNoKotlinInformation() {
    return false;
  }

  default boolean isClass() {
    return false;
  }

  default KotlinClassInfo asClass() {
    return null;
  }

  default boolean isFileFacade() {
    return false;
  }

  default KotlinFileFacadeInfo asFileFacade() {
    return null;
  }

  default boolean isMultiFileFacade() {
    return false;
  }

  default KotlinMultiFileClassFacadeInfo asMultiFileFacade() {
    return null;
  }

  default boolean isMultiFileClassPart() {
    return false;
  }

  default KotlinMultiFileClassPartInfo asMultiFileClassPart() {
    return null;
  }

  default boolean isSyntheticClass() {
    return false;
  }

  default KotlinSyntheticClassInfo asSyntheticClass() {
    return null;
  }

  KotlinClassHeader rewrite(DexClass clazz, AppView<?> appView, NamingLens namingLens);

  String getPackageName();

  int[] getMetadataVersion();
}
