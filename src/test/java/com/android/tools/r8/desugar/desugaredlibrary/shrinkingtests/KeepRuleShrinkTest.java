// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.shrinkingtests;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepRuleShrinkTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  public KeepRuleShrinkTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testMapProblem() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    D8TestRunResult d8TestRunResult =
        testForD8()
            .addInnerClasses(KeepRuleShrinkTest.class)
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
            .compile()
            .addDesugaredCoreLibraryRunClassPath(
                this::buildDesugaredLibrary,
                parameters.getApiLevel(),
                keepRuleConsumer.get(),
                shrinkDesugaredLibrary)
            .run(parameters.getRuntime(), Executor.class)
            .assertSuccess();
    assertLines2By2Correct(d8TestRunResult.getStdOut());
  }

  static class Executor {

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
      Map<String, String>[] maps =
          new Map[] {
            new ConcurrentHashMap<String, String>(), new ConcurrentSkipListMap<String, String>()
          };
      for (Map<String, String> map : maps) {
        map.put("guineaPig", "anotherGuineaPig");
        System.out.println(map.size());
      }
      ConcurrentMap<String, String>[] concurrentMaps =
          new ConcurrentMap[] {
            new ConcurrentHashMap<String, String>(), new ConcurrentSkipListMap<String, String>()
          };
      for (ConcurrentMap<String, String> map : concurrentMaps) {
        map.put("guineaPig", "anotherGuineaPig");
        System.out.println(map.size());
      }
    }
  }
}
