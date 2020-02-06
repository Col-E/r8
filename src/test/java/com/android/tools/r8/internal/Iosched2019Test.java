// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Iosched2019Test extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public Iosched2019Test(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    assumeTrue(ToolHelper.isLocalDevelopment());

    List<Path> programFiles =
        Files.list(Paths.get(ToolHelper.THIRD_PARTY_DIR, "iosched_2019"))
            .filter(path -> path.toString().endsWith(".jar"))
            .collect(Collectors.toList());
    assertEquals(155, programFiles.size());

    testForR8(parameters.getBackend())
        .addProgramFiles(programFiles)
        .addKeepRuleFiles(
            Paths.get(ToolHelper.THIRD_PARTY_DIR, "iosched_2019", "proguard-rules.pro"))
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .allowDiagnosticMessages()
        .allowUnusedProguardConfigurationRules()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .assertAllInfoMessagesMatch(
            anyOf(
                containsString("Ignoring option: "),
                containsString("Proguard configuration rule does not match anything: ")))
        .assertAllWarningMessagesMatch(
            anyOf(
                containsString("Missing class: "),
                containsString("required for default or static interface methods desugaring"),
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists.")))
        .assertNoErrorMessages();
  }
}
