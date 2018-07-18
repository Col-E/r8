// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.b80083341;

import static com.android.tools.r8.utils.DescriptorUtils.getClassNameFromDescriptor;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VmTestRunner.class)
public class B80083341 extends TestBase {

  @Test
  public void test() throws Exception {
    Class mainClass = TestMain.class;
    List<String> config = ImmutableList.of(
        "-printmapping",
        "-keepattributes EnclosingMethod,InnerClasses,Signature",
        "-repackageclasses",
        "-keepclassmembers class " + mainClass.getCanonicalName() + " {",
        "  public void main(...);",
        "}",
        "-keep,allowobfuscation class " + mainClass.getCanonicalName() + " {",
        "}"
    );
    AndroidApp app = readClassesAndAndriodJar(ImmutableList.of(
        mainClass, TestClass.class, AnotherClass.class,
        PackagePrivateClass.class, PackagePrivateClass.Itf.class, PackagePrivateClass.Impl.class
    ));
    AndroidApp processedApp = compileWithR8(app, String.join(System.lineSeparator(), config));
    CodeInspector inspector = new CodeInspector(processedApp);
    ClassSubject mainSubject = inspector.clazz(mainClass);
    assertThat(mainSubject, isPresent());

    ProcessResult artResult =
        runOnArtRaw(processedApp, getClassNameFromDescriptor(mainSubject.getFinalDescriptor()));
    assertEquals(0, artResult.exitCode);
    assertEquals(-1, artResult.stderr.indexOf("IllegalAccessError"));
  }
}
