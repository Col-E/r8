// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.b132460884;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class TestMap<K, V> {
  private Map<K, V> map;

  private TestMap() {
    this.map = new HashMap<>();
  }

  public static <K, V> TestMap<K, V> of(K k, V v) {
    TestMap<K, V> m = new TestMap<>();
    m.map.put(k, v);
    return m;
  }

  public Set<Map.Entry<K, V>> entrySet() {
    class LocalEntrySet extends HashSet<Map.Entry<K, V>> {
      @Override
      public Iterator<Map.Entry<K, V>> iterator() {
        return map.entrySet().iterator();
      }
    }
    return map.isEmpty() ? new HashSet<>() : new LocalEntrySet();
  }
}

class TestMain {
  public static void main(String... args) {
    TestMap<String, Integer> map = TestMap.of("R", 8);
    Iterator<Map.Entry<String, Integer>> it = map.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, Integer> entry = it.next();
      System.out.println(entry.getKey() + " -> " + entry.getValue());
    }
  }
}

@RunWith(Parameterized.class)
public class LocalClassRenamingTest extends TestBase {
  private static final String EXPECTED_OUTPUT = StringUtils.lines("R -> 8");

  private final TestParameters parameters;
  private final boolean enableMinification;

  @Parameterized.Parameters(name = "{0} minification: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevels().build(),
        BooleanUtils.values());
  }

  public LocalClassRenamingTest(TestParameters parameters, boolean enableMinification) {
    this.parameters = parameters;
    this.enableMinification = enableMinification;
  }

  @Test
  public void b132460884() throws Exception {
    testForR8Compat(parameters.getBackend())
        .addProgramClassesAndInnerClasses(TestMap.class)
        .addProgramClasses(TestMain.class)
        .addKeepMainRule(TestMain.class)
        .addKeepAttributes("Signature", "InnerClasses")
        .noTreeShaking()
        .minification(enableMinification)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject local = inspector.clazz(TestMap.class.getName() + "$1LocalEntrySet");
              assertThat(local, isPresent());
              assertEquals(enableMinification, local.isRenamed());
            })
        .run(parameters.getRuntime(), TestMain.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }
}