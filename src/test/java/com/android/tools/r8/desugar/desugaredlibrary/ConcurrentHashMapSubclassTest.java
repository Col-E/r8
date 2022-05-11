// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
//  for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.SPECIFICATIONS_WITH_CF2CF;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConcurrentHashMapSubclassTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines("1.0", "2.0", "10.0", "1.0", "2.0", "10.0", "1.0", "2.0", "10.0");

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withAllRuntimes()
            .withAllApiLevelsAlsoForCf()
            .withApiLevel(AndroidApiLevel.N)
            .build(),
        getJdk8Jdk11(),
        SPECIFICATIONS_WITH_CF2CF);
  }

  public ConcurrentHashMapSubclassTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void test() throws Exception {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(Executor.class)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @SuppressWarnings("unchecked")
  static class Executor {
    public static void main(String[] args) {
      directType();
      classType();
      itfType();
    }

    static void itfType() {
      Map map = new NullableConcurrentHashMap<Integer, Double>();
      map.put(1, 1.0);
      map.putIfAbsent(2, 2.0);
      map.putIfAbsent(2, 3.0);
      map.putAll(example());
      System.out.println(map.get(1));
      System.out.println(map.get(2));
      System.out.println(map.get(10));
    }

    static void classType() {
      ConcurrentHashMap map = new NullableConcurrentHashMap<Integer, Double>();
      map.put(1, 1.0);
      map.putIfAbsent(2, 2.0);
      map.putIfAbsent(2, 3.0);
      map.putAll(example());
      System.out.println(map.get(1));
      System.out.println(map.get(2));
      System.out.println(map.get(10));
    }

    static void directType() {
      NullableConcurrentHashMap map = new NullableConcurrentHashMap<Integer, Double>();
      map.put(1, 1.0);
      map.putIfAbsent(2, 2.0);
      map.putIfAbsent(2, 3.0);
      map.putAll(example());
      System.out.println(map.get(1));
      System.out.println(map.get(2));
      System.out.println(map.get(10));
    }

    static Map<Integer, Double> example() {
      IdentityHashMap<Integer, Double> example = new IdentityHashMap<>();
      example.put(10, 10.0);
      return example;
    }
  }

  static class NullableConcurrentHashMap<K, V> extends ConcurrentHashMap<K, V> {
    NullableConcurrentHashMap() {
      super();
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public V put(K key, V value) {
      if (key == null || value == null) {
        return null;
      }
      return super.put(key, value);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
      for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
        put(entry.getKey(), entry.getValue());
      }
    }
  }
}
