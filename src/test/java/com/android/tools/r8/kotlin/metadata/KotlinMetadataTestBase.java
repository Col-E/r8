// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.utils.DescriptorUtils;

abstract class KotlinMetadataTestBase extends KotlinTestBase {

  KotlinMetadataTestBase(KotlinTargetVersion targetVersion) {
    super(targetVersion);
  }

  static final String PKG_PREFIX =
      DescriptorUtils.getBinaryNameFromJavaType(
          KotlinMetadataTestBase.class.getPackage().getName());
}
