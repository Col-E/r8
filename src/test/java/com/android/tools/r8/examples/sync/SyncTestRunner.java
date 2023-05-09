// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.sync;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.examples.ExamplesTestBase;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SyncTestRunner extends ExamplesTestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().enableApiLevelsForCf().build();
  }

  public SyncTestRunner(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<?> getMainClass() {
    return Sync.class;
  }

  @Override
  public List<Class<?>> getTestClasses() throws Exception {
    return ImmutableList.of(
        getMainClass(),
        Sync.Consumer.class,
        Class.forName(getMainClass().getTypeName() + "$1"),
        Class.forName(getMainClass().getTypeName() + "$2"),
        Class.forName(getMainClass().getTypeName() + "$3"),
        Class.forName(getMainClass().getTypeName() + "$4"),
        Class.forName(getMainClass().getTypeName() + "$5"));
  }

  @Override
  public String getExpected() {
    List<String> lines = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      lines.add("static");
      lines.add("end");
    }
    for (int i = 0; i < 10; i++) {
      lines.add("instance");
      lines.add("end");
    }
    for (int i = 0; i < 20; i++) {
      lines.add("manual");
    }
    for (int i = 0; i < 10; i++) {
      lines.add("trycatch");
      lines.add("end");
    }
    lines.add("caught throw");
    lines.add("caught throw");
    return StringUtils.lines(lines);
  }

  @Test
  public void testDesugaring() throws Exception {
    runTestDesugaring();
  }

  @Test
  public void testR8() throws Exception {
    runTestR8();
  }

  @Test
  public void testDebug() throws Exception {
    // TODO(b/79671093): DEX has different line number info during stepping.
    Assume.assumeTrue(parameters.isCfRuntime());
    runTestDebugComparator();
  }
}
