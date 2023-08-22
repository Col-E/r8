// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.profile.art.dump;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.DesugaredLibraryTestBuilder;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.profile.art.ArtProfileBuilder;
import com.android.tools.r8.profile.art.ArtProfileProvider;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.DumpInputFlags;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.UTF8TextInputStream;
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
public class DumpArtProfileProvidersTest extends DesugaredLibraryTestBase {

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
    return buildParameters(
        DumpStrategy.values(),
        getTestParameters().withDefaultDexRuntime().withMinimumApiLevel().build());
  }

  @Test
  public void testD8() throws Exception {
    Path dump = dumpStrategy.createDumpPath(temp);
    DumpInputFlags dumpInputFlags = dumpStrategy.createDumpInputFlags(dump);
    try {
      testForD8(parameters.getBackend())
          .addProgramClasses(Main.class)
          .addOptionsModification(options -> options.setDumpInputFlags(dumpInputFlags))
          .apply(this::addArtProfileProviders)
          .setMinApi(parameters)
          .compileWithExpectedDiagnostics(
              diagnostics -> inspectDiagnosticMessages(diagnostics, dumpInputFlags));
      assertFalse("Expected compilation to fail", dumpInputFlags.shouldFailCompilation());
    } catch (CompilationFailedException e) {
      assertTrue("Expected compilation to succeed", dumpInputFlags.shouldFailCompilation());
    }
    inspectDump(dump);
  }

  @Test
  public void testR8() throws Exception {
    Path dump = dumpStrategy.createDumpPath(temp);
    DumpInputFlags dumpInputFlags = dumpStrategy.createDumpInputFlags(dump);
    try {
      testForR8(parameters.getBackend())
          .addProgramClasses(Main.class)
          .addKeepMainRule(Main.class)
          .addOptionsModification(options -> options.setDumpInputFlags(dumpInputFlags))
          .allowDiagnosticInfoMessages()
          .apply(this::addArtProfileProviders)
          .setMinApi(parameters)
          .compileWithExpectedDiagnostics(
              diagnostics -> inspectDiagnosticMessages(diagnostics, dumpInputFlags));
      assertFalse("Expected compilation to fail", dumpInputFlags.shouldFailCompilation());
    } catch (CompilationFailedException e) {
      assertTrue("Expected compilation to succeed", dumpInputFlags.shouldFailCompilation());
    }
    inspectDump(dump);
  }

  @Test
  public void testL8() throws Exception {
    Path dump = dumpStrategy.createDumpPath(temp);
    DumpInputFlags dumpInputFlags = dumpStrategy.createDumpInputFlags(dump);
    CompilationSpecification compilationSpecification = CompilationSpecification.D8_L8SHRINK;
    LibraryDesugaringSpecification libraryDesugaringSpecification =
        LibraryDesugaringSpecification.JDK11;
    try {
      testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
          .addProgramClasses(Main.class)
          .addKeepMainRule(Main.class)
          .addL8OptionsModification(options -> options.setDumpInputFlags(dumpInputFlags))
          .apply(this::addArtProfileProviders)
          .compile()
          .inspectL8DiagnosticMessages(
              diagnostics -> inspectDiagnosticMessages(diagnostics, dumpInputFlags));
      assertFalse("Expected compilation to fail", dumpInputFlags.shouldFailCompilation());
    } catch (CompilationFailedException e) {
      assertTrue("Expected compilation to succeed", dumpInputFlags.shouldFailCompilation());
    }
    inspectDump(dump);
  }

  private void addArtProfileProviders(D8TestBuilder testBuilder) {
    getArtProfileProviders().forEach(testBuilder::addArtProfileForRewriting);
  }

  private void addArtProfileProviders(R8FullTestBuilder testBuilder) {
    getArtProfileProviders().forEach(testBuilder::addArtProfileForRewriting);
  }

  private void addArtProfileProviders(DesugaredLibraryTestBuilder<?> testBuilder) {
    getArtProfileProviders().forEach(testBuilder::addL8ArtProfileForRewriting);
  }

  private Collection<ArtProfileProvider> getArtProfileProviders() {
    return ImmutableList.of(
        new ArtProfileProvider() {

          @Override
          public void getArtProfile(ArtProfileBuilder profileBuilder) {
            profileBuilder.addHumanReadableArtProfile(
                new UTF8TextInputStream(StringUtils.joinLines("# Comment", "Lfoo/Bar;")),
                parserBuilder -> {});
            ClassReference bazClassReference = Reference.classFromDescriptor("Lfoo/Baz;");
            MethodReference bazMainMethodReference =
                MethodReferenceUtils.mainMethod(bazClassReference);
            profileBuilder.addClassRule(
                classRuleBuilder -> classRuleBuilder.setClassReference(bazClassReference));
            profileBuilder.addMethodRule(
                methodRuleBuilder ->
                    methodRuleBuilder
                        .setMethodReference(bazMainMethodReference)
                        .setMethodRuleInfo(
                            methodRuleInfoBuilder -> methodRuleInfoBuilder.setIsHot(true)));
          }

          @Override
          public Origin getOrigin() {
            return Origin.unknown();
          }
        },
        new ArtProfileProvider() {

          @Override
          public void getArtProfile(ArtProfileBuilder profileBuilder) {
            ClassReference bazClassReference = Reference.classFromDescriptor("Lfoo/Baz;");
            MethodReference bazMainMethodReference =
                MethodReferenceUtils.mainMethod(bazClassReference);
            profileBuilder.addClassRule(
                classRuleBuilder -> classRuleBuilder.setClassReference(bazClassReference));
            profileBuilder.addHumanReadableArtProfile(
                new UTF8TextInputStream(StringUtils.joinLines("# Comment", "Lfoo/Bar;")),
                parserBuilder -> {});
            profileBuilder.addMethodRule(
                methodRuleBuilder ->
                    methodRuleBuilder
                        .setMethodReference(bazMainMethodReference)
                        .setMethodRuleInfo(
                            methodRuleInfoBuilder -> methodRuleInfoBuilder.setIsHot(true)));
          }

          @Override
          public Origin getOrigin() {
            return Origin.unknown();
          }
        });
  }

  private void inspectDiagnosticMessages(
      TestDiagnosticMessages diagnostics, DumpInputFlags dumpInputFlags) {
    if (dumpInputFlags.shouldFailCompilation()) {
      diagnostics.assertErrorsMatch(
          diagnosticMessage(containsString("Dumped compilation inputs to:")));
    } else {
      diagnostics.assertInfosMatch(
          diagnosticMessage(containsString("Dumped compilation inputs to:")));
    }
  }

  private void inspectDump(Path dump) throws IOException {
    if (dumpStrategy == DumpStrategy.DIRECTORY) {
      List<Path> dumps =
          Files.walk(dump, 1).filter(path -> path.toFile().isFile()).collect(Collectors.toList());
      assertEquals(1, dumps.size());
      dump = dumps.get(0);
    }

    assertTrue(Files.exists(dump));
    Path unzipped = temp.newFolder().toPath();
    ZipUtils.unzip(dump.toString(), unzipped.toFile());

    Path artProfile1 = unzipped.resolve("art-profile-1.txt");
    assertTrue(Files.exists(artProfile1));
    assertEquals(
        Lists.newArrayList(
            "# Comment", "Lfoo/Bar;", "Lfoo/Baz;", "HLfoo/Baz;->main([Ljava/lang/String;)V"),
        FileUtils.readAllLines(artProfile1));

    Path artProfile2 = unzipped.resolve("art-profile-2.txt");
    assertTrue(Files.exists(artProfile2));
    assertEquals(
        Lists.newArrayList(
            "Lfoo/Baz;", "# Comment", "Lfoo/Bar;", "HLfoo/Baz;->main([Ljava/lang/String;)V"),
        FileUtils.readAllLines(artProfile2));
  }

  static class Main {

    public static void main(String[] args) {}
  }
}
