// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.shaking.classkinds.Annotation;
import com.android.tools.r8.shaking.classkinds.Class;
import com.android.tools.r8.shaking.classkinds.Enum;
import com.android.tools.r8.shaking.classkinds.Interface;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClassKindTest extends TestBase {

  private static List<java.lang.Class> CLASSES_TO_INCLUDE = ImmutableList.of(
      Annotation.class, Class.class, Enum.class, Interface.class);

  public ClassKindTest(String config, List<java.lang.Class<?>> classes) {
    this.config = config;
    this.classes = classes;
  }

  @Parameters(name = "{0}")
  public static Collection<Object[]> paramters() {
    return ImmutableList.copyOf(new Object[][]{
        {"-keep interface *", ImmutableList.of(Interface.class)},
        {"-keep class *", CLASSES_TO_INCLUDE},
        {"-keep enum *", ImmutableList.of(Enum.class)},
        {"-keep @interface *", ImmutableList.of(Annotation.class)},
        {"-keep !interface *", ImmutableList.of(Enum.class, Annotation.class, Class.class)},
        {"-keep !enum *", ImmutableList.of(Interface.class, Annotation.class, Class.class)},
        {"-keep !@interface *", ImmutableList.of(Interface.class, Enum.class, Class.class)},
        {"-keep !class *", ImmutableList.of()}
    });
  }

  public final String config;
  public final List<java.lang.Class<?>> classes;

  @Test
  public void run() throws Exception {
    AndroidApp app;
    try {
      app = compileWithR8(CLASSES_TO_INCLUDE, config);
    } catch (AssertionError e) {
      // Compilation will fail if we produce no classes.
      Assert.assertTrue(classes.isEmpty());
      return;
    }
    DexInspector inspector = new DexInspector(app);
    HashSet<java.lang.Class<?>> expected = Sets.newHashSet(classes);
    CLASSES_TO_INCLUDE.forEach(c -> {
      Assert.assertEquals(expected.contains(c), inspector.clazz(c).isPresent());
    });
  }
}
