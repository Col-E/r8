// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.dexinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsChecker;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.ArchiveEntryOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.dexinspector.DexInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharSource;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

class A {
  private static String buildClassName(String className) {
    return A.class.getPackage().getName() + "." + className;
  }

  public static void main(String[] args) {
    try {
      Class bClass = Class.forName(buildClassName("B"));
      System.out.println("YES");
    } catch (ClassNotFoundException e) {
      System.out.println("NO");
    }
  }
}

class B {

}

@RunWith(Parameterized.class)
public class LibraryProvidedProguardRulesTest extends TestBase {

  private Backend backend;

  @Parameters(name = "Backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }

  public LibraryProvidedProguardRulesTest(Backend backend) {
    this.backend = backend;
  }

  private void addTextJarEntry(JarOutputStream out, String name, String content) throws Exception {
    out.putNextEntry(new ZipEntry(name));
    ByteStreams.copy(
        CharSource.wrap(content).asByteSource(StandardCharsets.UTF_8).openBufferedStream(), out);
    out.closeEntry();
  }

  private AndroidApp runTest(List<String> rules, DiagnosticsHandler handler) throws Exception {
    Path jar = temp.newFile("test.jar").toPath();
    try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar.toFile()))) {
      addTestClassesToJar(out, A.class, B.class);
      for (int i =  0; i < rules.size(); i++) {
        String name = "META-INF/proguard/jar" + (i == 0 ? "" : i) + ".rules";
        addTextJarEntry(out, name, rules.get(i));
      }
    }

    try {
      R8Command.Builder builder =
          (handler != null ? R8Command.builder(handler) : R8Command.builder()).addProgramFiles(jar);
      if (backend == Backend.DEX) {
        builder
            .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
            .addLibraryFiles(ToolHelper.getDefaultAndroidJar());
      } else {
        assert backend == Backend.CF;
        builder
            .setProgramConsumer(ClassFileConsumer.emptyConsumer())
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar());
      }
      return ToolHelper.runR8(builder.build());
    } catch (CompilationFailedException e) {
      assertNotNull(handler);
      return null;
    }
  }

  private AndroidApp runTest(String rules, DiagnosticsHandler handler) throws Exception {
    return runTest(ImmutableList.of(rules), handler);
  }


  @Test
  public void keepOnlyA() throws Exception {
    AndroidApp app = runTest("-keep class " + A.class.getTypeName() +" {}", null);
    DexInspector inspector = new DexInspector(app);
    assertThat(inspector.clazz(A.class), isPresent());
    assertThat(inspector.clazz(B.class), not(isPresent()));
  }

  @Test
  public void keepOnlyB() throws Exception {
    AndroidApp app = runTest("-keep class **B {}", null);
    DexInspector inspector = new DexInspector(app);
    assertThat(inspector.clazz(A.class), not(isPresent()));
    assertThat(inspector.clazz(B.class), isPresent());
  }

  @Test
  public void keepBoth() throws Exception {
    AndroidApp app = runTest("-keep class ** {}", null);
    DexInspector inspector = new DexInspector(app);
    assertThat(inspector.clazz(A.class), isPresent());
    assertThat(inspector.clazz(B.class), isPresent());
  }

  @Test
  public void multipleFiles() throws Exception {
    AndroidApp app = runTest(ImmutableList.of("-keep class **A {}", "-keep class **B {}"), null);
    DexInspector inspector = new DexInspector(app);
    assertThat(inspector.clazz(A.class), isPresent());
    assertThat(inspector.clazz(B.class), isPresent());
  }

  private void checkOrigin(Origin origin) {
    assertTrue(origin instanceof ArchiveEntryOrigin);
    assertEquals(origin.part(), "META-INF/proguard/jar.rules");
    assertTrue(origin.parent() instanceof PathOrigin);
  }

  @Test
  public void syntaxError() throws Exception {
    DiagnosticsChecker checker = new DiagnosticsChecker();
    AndroidApp app = runTest("error", checker);
    assertNull(app);
    DiagnosticsChecker.checkDiagnostic(
        checker.errors.get(0), this::checkOrigin, 1, 1, "Expected char '-'");
  }

  @Test
  public void includeError() throws Exception {
    DiagnosticsChecker checker = new DiagnosticsChecker();
    AndroidApp app = runTest("-include other.rules", checker);
    assertNull(app);
    DiagnosticsChecker.checkDiagnostic(checker.errors.get(0), this::checkOrigin, 1, 10,
        "Options with file names are not supported");
  }

  class TestProvider implements ProgramResourceProvider, DataResourceProvider {

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
  public void throwingDataResourceProvider() throws Exception {
    DiagnosticsChecker checker = new DiagnosticsChecker();
    try {
      R8Command.Builder builder =
          R8Command.builder(checker).addProgramResourceProvider(new TestProvider());
      if (backend == Backend.DEX) {
        builder
            .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
            .addLibraryFiles(ToolHelper.getDefaultAndroidJar());
      } else {
        assert backend == Backend.CF;
        builder
            .setProgramConsumer(ClassFileConsumer.emptyConsumer())
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar());
      }
      builder.build();
      fail("Should not succeed");
    } catch (CompilationFailedException e) {
      DiagnosticsChecker.checkDiagnostic(
          checker.errors.get(0),
          origin -> assertSame(origin, Origin.unknown()),
          "Cannot provide data resources after all");
    }
  }
}
