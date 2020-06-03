// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.testing;

/**
 * Stub class to simulate having a Build.VERSION property in headless tests.
 *
 * <p>Use test builder addAndroidBuildVersion() methods when used in tests.
 */
public class AndroidBuildVersion {
  public static final String PROPERTY = "com.android.tools.r8.testing.AndroidBuildVersion.VERSION";
  public static int VERSION = Integer.parseInt(System.getProperty(PROPERTY));
}
