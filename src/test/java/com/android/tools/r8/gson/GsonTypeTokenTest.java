// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.gson;

import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GsonTypeTokenTest extends GsonTestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("Hello, world!");

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .apply(b -> addRuntimeLibrary(b, parameters))
        .addInnerClasses(getClass())
        .apply(GsonTestBase::addGsonLibraryAndKeepRules)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testProguard() throws Exception {
    parameters.assumeProguardTestParameters();
    testForProguard(ProguardVersion.getLatest())
        .apply(b -> addRuntimeLibrary(b, parameters))
        .addInnerClasses(getClass())
        .apply(GsonTestBase::addGsonLibraryAndKeepRules)
        .addKeepMainRule(TestClass.class)
        .addDontNote("*")
        .addDontWarn(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class TestClass {
    public static void main(String[] args) {
      Object o =
          new GsonBuilder()
              .disableJdkUnsafe()
              .create()
              .fromJson("[\"Hello\",\"world\"]", new TypeToken<List<String>>() {}.getType());
      List<String> list = (List<String>) o;
      System.out.println(list.get(0) + ", " + list.get(1) + "!");
    }
  }
}
