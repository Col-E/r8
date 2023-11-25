// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.startup.dump;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.StartupClassesNonStartupFractionDiagnostic;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.startup.StartupProfileBuilder;
import com.android.tools.r8.startup.StartupProfileProvider;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DumpInputFlags;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DumpStartupProfileProvidersTest extends TestBase {

  private enum DumpStrategy {
    DIRECTORY,
    FILE;

    DumpInputFlags createDumpInputFlags(Path dump) {
      if (this == DIRECTORY) {
        return DumpInputFlags.dumpToDirectory(dump);
      }
      assert this == FILE;
      return DumpInputFlags.dumpToFile(dump);
    }

    Path createDumpPath(TemporaryFolder temp) throws IOException {
      if (this == DIRECTORY) {
        return temp.newFolder().toPath();
      }
      assert this == FILE;
      return temp.newFile("dump.zip").toPath();
    }
  }

  @Parameter(0)
  public DumpStrategy dumpStrategy;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, {0}")
  public static List<Object[]> data() {
    return buildParameters(DumpStrategy.values(), getTestParameters().withNoneRuntime().build());
  }

  @Test
  public void test() throws Exception {
    Path dump = dumpStrategy.createDumpPath(temp);
    DumpInputFlags dumpInputFlags = dumpStrategy.createDumpInputFlags(dump);
    try {
      testForR8(Backend.DEX)
          .addProgramClasses(Main.class)
          .addKeepMainRule(Main.class)
          .addOptionsModification(options -> options.setDumpInputFlags(dumpInputFlags))
          .addStartupProfileProviders(getStartupProfileProviders())
          .allowDiagnosticInfoMessages()
          .setMinApi(AndroidApiLevel.LATEST)
          .compileWithExpectedDiagnostics(
              diagnostics -> {
                if (dumpInputFlags.shouldFailCompilation()) {
                  diagnostics.assertErrorsMatch(
                      diagnosticMessage(containsString("Dumped compilation inputs to:")));
                } else {
                  diagnostics.assertInfosMatch(
                      diagnosticMessage(containsString("Dumped compilation inputs to:")),
                      diagnosticType(StartupClassesNonStartupFractionDiagnostic.class));
                }
              });
      assertFalse("Expected compilation to fail", dumpInputFlags.shouldFailCompilation());
    } catch (CompilationFailedException e) {
      assertTrue("Expected compilation to succeed", dumpInputFlags.shouldFailCompilation());
    }
    verifyDump(dump);
  }

  private Collection<StartupProfileProvider> getStartupProfileProviders() {
    return ImmutableList.of(
        new StartupProfileProvider() {

          @Override
          public void getStartupProfile(StartupProfileBuilder startupProfileBuilder) {
            startupProfileBuilder.addStartupClass(
                startupClassBuilder ->
                    startupClassBuilder.setClassReference(Reference.classFromClass(Main.class)));
          }

          @Override
          public Origin getOrigin() {
            return Origin.unknown();
          }
        },
        new StartupProfileProvider() {

          @Override
          public void getStartupProfile(StartupProfileBuilder startupProfileBuilder) {
            startupProfileBuilder.addStartupMethod(
                startupMethodBuilder ->
                    startupMethodBuilder.setMethodReference(
                        MethodReferenceUtils.mainMethod(Main.class)));
          }

          @Override
          public Origin getOrigin() {
            return Origin.unknown();
          }
        });
  }

  private void verifyDump(Path dump) throws IOException {
    if (dumpStrategy == DumpStrategy.DIRECTORY) {
      List<Path> dumps =
          Files.walk(dump, 1).filter(path -> path.toFile().isFile()).collect(Collectors.toList());
      assertEquals(1, dumps.size());
      dump = dumps.get(0);
    }

    assertTrue(Files.exists(dump));
    Path unzipped = temp.newFolder().toPath();
    ZipUtils.unzip(dump.toString(), unzipped.toFile());

    Path startupProfile1 = unzipped.resolve("startup-profile-1.txt");
    assertTrue(Files.exists(startupProfile1));
    assertEquals(
        Lists.newArrayList(Reference.classFromClass(Main.class).getDescriptor()),
        FileUtils.readAllLines(startupProfile1));

    Path startupProfile2 = unzipped.resolve("startup-profile-2.txt");
    assertTrue(Files.exists(startupProfile2));
    assertEquals(
        Lists.newArrayList(
            MethodReferenceUtils.toSmaliString(MethodReferenceUtils.mainMethod(Main.class))),
        FileUtils.readAllLines(startupProfile2));
  }

  static class Main {

    public static void main(String[] args) {}
  }
}
