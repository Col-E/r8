// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.modifiers;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;

public class SyntheticAndBridgeModifiersTest extends TestBase {

  static final List<Class<?>> CLASSES = ImmutableList.of(
      SyntheticAndBridgeModifiersTestClass.class,
      SyntheticAndBridgeModifiersTestClass.Super.class,
      SyntheticAndBridgeModifiersTestClass.TestClass.class);

  private void runTest(String keepRules, Consumer<ClassSubject> classConsumer) throws Exception {
    testForProguard()
        .addProgramClasses(CLASSES)
        .addKeepRules(keepRules)
        .addDontObfuscate()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject clazz =
                  inspector.clazz(SyntheticAndBridgeModifiersTestClass.TestClass.class);
              assertThat(clazz, isPresent());
              classConsumer.accept(clazz);
            });

    testForR8(Backend.DEX)
        .addProgramClasses(CLASSES)
        .addKeepRules(keepRules)
        .addDontObfuscate()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject clazz =
                  inspector.clazz(SyntheticAndBridgeModifiersTestClass.TestClass.class);
              assertThat(clazz, isPresent());
              classConsumer.accept(clazz);
            });
  }

  private long methodsWithNameStartingWith(ClassSubject clazz, String prefix) {
    return clazz
        .allMethods()
        .stream()
        .filter(method -> method.getOriginalName().startsWith(prefix))
        .count();
  }

  @Test
  public void testBridgeNotKept() throws Exception {
    runTest(
        "-keep class **TestClass$TestClass { java.lang.String x(); }",
        clazz -> {
          assertThat(clazz.method("java.lang.Object", "x", ImmutableList.of()), not(isPresent()));
          assertThat(clazz.method("java.lang.String", "x", ImmutableList.of()), isPresent());
        });
  }

  @Test
  public void testBridgeKept() throws Exception {
    runTest(
        "-keep class **TestClass$TestClass { bridge ** x(); }",
        clazz ->
            assertThat(clazz.method("java.lang.Object", "x", ImmutableList.of()), isPresent()));
  }

  @Test
  public void testSyntheticFieldNotKept() throws Exception {
    runTest(
        "-keep class **TestClass$TestClass { java.lang.String x(); }",
        clazz -> assertEquals(0, clazz.allFields().size()));
  }

  @Test
  public void testSyntheticFieldKept() throws Exception {
    runTest(
        "-keep class **TestClass$TestClass { synthetic ** this*; }",
        clazz -> assertEquals(1, clazz.allFields().size()));
  }

  @Test
  public void testSyntheticAccessorNotKept() throws Exception {
    runTest(
        "-keep class **TestClass$TestClass { java.lang.String x(); }",
        clazz -> assertEquals(0, methodsWithNameStartingWith(clazz, "access")));
  }

  @Test
  public void testSyntheticAccessorKept() throws Exception {
    runTest(
        "-keep class **TestClass$TestClass { static synthetic *; }",
        clazz -> assertEquals(1, methodsWithNameStartingWith(clazz, "access")));
  }
}

class SyntheticAndBridgeModifiersTestClass {
  class Super {
    Object x() {
      return null;
    }
  }

  // Non static inner classes will have a synthetic field for the outer instance (javac
  // named this$X).
  class TestClass extends Super {
    // This will have a synthetic static accessor (javac named access$XXX).
    private void test() {

    }

    // javac will create a forwarding bridge with signature 'Object x()'.
    String x() {
      return null;
    }
  }

  void accessPrivate() {
    new TestClass().test();
  }
}