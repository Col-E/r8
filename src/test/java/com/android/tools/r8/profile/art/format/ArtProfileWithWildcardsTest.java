// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.format;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.profile.art.ArtProfileBuilder;
import com.android.tools.r8.profile.art.ArtProfileProvider;
import com.android.tools.r8.profile.art.diagnostic.HumanReadableArtProfileParserErrorDiagnostic;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.UTF8TextInputStream;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArtProfileWithWildcardsTest extends TestBase {

  private static final String TEST_BINARY_NAME = binaryName(ArtProfileWithWildcardsTest.class);

  private static Path profileWithWildcards;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withMinimumApiLevel().build();
  }

  @BeforeClass
  public static void setup() throws Exception {
    profileWithWildcards = getStaticTemp().newFile().toPath();
    FileUtils.writeTextFile(
        profileWithWildcards, StringUtils.lines(getWildcardRules()) + descriptor(Main.class));
  }

  private static List<String> getWildcardRules() {
    return ImmutableList.of(
        "L" + TEST_BINARY_NAME + "$Mai?;",
        "L" + TEST_BINARY_NAME + "$*;",
        "L" + TEST_BINARY_NAME + "$**;",
        "HSPL" + TEST_BINARY_NAME + "$Mai?;->main([Ljava/lang/String;)V",
        "HSPL" + TEST_BINARY_NAME + "$*;->main([Ljava/lang/String;)V",
        "HSPL" + TEST_BINARY_NAME + "$**;->main([Ljava/lang/String;)V",
        "HSPL" + TEST_BINARY_NAME + "$Main;->mai?([Ljava/lang/String;)V",
        "HSPL" + TEST_BINARY_NAME + "$Main;->*([Ljava/lang/String;)V",
        "HSPL" + TEST_BINARY_NAME + "$Main;->**([Ljava/lang/String;)V",
        "HSPL" + TEST_BINARY_NAME + "$Main;->main([Ljava/lang/Strin?;)V",
        "HSPL" + TEST_BINARY_NAME + "$Main;->main([Ljava/lang/*;)V",
        "HSPL" + TEST_BINARY_NAME + "$Main;->main([Ljava/lang/**;)V",
        "HSPL" + TEST_BINARY_NAME + "$Main;->main([Ljava/lang/String;)?",
        "HSPL" + TEST_BINARY_NAME + "$Main;->main([Ljava/lang/String;)*",
        "HSPL" + TEST_BINARY_NAME + "$Main;->main([Ljava/lang/String;)**");
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramClasses(Main.class)
        .addArtProfileForRewriting(getArtProfileProvider())
        .release()
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(this::inspectDiagnostics)
        .inspectResidualArtProfile(this::inspectResidualArtProfile);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .addArtProfileForRewriting(getArtProfileProvider())
        .allowDiagnosticInfoMessages()
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(this::inspectDiagnostics)
        .inspectResidualArtProfile(this::inspectResidualArtProfile);
  }

  private ArtProfileProvider getArtProfileProvider() {
    return new ArtProfileProvider() {

      @Override
      public void getArtProfile(ArtProfileBuilder profileBuilder) {
        try {
          profileBuilder.addHumanReadableArtProfile(
              new UTF8TextInputStream(profileWithWildcards), parserBuilder -> {});
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }

      @Override
      public Origin getOrigin() {
        return new PathOrigin(profileWithWildcards);
      }
    };
  }

  private void inspectDiagnostics(TestDiagnosticMessages diagnostics) {
    diagnostics.assertInfosMatch(
        getWildcardRules().stream()
            .map(
                wildcardRule ->
                    allOf(
                        diagnosticType(HumanReadableArtProfileParserErrorDiagnostic.class),
                        diagnosticMessage(containsString(wildcardRule))))
            .collect(Collectors.toList()));
  }

  private void inspectResidualArtProfile(ArtProfileInspector profileInspector) {
    profileInspector
        .assertContainsClassRule(Reference.classFromClass(Main.class))
        .assertContainsNoOtherRules();
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
