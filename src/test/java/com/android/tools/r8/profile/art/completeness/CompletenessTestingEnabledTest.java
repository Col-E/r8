// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.completeness;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.profile.art.ArtProfileOptions;
import com.android.tools.r8.utils.InternalOptions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CompletenessTestingEnabledTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public CompletenessTestingEnabledTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  /**
   * Verifies that -Dcom.android.tools.r8.artprofilerewritingcompletenesscheck=true when running
   * from test.py. If running this locally in IntelliJ make sure to set the system property in the
   * run configuration.
   */
  @Test
  public void test() {
    // Verify that completeness testing is enabled for testing.
    assertEquals("true", System.getProperty(ArtProfileOptions.COMPLETENESS_PROPERTY_KEY, "false"));
    assertTrue(new InternalOptions().getArtProfileOptions().isCompletenessCheckForTestingEnabled());

    // Verify that completeness testing is disabled by default.
    System.clearProperty(ArtProfileOptions.COMPLETENESS_PROPERTY_KEY);
    assertFalse(
        new InternalOptions().getArtProfileOptions().isCompletenessCheckForTestingEnabled());
    System.setProperty(ArtProfileOptions.COMPLETENESS_PROPERTY_KEY, "true");
  }
}
