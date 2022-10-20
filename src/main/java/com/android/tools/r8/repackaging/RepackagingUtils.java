// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackaging;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.shaking.ProguardClassFilter;
import com.android.tools.r8.utils.InternalOptions;

public class RepackagingUtils {

  public static boolean isPackageNameKept(DexProgramClass clazz, InternalOptions options) {
    String packageDescriptor = clazz.getType().getPackageDescriptor();
    if (packageDescriptor.isEmpty()) {
      return true;
    }
    ProguardClassFilter keepPackageNamesPatterns =
        options.getProguardConfiguration().getKeepPackageNamesPatterns();
    if (keepPackageNamesPatterns.isEmpty()) {
      return false;
    }
    if (keepPackageNamesPatterns.matches(
        options.dexItemFactory().createType("L" + packageDescriptor + ";"))) {
      return true;
    }
    return !options.isForceProguardCompatibilityEnabled()
        && keepPackageNamesPatterns.matches(clazz.getType());
  }
}
