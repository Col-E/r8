// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.shrinkingtests;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
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
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public KeepRuleShrinkTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testMapProblem() throws Exception {
    String stdOut =
        testForDesugaredLibrary(
                parameters, libraryDesugaringSpecification, compilationSpecification)
            .addInnerClasses(KeepRuleShrinkTest.class)
            .addKeepMainRule(Executor.class)
            .run(parameters.getRuntime(), Executor.class)
            .assertSuccess()
            .getStdOut();
    assertLines2By2Correct(stdOut);
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
