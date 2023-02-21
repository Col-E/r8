// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal.interfaces;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CollisionWithDefaultMethodsTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines("Event1", "Event2", "Event1!", "Event2!", "Event1#", "Event2#");

  @Test
  public void testDesugaring() throws Exception {
    testForDesugaring(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .addKeepRules("-keep class ** { *; }")
        .addHorizontallyMergedClassesInspector(
            inspector -> {
              if (parameters.isCfRuntime()) {
                inspector.assertNoClassesMerged();
              } else if (parameters.isDexRuntime()
                  && parameters.canUseDefaultAndStaticInterfaceMethods()) {
                assertEquals(2, inspector.getMergeGroups().size());
              }
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class Event {}

  static class Event1 extends Event {
    @Override
    public String toString() {
      return "Event1";
    }
  }

  static class Event2 extends Event {
    @Override
    public String toString() {
      return "Event2";
    }
  }

  interface GenericListener<T extends Event> {
    void onEvent(T event);
  }

  interface Listener1 extends GenericListener<Event1> {
    void onEvent(Event1 event);
  }

  interface Listener2 extends GenericListener<Event2> {
    void onEvent(Event2 event);
  }

  static class TestClass {

    static <T extends Event> void invokeOnGenericListenerInterface(
        GenericListener<T> listener, Class<T> eventClass) throws Exception {
      listener.onEvent(eventClass.getDeclaredConstructor().newInstance());
    }

    static void eventOnListener1(Listener1 listener) throws Exception {
      invokeOnGenericListenerInterface(listener, Event1.class);
    }

    static void eventOnListener2(Listener2 listener) throws Exception {
      invokeOnGenericListenerInterface(listener, Event2.class);
    }

    public static void main(String[] args) throws Exception {
      eventOnListener1(System.out::println);
      eventOnListener2(System.out::println);
      // This will create two merge groups with default methods in the implemented interfaces.
      eventOnListener1(e -> System.out.println(e.toString() + "!"));
      eventOnListener2(e -> System.out.println(e.toString() + "!"));
      eventOnListener1(e -> System.out.println(e.toString() + "#"));
      eventOnListener2(e -> System.out.println(e.toString() + "#"));
    }
  }
}
