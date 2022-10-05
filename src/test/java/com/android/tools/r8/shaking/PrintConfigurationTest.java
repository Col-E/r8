// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

class PrintConfigurationTestClass {

  public static void main(String[] args) {}
}

@RunWith(Parameterized.class)
public class PrintConfigurationTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withSystemRuntime().build();
  }

  public PrintConfigurationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

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
    assertEqualsStripOrigin(
        proguardConfig, FileUtils.readTextFile(printConfigurationFile, Charsets.UTF_8));
  }

  private String removeOriginComments(String s) {
    return StringUtils.lines(
        StringUtils.splitLines(s).stream()
            .filter(line -> !line.startsWith("# The proguard"))
            .filter(line -> !line.startsWith("# End of content"))
            .filter(line -> !line.equals(""))
            .collect(Collectors.toList()));
  }

  private void assertEqualsStripOrigin(String a, String b) {
    String expected = removeOriginComments(a);
    assertEquals(expected, removeOriginComments(b));
  }

  @Test
  public void testSingleConfigurationWithRelativePath() throws Exception {
    Path subDirectory = temp.newFolder().toPath();
    Path proguardConfigFile = subDirectory.resolve("proguard-config.txt");
    Path proguardConfigOutFile = subDirectory.resolve("proguard-config-out.txt");

    String proguardConfig =
        StringUtils.lines(
            keepMainProguardConfiguration(PrintConfigurationTestClass.class),
            "-printconfiguration proguard-config-out.txt");
    FileUtils.writeTextFile(proguardConfigFile, proguardConfig.trim());

    assertEquals(TestRuntime.getSystemRuntime(), parameters.getRuntime());
    testForExternalR8(Backend.DEX, parameters.getRuntime())
        .addProgramClasses(PrintConfigurationTestClass.class)
        .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
        .addKeepRuleFiles(proguardConfigFile)
        .compile();

    String outFileContent = FileUtils.readTextFile(proguardConfigOutFile, Charsets.UTF_8);
    assertEqualsStripOrigin(proguardConfig, outFileContent);

    // We should have added the proguard-config.txt file as the origin in the config output
    String firstLine = StringUtils.splitLines(outFileContent).get(0);
    assertThat(outFileContent, containsString(proguardConfigFile.toString()));
  }

  @Test
  public void testSingleConfigurationWithRelativePathCompatibility() throws Exception {
    Path subDirectory = temp.newFolder().toPath();
    Path proguardConfigFile = subDirectory.resolve("proguard-config.txt");
    Path proguardConfigOutFile = subDirectory.resolve("proguard-config-out.txt");

    String proguardConfig =
        StringUtils.lines(
            keepMainProguardConfiguration(PrintConfigurationTestClass.class),
            "-printconfiguration proguard-config-out.txt");
    FileUtils.writeTextFile(proguardConfigFile, proguardConfig);

    testForProguard()
        .addProgramClasses(PrintConfigurationTestClass.class)
        .addKeepRuleFiles(proguardConfigFile)
        .compile();

    String proguardConfigOut = FileUtils.readTextFile(proguardConfigOutFile, Charsets.UTF_8);
    assertThat(
        proguardConfigOut,
        containsString(
            StringUtils.lines(
                "-keep class com.android.tools.r8.shaking.PrintConfigurationTestClass {",
                "    public static void main(java.lang.String[]);",
                "}")));
    assertThat(
        proguardConfigOut,
        containsString(
            "-printconfiguration "
                + (ToolHelper.isWindows()
                    ? ("'" + proguardConfigOutFile.toAbsolutePath().toString() + "'")
                    : proguardConfigOutFile.toAbsolutePath().toString())));
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
    assertEqualsStripOrigin(
        expected, FileUtils.readTextFile(printConfigurationFile, Charsets.UTF_8));
  }
}
