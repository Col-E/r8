// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.ToolHelper;
import java.nio.file.Paths;

public class ScriptEngineTestBase extends TestBase {
  public void addRhinoForAndroid(TestBuilder builder) {
    builder
        // JSR 223: Scripting for the JavaTM Platform (https://jcp.org/en/jsr/detail?id=223).
        .addProgramFiles(Paths.get(ToolHelper.JSR223_RI_JAR))
        // The Rhino implementation.
        .addProgramFiles(Paths.get(ToolHelper.RHINO_JAR))
        // The rhino-android contains concrete implementation of sun.misc.Service
        // used by the JSR 223 RI, which is not in the Android runtime (except for N?).
        .addProgramFiles(Paths.get(ToolHelper.RHINO_ANDROID_JAR));
    if (builder instanceof R8FullTestBuilder) {
      ((R8FullTestBuilder) builder)
          // The rhino-android library have references to missing classes.
          .addOptionsModification(options -> options.ignoreMissingClasses = true);
    }
  }

  public void addKeepRulesForAndroidRhino(R8TestBuilder builder) {
    builder
        // Keep the service interface for script engine factories.
        .addKeepClassAndMembersRules("javax.script.ScriptEngineFactory")
        // Keep the Rhino script engine factory implementation.
        .addKeepClassAndMembersRules("com.sun.script.javascript.RhinoScriptEngineFactory")
        // Keep parts of com.sun.script.javascript.RhinoTopLevel.
        .addKeepRules(
            "-keep class com.sun.script.javascript.RhinoTopLevel {",
            "  public static java.lang.Object bindings(",
            "      org.mozilla.javascript.Context,",
            "      org.mozilla.javascript.Scriptable,",
            "      java.lang.Object[],",
            "      org.mozilla.javascript.Function);",
            "  public static java.lang.Object scope(",
            "      org.mozilla.javascript.Context,",
            "      org.mozilla.javascript.Scriptable,",
            "      java.lang.Object[],",
            "      org.mozilla.javascript.Function);",
            "  public static java.lang.Object sync(",
            "      org.mozilla.javascript.Context,",
            "      org.mozilla.javascript.Scriptable,",
            "      java.lang.Object[],",
            "      org.mozilla.javascript.Function);",
            "}")
        // TODO(b/136633154): See how to trim this.
        .addKeepRules("-keep class org.mozilla.javascript.** { *; }");
  }
}
