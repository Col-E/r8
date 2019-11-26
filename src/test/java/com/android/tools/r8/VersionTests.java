// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.Version.LABEL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VersionTests extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private final TestParameters parameters;

  public VersionTests(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testSemVerInfo() {
    int majorVersion = Version.getMajorVersion();
    int minorVersion = Version.getMinorVersion();
    int patchVersion = Version.getPatchVersion();
    String preReleaseString = Version.getPreReleaseString();
    if (LABEL.equals("master")) {
      assertEquals(-1, majorVersion);
      assertEquals(-1, minorVersion);
      assertEquals(-1, patchVersion);
      assertNull(preReleaseString);
      assertTrue(Version.getVersionString().startsWith("master"));
    } else {
      assertTrue(majorVersion > 0);
      assertTrue(minorVersion >= 0);
      assertTrue(patchVersion >= 0);
      assertNotNull(preReleaseString);
      assertTrue(
          Version.getVersionString()
              .startsWith(
                  ""
                      + majorVersion
                      + "."
                      + minorVersion
                      + "."
                      + patchVersion
                      + (preReleaseString.isEmpty() ? "" : "-" + preReleaseString)));
    }
  }

  @Test
  public void testDevelopmentPredicate() {
    assertEquals(LABEL.equals("master") || LABEL.contains("-dev"), Version.isDevelopmentVersion());
  }
}
