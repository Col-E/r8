// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.profile.art.ArtProfile;
import com.android.tools.r8.profile.art.ArtProfileConsumer;
import com.android.tools.r8.profile.art.ArtProfileForRewriting;
import com.android.tools.r8.profile.art.ArtProfileProvider;
import com.android.tools.r8.profile.startup.profile.StartupProfile;
import com.android.tools.r8.profile.startup.profile.StartupProfileRule;
import com.android.tools.r8.startup.StartupProfileProvider;
import com.android.tools.r8.startup.diagnostic.MissingStartupProfileItemsDiagnostic;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;

public abstract class CommandTestBase<C extends BaseCompilerCommand> extends TestBase {

  public boolean isL8() {
    return false;
  }

  private void mapDiagnosticsMissingArguments(String... args) {
    try {
      DiagnosticsChecker.checkErrorsContains(
          "Missing argument(s) for --map-diagnostics", handler -> parse(handler, args));
      fail("Expected failure");
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  @Test
  public void mapDiagnosticsMissingArguments() throws Exception {
    mapDiagnosticsMissingArguments("--map-diagnostics");
    mapDiagnosticsMissingArguments("--map-diagnostics", "error");
    mapDiagnosticsMissingArguments("--map-diagnostics", "warning");
    mapDiagnosticsMissingArguments("--map-diagnostics", "info");
    mapDiagnosticsMissingArguments("--map-diagnostics", "xxx");
  }

  private void mapDiagnosticsInvalidArguments(String... args) {
    try {
      DiagnosticsChecker.checkErrorsContains(
          "Invalid diagnostics level 'xxx'", handler -> parse(handler, args));
      fail("Expected failure");
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  @Test
  public void mapDiagnosticsInvalidArguments() throws Exception {
    mapDiagnosticsInvalidArguments("--map-diagnostics", "error", "xxx");
    mapDiagnosticsInvalidArguments("--map-diagnostics", "warning", "xxx");
    mapDiagnosticsInvalidArguments("--map-diagnostics", "info", "xxx");
    mapDiagnosticsInvalidArguments("--map-diagnostics", "xxx", "xxx");
    mapDiagnosticsInvalidArguments("--map-diagnostics", "xxx", "error");
    mapDiagnosticsInvalidArguments("--map-diagnostics", "xxx", "warning");
    mapDiagnosticsInvalidArguments("--map-diagnostics", "xxx", "info");
    mapDiagnosticsInvalidArguments("--debug", "--map-diagnostics", "error", "xxx", "--debug");
  }

  @Test
  public void mapDiagnosticsInvalidArgumentsMoreErrors() {
    try {
      DiagnosticsChecker.checkErrorsContains(
          ImmutableList.of("Invalid diagnostics level 'xxx'", "Unknown option: --xxx"),
          handler -> parse(handler, "--debug", "--map-diagnostics", "error", "xxx", "--xxx"));
      fail("Expected failure");
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  @Test
  public void errorsToWarnings() throws Exception {
    DiagnosticsChecker.checkWarningsContains(
        "Error",
        handler -> {
          C command = parseWithRequiredArgs(handler, "--map-diagnostics", "error", "warning");
          command.getReporter().error("Error");
        });

    try {
      DiagnosticsChecker.checkErrorsContains(
          "Test diagnostic",
          handler -> {
            C command =
                parseWithRequiredArgs(handler, "--map-diagnostics:a.b.C", "error", "warning");
            command.getReporter().error(new TestDiagnostic());
            try {
              command.getReporter().failIfPendingErrors();
            } catch (RuntimeException e) {
              throw InternalCompilationFailedExceptionUtils.createForTesting();
            }
          });
      fail("Failure expected");
    } catch (CompilationFailedException e) {
      // Expected.
    }

    DiagnosticsChecker.checkWarningsContains(
        "Test diagnostic",
        handler -> {
          C command =
              parseWithRequiredArgs(
                  handler,
                  "--map-diagnostics:com.android.tools.r8.TestDiagnostic",
                  "error",
                  "warning");
          command.getReporter().error(new TestDiagnostic());
        });

    DiagnosticsChecker.checkWarningsContains(
        "Test diagnostic",
        handler -> {
          C command =
              parseWithRequiredArgs(
                  handler, "--map-diagnostics:TestDiagnostic", "error", "warning");
          command.getReporter().error(new TestDiagnostic());
        });
  }

  @Test
  public void errorsToInfo() throws Exception {
    DiagnosticsChecker.checkInfosContains(
        "Error",
        handler -> {
          C command = parseWithRequiredArgs(handler, "--map-diagnostics", "error", "info");
          command.getReporter().error("Error");
        });

    try {
      DiagnosticsChecker.checkErrorsContains(
          "Test diagnostic",
          handler -> {
            C command = parseWithRequiredArgs(handler, "--map-diagnostics:a.b.C", "error", "info");
            command.getReporter().error(new TestDiagnostic());
            try {
              command.getReporter().failIfPendingErrors();
            } catch (RuntimeException e) {
              throw InternalCompilationFailedExceptionUtils.createForTesting();
            }
          });
      fail("Failure expected");
    } catch (CompilationFailedException e) {
      // Expected.
    }

    DiagnosticsChecker.checkInfosContains(
        "Test diagnostic",
        handler -> {
          C command =
              parseWithRequiredArgs(handler, "--map-diagnostics:TestDiagnostic", "error", "info");
          command.getReporter().error(new TestDiagnostic());
        });
  }

  @Test
  public void warningsToInfo() throws Exception {
    DiagnosticsChecker.checkInfosContains(
        "Warning",
        handler -> {
          C command = parseWithRequiredArgs(handler, "--map-diagnostics", "warning", "info");
          command.getReporter().warning("Warning");
        });

    DiagnosticsChecker.checkInfosContains(
        "Test diagnostic",
        handler -> {
          C command =
              parseWithRequiredArgs(handler, "--map-diagnostics:TestDiagnostic", "warning", "info");
          command.getReporter().warning(new TestDiagnostic());
        });
  }

  @Test
  public void warningsToError() {
    try {
      DiagnosticsChecker.checkErrorsContains(
          "Warning",
          handler -> {
            C command = parseWithRequiredArgs(handler, "--map-diagnostics", "warning", "error");
            command.getReporter().warning("Warning");
            try {
              command.getReporter().failIfPendingErrors();
            } catch (RuntimeException e) {
              throw InternalCompilationFailedExceptionUtils.createForTesting();
            }
          });
      fail("Expected failure");
    } catch (CompilationFailedException e) {
      // Expected.
    }

    try {
      DiagnosticsChecker.checkErrorsContains(
          "Test diagnostic",
          handler -> {
            C command =
                parseWithRequiredArgs(
                    handler, "--map-diagnostics:TestDiagnostic", "warning", "error");
            command.getReporter().warning(new TestDiagnostic());
            try {
              command.getReporter().failIfPendingErrors();
            } catch (RuntimeException e) {
              throw InternalCompilationFailedExceptionUtils.createForTesting();
            }
          });
      fail("Expected failure");
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  @Test
  public void errorsToWarningsWarningsToInfos() throws Exception {
    DiagnosticsChecker.checkInfosContains(
        "Error",
        handler -> {
          C command =
              parseWithRequiredArgs(
                  handler,
                  "--map-diagnostics",
                  "error",
                  "warning",
                  "--map-diagnostics",
                  "warning",
                  "info");
          command.getReporter().error("Error");
        });

    DiagnosticsChecker.checkInfosContains(
        "Test diagnostic",
        handler -> {
          C command =
              parseWithRequiredArgs(
                  handler,
                  "--map-diagnostics:TestDiagnostic",
                  "error",
                  "warning",
                  "--map-diagnostics",
                  "warning",
                  "info");
          command.getReporter().error(new TestDiagnostic());
        });
  }

  @Test
  public void warningsToInfosErrorsToWarnings() throws Exception {
    DiagnosticsChecker.checkDiagnostics(
        diagnostics -> {
          assertEquals(0, diagnostics.errors.size());
          diagnostics.checkWarningsContains("Error");
          diagnostics.checkInfosContains("Warning");
        },
        handler -> {
          C command =
              parseWithRequiredArgs(
                  handler,
                  "--map-diagnostics",
                  "warning",
                  "info",
                  "--map-diagnostics",
                  "error",
                  "warning");
          command.getReporter().error("Error");
          command.getReporter().warning("Warning");
        });

    DiagnosticsChecker.checkDiagnostics(
        diagnostics -> {
          assertEquals(0, diagnostics.errors.size());
          diagnostics.checkWarningsContains("Test diagnostic");
          diagnostics.checkInfosContains("Test diagnostic");
        },
        handler -> {
          C command =
              parseWithRequiredArgs(
                  handler,
                  "--map-diagnostics:TestDiagnostic",
                  "warning",
                  "info",
                  "--map-diagnostics:TestDiagnostic",
                  "error",
                  "warning");
          command.getReporter().error(new TestDiagnostic());
          command.getReporter().warning(new TestDiagnostic());
        });
  }

  @Test
  public void artProfileFlagAbsentTest() throws Exception {
    assertTrue(parseWithRequiredArgs().getArtProfilesForRewriting().isEmpty());
  }

  @Test
  public void artProfileFlagPresentTest() throws Exception {
    // Create a simple profile.
    Path profile = temp.newFile("profile.txt").toPath();
    Path residualProfile = temp.newFile("profile-out.txt").toPath();
    String profileRuleFlags = "HSP";
    String profileRuleDescriptor = "Lfoo/bar/Baz;->qux()V";
    FileUtils.writeTextFile(profile, profileRuleFlags + profileRuleDescriptor);

    // Pass the profile on the command line.
    List<String> args =
        new ArrayList<>(
            ImmutableList.of("--art-profile", profile.toString(), residualProfile.toString()));
    // L8 only accepts an art profile when shrinking.
    if (this instanceof L8CommandTest) {
      Path path = temp.newFile("proguard-rules.pro").toPath();
      FileUtils.writeTextFile(path, "-dontshrink");
      args.addAll(ImmutableList.of("--pg-conf", path.toString()));
    }
    List<ArtProfileForRewriting> artProfilesForRewriting =
        parseWithRequiredArgs(args.toArray(new String[0])).getArtProfilesForRewriting();
    assertEquals(1, artProfilesForRewriting.size());

    // Extract inputs.
    ArtProfileForRewriting artProfileForRewriting = artProfilesForRewriting.get(0);
    ArtProfileProvider artProfileProvider = artProfileForRewriting.getArtProfileProvider();
    ArtProfileConsumer residualArtProfileConsumer =
        artProfileForRewriting.getResidualArtProfileConsumer();
    InternalOptions options = new InternalOptions();

    // Build provided ART profile.
    ArtProfile.Builder artProfileBuilder =
        ArtProfile.builderForInitialArtProfile(artProfileProvider, options);
    artProfileProvider.getArtProfile(artProfileBuilder);
    ArtProfile artProfile = artProfileBuilder.build();

    // Verify we found the same rule.
    assertEquals(1, artProfile.size());
    IntBox count = new IntBox();
    artProfile.forEachRule(
        classRule -> fail(),
        methodRule -> {
          assertEquals(profileRuleDescriptor, methodRule.getMethod().toSmaliString());
          assertTrue(methodRule.getMethodRuleInfo().isHot());
          assertTrue(methodRule.getMethodRuleInfo().isStartup());
          assertTrue(methodRule.getMethodRuleInfo().isPostStartup());
          count.increment();
        });
    assertEquals(1, count.get());

    // Supply the rule back to the consumer.
    artProfile.supplyConsumer(residualArtProfileConsumer, options.reporter);
    assertEquals(
        ImmutableList.of(profileRuleFlags + profileRuleDescriptor),
        FileUtils.readAllLines(residualProfile));
  }

  @Test
  public void artProfileFlagMissingInputOutputParameterTest() {
    String expectedErrorContains = "Missing parameter for --art-profile.";
    try {
      DiagnosticsChecker.checkErrorsContains(
          expectedErrorContains, handler -> parseWithRequiredArgs(handler, "--art-profile"));
      fail("Expected failure");
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  @Test
  public void artProfileFlagMissingOutputParameterTest() throws Exception {
    String expectedErrorContains = "Missing parameter for --art-profile.";
    Path profile = temp.newFile("profile.txt").toPath();
    FileUtils.writeTextFile(profile, "");
    try {
      DiagnosticsChecker.checkErrorsContains(
          expectedErrorContains,
          handler -> parseWithRequiredArgs(handler, "--art-profile", profile.toString()));
      fail("Expected failure");
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  @Test
  public void startupProfileFlagAbsentTest() throws Exception {
    assertTrue(parseWithRequiredArgs().getStartupProfileProviders().isEmpty());
  }

  @Test
  public void startupProfileFlagPresentTest() throws Exception {
    // Create a simple profile.
    Path profile = temp.newFile("profile.txt").toPath();
    String profileRule = "Lfoo/bar/Baz;->qux()V";
    FileUtils.writeTextFile(profile, profileRule);

    // Pass the profile on the command line.
    List<StartupProfileProvider> startupProfileProviders;
    try {
      startupProfileProviders =
          parseWithRequiredArgs(
                  "--min-api",
                  Integer.toString(AndroidApiLevel.L.getLevel()),
                  "--startup-profile",
                  profile.toString())
              .getStartupProfileProviders();
    } catch (CompilationFailedException e) {
      assertTrue(isL8());
      return;
    }

    assertEquals(1, startupProfileProviders.size());

    // Construct the internal profile representation using the provider.
    InternalOptions options = new InternalOptions();
    MissingStartupProfileItemsDiagnostic.Builder missingStartupProfileItemsDiagnosticBuilder =
        MissingStartupProfileItemsDiagnostic.Builder.nop();
    StartupProfileProvider startupProfileProvider = startupProfileProviders.get(0);
    StartupProfile.Builder startupProfileBuilder =
        StartupProfile.builder(
            options, missingStartupProfileItemsDiagnosticBuilder, startupProfileProvider);
    startupProfileProvider.getStartupProfile(startupProfileBuilder);

    // Verify we found the same rule.
    StartupProfile startupProfile = startupProfileBuilder.build();
    Collection<StartupProfileRule> startupItems =
        ListUtils.newArrayList(consumer -> startupProfile.forEachRule(consumer::accept));
    assertEquals(1, startupItems.size());
    StartupProfileRule startupItem = startupItems.iterator().next();
    startupItem.accept(
        startupClass -> fail(),
        startupMethod -> assertEquals(profileRule, startupMethod.getReference().toSmaliString()));
  }

  @Test
  public void startupProfileFlagMissingParameterTest() {
    String expectedErrorContains =
        isL8() ? "Unknown option: --startup-profile" : "Missing parameter for --startup-profile.";
    try {
      DiagnosticsChecker.checkErrorsContains(
          expectedErrorContains, handler -> parseWithRequiredArgs(handler, "--startup-profile"));
      fail("Expected failure");
    } catch (CompilationFailedException e) {
      // Expected.
    }
  }

  private String[] prepareArgs(String[] args) {
    String[] actualTestArgs;
    String[] additionalTestArgs = requiredArgsForTest();
    if (additionalTestArgs.length > 0) {
      actualTestArgs = new String[args.length + additionalTestArgs.length];
      System.arraycopy(additionalTestArgs, 0, actualTestArgs, 0, additionalTestArgs.length);
      System.arraycopy(args, 0, actualTestArgs, additionalTestArgs.length, args.length);
    } else {
      actualTestArgs = args;
    }
    return actualTestArgs;
  }

  private C parseWithRequiredArgs(String... args) throws CompilationFailedException {
    return parse(prepareArgs(args));
  }

  private C parseWithRequiredArgs(DiagnosticsHandler handler, String... args)
      throws CompilationFailedException {
    return parse(handler, prepareArgs(args));
  }

  protected InternalOptions getOptionsWithLoadedDesugaredLibraryConfiguration(
      C command, boolean libraryCompilation) throws IOException {
    InternalOptions options = command.getInternalOptions();
    LibraryDesugaringSpecification spec = LibraryDesugaringSpecification.JDK11;
    options.loadMachineDesugaredLibrarySpecification(
        Timing.empty(), spec.getAppForTesting(options, libraryCompilation));
    return options;
  }

  /**
   * Tests in this class are executed for all of D8, R8 and L8. When testing arguments this can add
   * additional required arguments to all tests
   */
  abstract String[] requiredArgsForTest();

  abstract C parse(String... args) throws CompilationFailedException;

  abstract C parse(DiagnosticsHandler handler, String... args) throws CompilationFailedException;
}
