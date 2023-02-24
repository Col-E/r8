// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticOrigin;
import static com.android.tools.r8.OriginMatcher.hasPart;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.origin.ArchiveEntryOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibraryProvidedProguardRulesTest extends LibraryProvidedProguardRulesTestBase {

  static class A {
    private static String buildClassName(String className) {
      return A.class.getPackage().getName() + "." + className;
    }

    public static void main(String[] args) {
      try {
        Class.forName(buildClassName("B"));
        System.out.println("YES");
      } catch (ClassNotFoundException e) {
        System.out.println("NO");
      }
    }
  }

  static class B {}

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public LibraryType libraryType;

  @Parameter(2)
  public ProviderType providerType;

  @Parameters(name = "{0}, AAR: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build(),
        LibraryType.values(),
        ProviderType.values());
  }

  private Path buildLibrary(List<String> rules) throws Exception {
    ZipBuilder jarBuilder =
        ZipBuilder.builder(temp.newFile(libraryType.isAar() ? "classes.jar" : "test.jar").toPath());
    addTestClassesToZip(jarBuilder.getOutputStream(), ImmutableList.of(A.class, B.class));
    if (libraryType.hasRulesInJar()) {
      for (int i = 0; i < rules.size(); i++) {
        String name = "META-INF/proguard/jar" + (i == 0 ? "" : i) + ".rules";
        jarBuilder.addText(name, rules.get(i));
      }
    }
    if (libraryType.isAar()) {
      Path jar = jarBuilder.build();
      String allRules = StringUtils.lines(rules);
      ZipBuilder aarBuilder = ZipBuilder.builder(temp.newFile("test.aar").toPath());
      aarBuilder.addFilesRelative(jar.getParent(), jar);
      if (libraryType.hasRulesInAar()) {
        aarBuilder.addText("proguard.txt", allRules);
      }
      return aarBuilder.build();
    } else {
      return jarBuilder.build();
    }
  }

  private CodeInspector runTest(List<String> rules) throws Exception {
    Path library = buildLibrary(rules);
    return testForR8(parameters.getBackend())
        .applyIf(providerType == ProviderType.API, b -> b.addProgramFiles(library))
        .applyIf(providerType == ProviderType.INJARS, b -> b.addKeepRules("-injars " + library))
        .setMinApi(parameters)
        .compile()
        .inspector();
  }

  private CodeInspector runTest(String rules) throws Exception {
    return runTest(ImmutableList.of(rules));
  }

  @Test
  public void keepOnlyA() throws Exception {
    CodeInspector inspector = runTest("-keep class " + A.class.getTypeName() + " {}");
    // TODO(b/228319861): Read Proguard rules from AAR's.
    assertThat(inspector.clazz(A.class), notIf(isPresent(), libraryType.isAar()));
    assertThat(inspector.clazz(B.class), not(isPresent()));
  }

  @Test
  public void keepOnlyB() throws Exception {
    CodeInspector inspector = runTest("-keep class **B {}");
    assertThat(inspector.clazz(A.class), not(isPresent()));
    // TODO(b/228319861): Read Proguard rules from AAR's.
    assertThat(inspector.clazz(B.class), notIf(isPresent(), libraryType.isAar()));
  }

  @Test
  public void keepBoth() throws Exception {
    CodeInspector inspector = runTest("-keep class ** {}");
    // TODO(b/228319861): Read Proguard rules from AAR's.
    assertThat(inspector.clazz(A.class), notIf(isPresent(), libraryType.isAar()));
    assertThat(inspector.clazz(B.class), notIf(isPresent(), libraryType.isAar()));
  }

  @Test
  public void multipleFiles() throws Exception {
    CodeInspector inspector = runTest(ImmutableList.of("-keep class **A {}", "-keep class **B {}"));
    // TODO(b/228319861): Read Proguard rules from AAR's.
    assertThat(inspector.clazz(A.class), notIf(isPresent(), libraryType.isAar()));
    assertThat(inspector.clazz(B.class), notIf(isPresent(), libraryType.isAar()));
  }

  @Test
  public void syntaxError() {
    // TODO(b/228319861): Read Proguard rules from AAR's.
    assumeTrue(!libraryType.isAar());
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addProgramFiles(buildLibrary(ImmutableList.of("error")))
                .setMinApi(parameters)
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorThatMatches(
                            allOf(
                                diagnosticMessage(containsString("Expected char '-'")),
                                diagnosticOrigin(hasPart("META-INF/proguard/jar.rules")),
                                diagnosticOrigin(instanceOf(ArchiveEntryOrigin.class))))));
  }

  @Test
  public void includeError() {
    // TODO(b/228319861): Read Proguard rules from AAR's.
    assumeTrue(!libraryType.isAar());
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addProgramFiles(buildLibrary(ImmutableList.of("-include other.rules")))
                .setMinApi(parameters)
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorThatMatches(
                            diagnosticMessage(
                                containsString("Options with file names are not supported")))));
  }

  static class TestProvider implements ProgramResourceProvider, DataResourceProvider {

    @Override
    public Collection<ProgramResource> getProgramResources() throws ResourceException {
      byte[] bytes;
      try {
        bytes = ByteStreams.toByteArray(A.class.getResourceAsStream("A.class"));
      } catch (IOException e) {
        throw new ResourceException(Origin.unknown(), "Unexpected");
      }
      return ImmutableList.of(
          ProgramResource.fromBytes(Origin.unknown(), Kind.CF, bytes,
              Collections.singleton(DescriptorUtils.javaTypeToDescriptor(A.class.getTypeName()))));
    }

    @Override
    public DataResourceProvider getDataResourceProvider() {
      return this;
    }

    @Override
    public void accept(Visitor visitor) throws ResourceException {
      throw new ResourceException(Origin.unknown(), "Cannot provide data resources after all");
    }
  }

  @Test
  public void throwingDataResourceProvider() {
    // TODO(b/228319861): Read Proguard rules from AAR's.
    assumeTrue(!libraryType.isAar());
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addProgramResourceProviders(new TestProvider())
                .setMinApi(parameters)
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorThatMatches(
                            allOf(
                                diagnosticMessage(
                                    containsString("Cannot provide data resources after all")),
                                diagnosticOrigin(is(Origin.unknown()))))));
  }
}
