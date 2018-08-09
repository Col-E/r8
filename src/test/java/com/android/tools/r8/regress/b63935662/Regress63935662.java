// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b63935662;

import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.OffOrAuto;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress63935662 extends TestBase {

  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }

  public Regress63935662(Backend backend) {
    this.backend = backend;
  }

  void run(AndroidApp app, Class mainClass) throws Exception {
    Path proguardConfig =
        writeTextToTempFile(keepMainProguardConfiguration(mainClass, true, false));
    R8Command.Builder builder =
        ToolHelper.prepareR8CommandBuilder(app, emptyConsumer(backend))
            .addLibraryFiles(runtimeJar(backend))
            .addProguardConfigurationFiles(proguardConfig);
    if (backend == Backend.DEX) {
      builder.setMinApiLevel(AndroidApiLevel.L.getLevel());
    }
    String resultFromJava = runOnJava(mainClass);
    app =
        ToolHelper.runR8(
            builder.build(), options -> options.interfaceMethodDesugaring = OffOrAuto.Auto);
    String result;
    if (backend == Backend.DEX) {
      result = runOnArt(app, mainClass);
    } else {
      assert backend == Backend.CF;
      result = runOnJava(app, mainClass);
    }
    Assert.assertEquals(resultFromJava, result);
  }

  @Test
  public void test() throws Exception {
    Class mainClass = TestClass.class;
    AndroidApp app = readClasses(
        TestClass.Top.class, TestClass.Left.class, TestClass.Right.class, TestClass.Bottom.class,
        TestClass.X1.class, TestClass.X2.class, TestClass.X3.class, TestClass.X4.class, TestClass.X5.class,
        mainClass);
    run(app, mainClass);
  }

  @Test
  public void test2() throws Exception {
    Class mainClass = TestFromBug.class;
    AndroidApp app = readClasses(
        TestFromBug.Map.class, TestFromBug.AbstractMap.class,
        TestFromBug.ConcurrentMap.class, TestFromBug.ConcurrentHashMap.class,
        mainClass);
    run(app, mainClass);
  }
}
