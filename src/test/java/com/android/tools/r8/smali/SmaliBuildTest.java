// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.smali;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.EmbeddedOrigin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import org.junit.Test;

public class SmaliBuildTest extends SmaliTestBase {

  private void checkJavaLangString(AndroidApp application, boolean present) {
    try {
      DexInspector inspector = new DexInspector(application);
      ClassSubject clazz = inspector.clazz("java.lang.String");
      assertEquals(present, clazz.isPresent());
    } catch (IOException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void buildWithoutLibrary() {
    // Build simple "Hello, world!" application.
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);
    builder.addMainMethod(
        2,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    const-string        v1, \"Hello, world!\"",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "    return-void"
    );

    // No libraries added - java.lang.String is not present.
    AndroidApp originalApplication = buildApplication(builder);
    checkJavaLangString(originalApplication, false);

    AndroidApp processedApplication = processApplication(originalApplication);
    checkJavaLangString(processedApplication, false);
  }

  @Test
  public void buildWithLibrary() throws Throwable {
    // Build simple "Hello, world!" application.
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);
    builder.addMainMethod(
        2,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    const-string        v1, \"Hello, world!\"",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "    return-void"
    );

    AndroidApp originalApp =
        AndroidApp.builder()
            .addDexProgramData(builder.compile(), EmbeddedOrigin.INSTANCE)
            .addLibraryFiles(Paths.get(ToolHelper.getDefaultAndroidJar()))
            .build();

    // Java standard library added - java.lang.String is present.
    checkJavaLangString(originalApp, true);

    AndroidApp processedApplication = processApplication(originalApp);

    // The library method is not part of the output.
    checkJavaLangString(processedApplication, false);
  }
}
