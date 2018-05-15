// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.regalloc;

import static com.android.tools.r8.utils.DexInspectorMatchers.isPresent;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import org.junit.Test;

public class B79405526 extends TestBase {
  @Test
  public void test() throws Exception {
    AndroidApp app = compileWithD8(readClasses(TestClass.class));
    DexInspector inspector = new DexInspector(app);
    DexInspector.ClassSubject clazz = inspector.clazz(TestClass.class);
    assertThat(clazz, isPresent());
    // Throws if a method in TestClass does not verify.
    runOnArt(app, TestClass.class.getName());
  }

  private static class TestClass {
    public static void main(String[] args) {}

    public void method() {
      Object x = this;
      TestClass y = this;
      nop(null, getObject("", this));
      TestClass z = getObject(null);
      nop(null, getObject("", this, X.Y.getLong(getLong() - 0L)));
      if (getBoolean()) {
        z = getObject(this);
        nop1(null, null);
      }
      nop2(null, null, null, null);
    }

    public static void nop(Object a, Object b, Object... c) {}

    public static void nop1(Object... a) {}

    private void nop2(Object a, Object b, Object c, Object d, Object... e) {}

    public boolean getBoolean() {
      return true;
    }

    public long getLong() {
      return 0L;
    }

    private TestClass getObject(Object a) {
      return null;
    }

    private static TestClass getObject(Object a, Object... c) {
      return null;
    }

    public enum X {
      Y {};

      public long getLong(long var1) {
        return 0L;
      }
    }
  }
}
