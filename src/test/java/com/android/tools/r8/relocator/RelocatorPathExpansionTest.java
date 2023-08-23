// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.relocator;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.relocator.foo.Base;
import com.android.tools.r8.relocator.foo.bar.Baz;
import com.android.tools.r8.relocator.foo.bar.BazImpl;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RelocatorPathExpansionTest extends TestBase {

  private final boolean external;

  @Parameters(name = "{0}, external: {1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), BooleanUtils.values());
  }

  public RelocatorPathExpansionTest(TestParameters testParameters, boolean external) {
    testParameters.assertNoneRuntime();
    this.external = external;
  }

  @Test
  public void testRewritingSingleClassWithSubPackageMatch() throws Exception {
    Path testJarPath = temp.newFile("test.jar").toPath();
    writeClassesToPath(
        testJarPath,
        Baz.dump(),
        BazImpl.dump(),
        transformer(Base.class).setClassDescriptor("Lfoo/Base;").transform());
    Path relocatedJar = temp.newFile("out.jar").toPath();
    ClassReference destination =
        Reference.classFromDescriptor("Lcom/android/tools/r8/foo/bar/Baz;");
    Map<String, String> mapping = new LinkedHashMap<>();
    mapping.put("foo", "com.android.tools.r8.foo");
    mapping.put("Lfoo/bar/Baz;", destination.getDescriptor());
    RelocatorUtils.runRelocator(testJarPath, mapping, relocatedJar, external);
    CodeInspector inspector = new CodeInspector(relocatedJar);
    assertThat(inspector.clazz("com.android.tools.r8.foo.Base"), isPresent());
    assertThat(inspector.clazz(destination), isPresent());
    assertThat(inspector.clazz("com.android.tools.r8.foo.bar.BazImpl"), isPresent());
  }

  @Test
  public void rewriteTopLevelClass() throws Exception {
    Path testJarPath = temp.newFile("test.jar").toPath();
    writeClassesToPath(
        testJarPath,
        Baz.dump(),
        BazImpl.dump(),
        transformer(Base.class).setClassDescriptor("LBase;").transform());
    Path relocatedJar = temp.newFile("out.jar").toPath();
    ClassReference destination = Reference.classFromDescriptor("Lcom/android/tools/r8/Base;");
    Map<String, String> mapping = new LinkedHashMap<>();
    mapping.put("LBase;", destination.getDescriptor());
    RelocatorUtils.runRelocator(testJarPath, mapping, relocatedJar, external);
    CodeInspector inspector = new CodeInspector(relocatedJar);
    assertThat(inspector.clazz(destination), isPresent());
    // Assert that we did not rename a class in a package that is under root.
    ClassSubject relocatedBaz = inspector.clazz("foo.bar.Baz");
    assertThat(relocatedBaz, isPresent());
  }

  @Test
  public void rewriteSingleClassDifferentlyFromPackage() throws Exception {
    Path testJarPath = temp.newFile("test.jar").toPath();
    writeClassesToPath(testJarPath, Baz.dump(), BazImpl.dump());
    Path relocatedJar = temp.newFile("out.jar").toPath();
    ClassReference destination =
        Reference.classFromDescriptor("Lcom/android/tools/r8/foo1/bar/Baz;");
    Map<String, String> mapping = new LinkedHashMap<>();
    mapping.put("foo.bar", "com.android.tools.r8.foo2.bar");
    // Relocation of specific classes should take precedence over relocation of packages.
    mapping.put("Lfoo/bar/Baz;", destination.getDescriptor());
    RelocatorUtils.runRelocator(testJarPath, mapping, relocatedJar, external);
    CodeInspector inspector = new CodeInspector(relocatedJar);
    assertThat(inspector.clazz(destination), isPresent());
    assertThat(inspector.clazz("com.android.tools.r8.foo2.bar.BazImpl"), isPresent());
  }

  @Test
  public void rewriteAllSubPackages() throws Exception {
    Path testJarPath = temp.newFile("test.jar").toPath();
    writeClassesToPath(testJarPath, Baz.dump(), BazImpl.dump());
    Path relocatedJar = temp.newFile("out.jar").toPath();
    Map<String, String> mapping = new LinkedHashMap<>();
    // The language as input is very simple and ** to match sub-packages would be a feature request.
    mapping.put("foo/**", "com.android.tools.r8.foo");
    assertThrows(
        IllegalArgumentException.class,
        () -> RelocatorUtils.runRelocator(testJarPath, mapping, relocatedJar, external));
  }

  private void writeClassesToPath(Path inputJar, byte[]... classes) {
    ClassFileConsumer inputConsumer = new ClassFileConsumer.ArchiveConsumer(inputJar);
    for (byte[] clazz : classes) {
      inputConsumer.accept(ByteDataView.of(clazz), extractClassDescriptor(clazz), null);
    }
    inputConsumer.finished(null);
  }
}
