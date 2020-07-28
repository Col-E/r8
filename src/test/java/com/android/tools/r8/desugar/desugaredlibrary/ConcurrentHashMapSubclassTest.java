// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
//  for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
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
  private final boolean shrinkDesugaredLibrary;

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  public ConcurrentHashMapSubclassTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testCustomCollectionD8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .addInnerClasses(ConcurrentHashMapSubclassTest.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testCustomCollectionR8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(Backend.DEX)
        .addInnerClasses(ConcurrentHashMapSubclassTest.class)
        .setMinApi(parameters.getApiLevel())
        .addKeepClassAndMembersRules(Executor.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
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
