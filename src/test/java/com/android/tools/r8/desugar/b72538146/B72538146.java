// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.b72538146;

import static org.junit.Assert.assertEquals;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.AndroidApp;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

public class B72538146 extends TestBase {

  @Test
  public void test() throws Exception {
    // Build the main app from source compiled separately using the Android API for classloading.
    AndroidApp.Builder builder = AndroidApp.builder();
    builder.addProgramFile(
        Paths.get("build/test/examplesAndroidApi/classes/classloader/Runner.class"));
    AndroidApp app = compileWithD8(builder.build());

    // Compile the parent and child applications into separate dex applications.
    Path parent = temp.newFolder("parent").toPath().resolve("classes.zip");
    Path child = temp.newFolder("child").toPath().resolve("classes.zip");
    AndroidApp parentApp = readClasses(
        Parent.class,
        Parent.Inner1.class,
        Parent.Inner2.class,
        Parent.Inner3.class,
        Parent.Inner4.class);
    compileWithD8(parentApp).write(parent, OutputMode.DexIndexed);

    AndroidApp childApp = readClasses(Child.class);
    compileWithD8(childApp).write(child, OutputMode.DexIndexed);

    // Run the classloader test loading the two dex applications.
    String result = runOnArt(app, "classloader.Runner",
        parent.toString(), child.toString(), "com.android.tools.r8.desugar.b72538146.Child");
    assertEquals("SUCCESS", result);
  }
}
