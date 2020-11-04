// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.utils.ThrowingAction;
import java.util.function.Consumer;

public class AssertUtils {

  public static <E extends Throwable> void assertFailsCompilationIf(
      boolean condition, ThrowingAction<E> action) throws E {
    assertFailsCompilationIf(condition, action, null);
  }

  public static <E extends Throwable> void assertFailsCompilationIf(
      boolean condition, ThrowingAction<E> action, Consumer<Throwable> consumer) throws E {
    assertThrowsIf(condition, CompilationFailedException.class, action, consumer);
  }

  public static <E extends Throwable> void assertThrowsIf(
      boolean condition, Class<? extends Throwable> clazz, ThrowingAction<E> action) throws E {
    assertThrowsIf(condition, clazz, action, null);
  }

  public static <E extends Throwable> void assertThrowsIf(
      boolean condition,
      Class<? extends Throwable> clazz,
      ThrowingAction<E> action,
      Consumer<Throwable> consumer)
      throws E {
    if (condition) {
      try {
        action.execute();
        fail("Expected action to fail with an exception, but succeeded");
      } catch (Throwable e) {
        assertEquals(clazz, e.getClass());
        if (consumer != null) {
          consumer.accept(e);
        }
      }
    } else {
      action.execute();
    }
  }
}
