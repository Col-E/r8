// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.androidapi.AndroidApiClass;

/** This is a template for generating the AndroidApiDatabasePackage. */
public class AndroidApiDatabasePackageTemplate {

  public static AndroidApiClass buildClass(String className) {
    // Code added dynamically in AndroidApiDatabaseBuilderGenerator.
    placeHolder();
    return null;
  }

  private static void placeHolder() {}
}
