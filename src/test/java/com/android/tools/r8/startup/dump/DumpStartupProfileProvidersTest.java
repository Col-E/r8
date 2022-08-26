// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.startup.dump;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DumpStartupProfileProvidersTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() throws Exception {
    Path dump = temp.newFile("dump.zip").toPath();
    try {
      testForR8(Backend.DEX)
          .addProgramClasses(Main.class)
          .addKeepMainRule(Main.class)
          .addOptionsModification(
              options -> options.setDumpInputFlags(DumpInputFlags.dumpToFile(dump)))
          .addStartupProfileProviders(getStartupProfileProviders())
          .allowDiagnosticErrorMessages()
          .setMinApi(AndroidApiLevel.LATEST)
          .compileWithExpectedDiagnostics(
              diagnostics ->
                  diagnostics.assertErrorsMatch(
                      diagnosticMessage(containsString("Dumped compilation inputs to:"))));
      fail("Expected compilation to fail");
    } catch (CompilationFailedException e) {
      // Expected.
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
