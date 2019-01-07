// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ExternalR8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import org.junit.Test;

class PrintConfigurationTestClass {

  public static void main(String[] args) {}
}

public class PrintConfigurationTest extends TestBase {

  @Test
  public void testSingleConfigurationWithAbsolutePath() throws Exception {
    Path printConfigurationFile = temp.newFile().toPath();
    String proguardConfig =
        StringUtils.lines(
            keepMainProguardConfiguration(PrintConfigurationTestClass.class),
            "-printconfiguration " + printConfigurationFile);
    testForR8(Backend.DEX)
        .addProgramClasses(PrintConfigurationTestClass.class)
        .addKeepRules(proguardConfig)
        .compile();
    assertEquals(proguardConfig, FileUtils.readTextFile(printConfigurationFile, Charsets.UTF_8));
  }

  @Test
  public void testSingleConfigurationWithRelativePath() throws Exception {
    String proguardConfig =
        StringUtils.lines(
            keepMainProguardConfiguration(PrintConfigurationTestClass.class),
            "-printconfiguration generated-proguard-config.txt");
    ExternalR8TestCompileResult result =
        testForExternalR8(Backend.DEX)
            .addProgramClasses(PrintConfigurationTestClass.class)
            .addKeepRules(proguardConfig.trim())
            .compile();
    Path printConfigurationFile =
        result.outputJar().getParent().resolve("generated-proguard-config.txt");
    assertEquals(proguardConfig, FileUtils.readTextFile(printConfigurationFile, Charsets.UTF_8));
  }

  @Test
  public void testIncludeFile() throws Exception {
    Class mainClass = PrintConfigurationTestClass.class;
    String includeProguardConfig = keepMainProguardConfiguration(mainClass);
    Path includeFile = temp.newFile().toPath();
    FileUtils.writeTextFile(includeFile, includeProguardConfig);
    Path printConfigurationFile = temp.newFile().toPath();
    String proguardConfig = String.join(System.lineSeparator(), ImmutableList.of(
        "-include " + includeFile.toString(),
        "-printconfiguration " + printConfigurationFile.toString()
    ));

    String expected = String.join(System.lineSeparator(), ImmutableList.of(
        "",  // The -include line turns into an empty line.
        includeProguardConfig,
        "",  // Writing to the file adds an ending line separator
        "",  // An empty line is emitted between two parts
        "-printconfiguration " + printConfigurationFile.toString()
    ));
    compileWithR8(ImmutableList.of(mainClass), proguardConfig);
    assertEquals(expected, FileUtils.readTextFile(printConfigurationFile, Charsets.UTF_8));
  }
}
