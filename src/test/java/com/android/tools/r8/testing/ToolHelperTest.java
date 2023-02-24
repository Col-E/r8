// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.testing;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Path;
import org.junit.Test;

public class ToolHelperTest extends TestBase {

  private void checkExpectedAndroidJar(Path androidJarPath, AndroidApiLevel apiLevel) {
    assertEquals("android.jar", androidJarPath.getFileName().toString());
    assertEquals(
        "lib-v" + apiLevel.getLevel(),
        androidJarPath.getName(androidJarPath.getNameCount() - 2).toString());
  }

  @Test
  public void testGetFirstSupportedAndroidJar() {
    // Check some API levels for which the repo does not have android.jar.
    checkExpectedAndroidJar(
        ToolHelper.getFirstSupportedAndroidJar(AndroidApiLevel.B), AndroidApiLevel.I);
    checkExpectedAndroidJar(
        ToolHelper.getFirstSupportedAndroidJar(AndroidApiLevel.K_WATCH), AndroidApiLevel.L);
    // All android.jar's for API level L are present.
    for (AndroidApiLevel androidApiLevel : AndroidApiLevel.values()) {
      if (androidApiLevel.isGreaterThanOrEqualTo(AndroidApiLevel.L)
          && androidApiLevel.isLessThanOrEqualTo(AndroidApiLevel.LATEST)) {
        checkExpectedAndroidJar(
            ToolHelper.getFirstSupportedAndroidJar(androidApiLevel), androidApiLevel);
      }
    }
  }
}
