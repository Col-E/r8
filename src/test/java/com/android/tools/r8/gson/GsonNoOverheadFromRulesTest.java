// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.gson;

import static org.junit.Assert.assertThrows;

import com.android.tools.r8.ProguardTestCompileResult;
import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GsonNoOverheadFromRulesTest extends GsonTestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    R8TestCompileResult withoutGson =
        testForR8(parameters.getBackend())
            .apply(b -> addRuntimeLibrary(b, parameters))
            .addInnerClasses(getClass())
            .addKeepMainRule(TestClass.class)
            .setMinApi(parameters)
            .compile();
    R8TestCompileResult withGson =
        testForR8(parameters.getBackend())
            .apply(b -> addRuntimeLibrary(b, parameters))
            .addInnerClasses(getClass())
            .apply(GsonTestBase::addGsonLibraryAndKeepRules)
            .addKeepMainRule(TestClass.class)
            .setMinApi(parameters)
            .compile();
    assertProgramsEqual(withoutGson.writeToZip(), withGson.writeToZip());
  }

  @Test
  public void testProguard() throws Exception {
    parameters.assumeProguardTestParameters();
    ProguardTestCompileResult withoutGson =
        testForProguard(ProguardVersion.getLatest())
            .apply(b -> addRuntimeLibrary(b, parameters))
            .addInnerClasses(getClass())
            .addKeepMainRule(TestClass.class)
            .addDontWarn(getClass())
            .setMinApi(parameters)
            .compile();
    ProguardTestCompileResult withGson =
        testForProguard(ProguardVersion.getLatest())
            .apply(b -> addRuntimeLibrary(b, parameters))
            .addInnerClasses(getClass())
            .apply(GsonTestBase::addGsonLibraryAndKeepRules)
            .addKeepMainRule(TestClass.class)
            .addDontWarn(getClass())
            .addDontObfuscate()
            .setMinApi(parameters)
            .compile();
    // ProGuard has overhead from the rules.
    assertThrows(
        AssertionError.class,
        () -> assertIdenticalInspectors(withoutGson.inspector(), withGson.inspector()));
  }

  static class TestClass {
    // No use of Gson.
    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
