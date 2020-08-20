// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ConcurrentHashMapKeySetTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public ConcurrentHashMapKeySetTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    TestRunResult<?> result =
        testForDesugaring(parameters)
            .addInnerClasses(ConcurrentHashMapKeySetTest.class)
            .run(parameters.getRuntime(), TestClass.class);
    if (parameters.isDexRuntime()
        && parameters.getRuntime().asDex().getMinApiLevel().isLessThan(AndroidApiLevel.Q)) {
      // TODO(b/123160897): Support desugaring of the Java 8 change to ConcurrentHashMap::keySet.
      result.assertFailureWithErrorThatThrows(NoSuchMethodError.class);
    } else {
      result.assertSuccessWithOutput(EXPECTED);
    }
    // TODO(b/123160897): Inspect that keySet has changed on API level < Q / JDK8.
  }

  static class TestClass {

    static ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();

    public static void main(String[] args) {
      map.put("Hello", "world");
      for (String key : map.keySet()) {
        System.out.println(key + ", " + map.get(key));
      }
    }
  }
}
