// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.proguard.rules;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NegatedClassMemberTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public NegatedClassMemberTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testProguard() {
    try {
      testForProguard()
          .addProgramClasses(
              NegatedClassMemberTestClassA.class,
              NegatedClassMemberTestClassB.class,
              NegatedClassMemberTestClassC.class)
          .addKeepRules(getKeepRule())
          .setMinApi(parameters.getRuntime())
          .compile();

      // For some reason, Proguard fails with "The output jar is empty". One likely explanation is
      // that Proguard only keeps classes that have a field y with type "!long", which is not a
      // valid type name, and therefore never matches anything.
      fail("Expected Proguard to fail with \"The output jar is empty\"");
    } catch (CompilationFailedException e) {
      assertThat(e.getMessage(), containsString("The output jar is empty"));
    }
  }

  @Test
  public void testR8() throws Exception {
    try {
      testForR8(Backend.DEX)
          .addProgramClasses(
              NegatedClassMemberTestClassA.class,
              NegatedClassMemberTestClassB.class,
              NegatedClassMemberTestClassC.class)
          .addKeepRules(getKeepRule())
          .compile();
      fail("Expected R8 to fail during parsing of the Proguard configuration file");
    } catch (CompilationFailedException e) {
      int expectedOffset = getKeepRule().indexOf("!");
      int expectedColumn = expectedOffset + 1;
      assertThat(
          e.getCause().getMessage(),
          allOf(
              containsString(
                  "Error: offset: "
                      + expectedOffset
                      + ", line: 1, column: "
                      + expectedColumn
                      + ", Unexpected character '!': "
                      + "The negation character can only be used to negate access flags"),
              containsString(
                  StringUtils.joinLines(
                      "-keepclasseswithmembers class ** { long x; !long y; }",
                      "                                           ^"))));
    }
  }

  private String getKeepRule() {
    return "-keepclasseswithmembers class ** { long x; !long y; }";
  }
}

class NegatedClassMemberTestClassA {

  long x = System.currentTimeMillis();
}

class NegatedClassMemberTestClassB {

  long x = System.currentTimeMillis();
  private long y = System.currentTimeMillis();
}

class NegatedClassMemberTestClassC {

  long x = System.currentTimeMillis();
  int y = (int) System.currentTimeMillis();
}
