// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.twr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.examples.JavaExampleClassProxy;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TwrCloseResourceDuplicationTest extends TestBase {

  private static final String PKG = "twrcloseresourceduplication";
  private static final String EXAMPLE = "examplesJava9/" + PKG;
  private final JavaExampleClassProxy MAIN =
      new JavaExampleClassProxy(EXAMPLE, PKG + "/TwrCloseResourceDuplication");
  private final JavaExampleClassProxy FOO =
      new JavaExampleClassProxy(EXAMPLE, PKG + "/TwrCloseResourceDuplication$Foo");
  private final JavaExampleClassProxy BAR =
      new JavaExampleClassProxy(EXAMPLE, PKG + "/TwrCloseResourceDuplication$Bar");

  static final int INPUT_CLASSES = 3;

  static final String EXPECTED =
      StringUtils.lines(
          "foo opened 1",
          "foo post close 1",
          "foo opened 2",
          "foo caught from 2: RuntimeException",
          "foo post close 2",
          "bar opened 1",
          "bar post close 1",
          "bar opened 2",
          "bar caught from 2: RuntimeException",
          "bar post close 2");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK9)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public TwrCloseResourceDuplicationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private String getZipFile() throws IOException {
    return ZipUtils.ZipBuilder.builder(temp.newFile("file.zip").toPath())
        // DEX VMs from 4.4 up-to 9.0 including, will fail if no entry is added.
        .addBytes("entry", new byte[1])
        .build()
        .toString();
  }

  private List<Path> getProgramInputs() throws Exception {
    return ImmutableList.of(JavaExampleClassProxy.examplesJar(EXAMPLE));
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addProgramFiles(getProgramInputs())
        .run(parameters.getRuntime(), MAIN.typeName(), getZipFile())
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramFiles(getProgramInputs())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), MAIN.typeName(), getZipFile())
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector -> {
              // There should be two synthetic classes besides the three program classes.
              // One for the desugar version of TWR $closeResource and one for the
              // Throwable.addSuppressed that is still present in the original $closeResource.
              // TODO(b/214329923): If the original $closeResource is pruned this will decrease.
              // TODO(b/168568827): Once we support a nested addSuppressed this will increase.
              int expectedSynthetics =
                  parameters.getApiLevel().isLessThan(apiLevelWithTwrCloseResourceSupport())
                      ? 3
                      : 0;
              assertEquals(INPUT_CLASSES + expectedSynthetics, inspector.allClasses().size());
            });
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForR8(parameters.getBackend())
        .addProgramFiles(getProgramInputs())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
        .addKeepMainRule(MAIN.typeName())
        .addKeepClassAndMembersRules(FOO.typeName(), BAR.typeName())
        .setMinApi(parameters.getApiLevel())
        .addDontObfuscate()
        .run(parameters.getRuntime(), MAIN.typeName(), getZipFile())
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector -> {
              List<FoundClassSubject> foundClassSubjects = inspector.allClasses();
              Set<String> foundClasses =
                  foundClassSubjects.stream()
                      .map(FoundClassSubject::getFinalName)
                      .collect(Collectors.toSet());
              // R8 will optimize the generated methods for the two cases below where the thrown
              // exception is known or not, thus the synthetic methods will be 2.
              Set<String> nonSyntheticClassOutput =
                  ImmutableSet.of(FOO.typeName(), BAR.typeName(), MAIN.typeName());
              if (parameters.getApiLevel().isLessThan(apiLevelWithTwrCloseResourceSupport())) {
                Set<String> classOutputWithSynthetics = new HashSet<>(nonSyntheticClassOutput);
                classOutputWithSynthetics.add(
                    SyntheticItemsTestUtils.syntheticApiOutlineClass(BAR.getClassReference(), 0)
                        .getTypeName());
                assertEquals(classOutputWithSynthetics, foundClasses);
              } else {
                assertEquals(nonSyntheticClassOutput, foundClasses);
              }
            });
  }

}
