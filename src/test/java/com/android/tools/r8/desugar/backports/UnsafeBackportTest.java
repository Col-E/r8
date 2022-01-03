// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.lang.reflect.Field;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnsafeBackportTest extends AbstractBackportTest {

  private static final String UNSAFE_TYPE_NAME = "sun.misc.Unsafe";
  private static final String UNSAFE_DESCRIPTOR =
      DescriptorUtils.javaTypeToDescriptor(UNSAFE_TYPE_NAME);

  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withDexRuntimesStartingFromExcluding(Version.V4_0_4)
        .withAllApiLevels()
        .build();
  }

  public UnsafeBackportTest(TestParameters parameters) throws IOException {
    super(
        parameters,
        "sun.misc.Unsafe",
        ImmutableList.of(UnsafeBackportTest.getTestRunner(), UnsafeBackportTest.getA()));

    ignoreInvokes("objectFieldOffset");

    // sun.misc.Unsafe issue is on API 31.
    registerTarget(AndroidApiLevel.Sv2, 3);
  }

  public static class UnsafeStub {

    boolean compareAndSwapObject(Object receiver, long offset, Object expect, Object update) {
      throw new RuntimeException("Stub called.");
    }

    public long objectFieldOffset(Field field) {
      throw new RuntimeException("Stub called.");
    }
  }

  private static byte[] getTestRunner() throws IOException {
    return transformer(TestRunner.class)
        .setReturnType(MethodPredicate.onName("getUnsafe"), UNSAFE_TYPE_NAME)
        .replaceClassDescriptorInMethodInstructions(descriptor(UnsafeStub.class), UNSAFE_DESCRIPTOR)
        .transform();
  }

  private static byte[] getA() throws IOException {
    return transformer(A.class).transform();
  }

  public static class A {
    public String field;
  }

  public static class TestRunner extends MiniAssert {

    private static UnsafeStub getUnsafe() throws Exception {
      Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
      Field f = unsafeClass.getDeclaredField("theUnsafe");
      f.setAccessible(true);
      return (UnsafeStub) f.get(null);
    }

    public static void main(String[] args) throws Exception {
      UnsafeStub theUnsafe = getUnsafe();
      A x = new A();
      long offset = theUnsafe.objectFieldOffset(A.class.getField("field"));
      assertTrue(theUnsafe.compareAndSwapObject(x, offset, null, "A"));
      assertTrue(theUnsafe.compareAndSwapObject(x, offset, "A", "B"));
      assertFalse(theUnsafe.compareAndSwapObject(x, offset, "A", "B"));
    }
  }
}
