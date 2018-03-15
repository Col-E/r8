// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.b72391662;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.VmTestRunner.IgnoreIfVmOlderThan;
import com.android.tools.r8.naming.b72391662.subpackage.OtherPackageSuper;
import com.android.tools.r8.naming.b72391662.subpackage.OtherPackageTestClass;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VmTestRunner.class)
public class B72391662 extends TestBase {

  private void doTest(boolean allowAccessModification, boolean minify) throws Exception {
    Class mainClass = TestMain.class;
    List<String> config = ImmutableList.of(
        allowAccessModification ?"-allowaccessmodification" : "",
        !minify ? "-dontobfuscate" : "",
        "-keep class " + mainClass.getCanonicalName() + " {",
        "  public void main(java.lang.String[]);",
        "}",
        "-keep class " + TestClass.class.getCanonicalName() + " {",
        "  *;",
        "}",
        "-keep class " + OtherPackageTestClass.class.getCanonicalName() + " {",
        "  *;",
        "}"
    );

    AndroidApp app = readClassesAndAndriodJar(ImmutableList.of(
        mainClass, Interface.class, Super.class, TestClass.class,
        OtherPackageSuper.class, OtherPackageTestClass.class));
    app = compileWithR8(app, String.join(System.lineSeparator(), config));
    assertEquals("123451234567\nABC\n", runOnArt(app, mainClass.getCanonicalName()));
  }

  @Test
  @IgnoreIfVmOlderThan(Version.V7_0_0)
  public void test() throws Exception {
    doTest(true, true);
    doTest(true, false);
    doTest(false, true);
    doTest(false, false);
  }
}
