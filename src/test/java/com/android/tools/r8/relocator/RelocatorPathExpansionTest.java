// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.relocator;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.relocator.foo.Base;
import com.android.tools.r8.relocator.foo.bar.Baz;
import com.android.tools.r8.relocator.foo.bar.BazImpl;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.util.List;
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
    ClassReference destination =
        Reference.classFromDescriptor("Lcom/android/tools/r8/foo/bar/Baz;");
    testForRelocator(external)
        .addProgramClassFileData(
            Baz.dump(),
            BazImpl.dump(),
            transformer(Base.class).setClassDescriptor("Lfoo/Base;").transform())
        .addPackageAndAllSubPackagesMapping("foo", "com.android.tools.r8.foo")
        .addClassMapping(
            destination, Reference.classFromDescriptor("Lcom/android/tools/r8/foo/bar/Baz;"))
        .run()
        .inspect(
            inspector -> {
              assertThat(inspector.clazz("com.android.tools.r8.foo.Base"), isPresent());
              assertThat(inspector.clazz(destination), isPresent());
              assertThat(inspector.clazz("com.android.tools.r8.foo.bar.BazImpl"), isPresent());
            });
  }

  @Test
  public void rewriteTopLevelClass() throws Exception {
    ClassReference base = Reference.classFromDescriptor("LBase;");
    ClassReference destination = Reference.classFromDescriptor("Lcom/android/tools/r8/Base;");
    testForRelocator(external)
        .addProgramClassFileData(
            Baz.dump(),
            BazImpl.dump(),
            transformer(Base.class).setClassDescriptor(base.getDescriptor()).transform())
        .addClassMapping(base, destination)
        .run()
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(destination), isPresent());
              // Assert that we did not rename a class in a package that is under root.
              ClassSubject relocatedBaz = inspector.clazz("foo.bar.Baz");
              assertThat(relocatedBaz, isPresent());
            });
  }

  @Test
  public void rewriteSingleClassDifferentlyFromPackage() throws Exception {
    ClassReference destination =
        Reference.classFromDescriptor("Lcom/android/tools/r8/foo1/bar/Baz;");
    String destinationPackage = "com.android.tools.r8.foo2.bar";
    testForRelocator(external)
        .addProgramClassFileData(Baz.dump(), BazImpl.dump())
        .addPackageAndAllSubPackagesMapping("foo.bar", destinationPackage)
        .addClassMapping(Reference.classFromDescriptor("Lfoo/bar/Baz;"), destination)
        .run()
        .inspect(
            inspector -> {
              // Relocation of specific classes should take precedence over relocation of packages.
              assertThat(inspector.clazz(destination), isPresent());
              assertThat(inspector.clazz(destinationPackage + ".BazImpl"), isPresent());
            });
  }

  @Test
  public void rewriteAllSubPackages() throws Exception {
    testForRelocator(external)
        .addProgramClassFileData(Baz.dump(), BazImpl.dump())
        .addPackageAndAllSubPackagesMapping("foo", "com.android.tools.r8.foo")
        .run()
        .inspect(
            inspector ->
                inspector
                    .allClasses()
                    .forEach(
                        clazz ->
                            assertThat(
                                clazz.getFinalName(), startsWith("com.android.tools.r8.foo"))));
  }
}
