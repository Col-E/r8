// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.util.Properties;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PackageMinificationWithDontOptimizeTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addDontOptimize()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject classSubject = inspector.clazz(TestClass.class);
              assertThat(classSubject, isPresent());
              assertEquals("a.a", classSubject.getFinalName());
            });
  }

  public static class Main {

    public static void main(String[] args) {
      TestClass.getProperty("abc");
      TestClass.getProperty("def");
    }
  }

  public static class TestClass {

    private static final Properties properties = new Properties();

    public static String getProperty(String key) {
      return properties.getProperty(key);
    }
  }
}
