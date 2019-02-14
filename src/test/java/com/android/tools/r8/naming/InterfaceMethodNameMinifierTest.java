// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.utils.FileUtils;
import java.nio.file.Path;
import org.junit.Test;

/** Regression test for b/123730537. */
public class InterfaceMethodNameMinifierTest extends TestBase {

  @Test
  public void test() throws Exception {
    Path dictionary = temp.getRoot().toPath().resolve("dictionary.txt");
    FileUtils.writeTextFile(dictionary, "a", "b", "c");

    testForR8(Backend.DEX)
        .addInnerClasses(InterfaceMethodNameMinifierTest.class)
        .addKeepRules(
            "-keep,allowobfuscation interface * { <methods>; }",
            "-keep,allowobfuscation class * { <methods>; }",
            "-keep interface " + L.class.getTypeName() + " { <methods>; }",
            "-obfuscationdictionary " + dictionary.toString())
        // Minify the interface methods in alphabetic order.
        .addOptionsModification(
            options -> options.testing.minifier.interfaceMethodOrdering = DexMethod::slowCompareTo)
        .compile();
  }

  interface I {}

  interface J extends I {

    // Will be renamed first, to a().
    void a();

    // Will be renamed secondly. Should be renamed to c(), and not b(), because `void K.b()` will
    // be renamed to b() because `void L.b()` is reserved.
    void c();
  }

  interface K extends I {

    // Will be renamed thirdly, to b(), because `void L.b()` is reserved.
    void b();
  }

  interface L {

    // Reserved. Will be renamed together with `void K.b()`.
    void b();
  }

  static class Implementation implements J, K {

    @Override
    public void a() {}

    @Override
    public void b() {}

    @Override
    public void c() {}
  }
}
