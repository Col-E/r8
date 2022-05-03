// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal.interfaces;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CollisionWithDefaultMethodsTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build());
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("Event1", "Event2");

  @Test
  public void testDesugaring() throws Exception {
    testForDesugaring(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters.getApiLevel())
        .addKeepRules("-keep class ** { *; }")
        .run(parameters.getRuntime(), TestClass.class)
        // TODO(b/229951607): This should never throw ICCE.
        .applyIf(
            parameters.isDexRuntime() && hasDefaultInterfaceMethodsSupport(parameters),
            r -> r.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class),
            r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT));
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
    }
  }
}
