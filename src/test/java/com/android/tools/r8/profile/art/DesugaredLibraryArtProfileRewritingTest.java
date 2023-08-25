// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.model.ExternalArtProfileMethodRule;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AbortException;
import com.android.tools.r8.utils.codeinspector.AssertUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DesugaredLibraryArtProfileRewritingTest extends DesugaredLibraryTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public CompilationSpecification compilationSpecification;

  @Parameter(2)
  public LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        DEFAULT_SPECIFICATIONS,
        getJdk8Jdk11());
  }

  @Test
  public void test() throws Throwable {
    Assume.assumeTrue(libraryDesugaringSpecification.hasEmulatedInterfaceDesugaring(parameters));
    AssertUtils.assertFailsCompilationIf(
        !compilationSpecification.isL8Shrink(),
        () ->
            testForDesugaredLibrary(
                    parameters, libraryDesugaringSpecification, compilationSpecification)
                .addInnerClasses(getClass())
                .addKeepMainRule(Main.class)
                .addL8ArtProfileForRewriting(getArtProfile())
                .compile()
                .inspectL8ResidualArtProfile(this::inspect)
                .run(parameters.getRuntime(), Main.class)
                .assertSuccessWithOutputLines("0"),
        exception -> {
          assertEquals(CompilationFailedException.class, exception.getClass());
          assertThat(exception.getMessage(), equalTo("Compilation failed to complete"));
          assertNotNull(exception.getCause());
          assertEquals(AbortException.class, exception.getCause().getClass());
          assertThat(
              exception.getCause().getMessage(),
              containsString(
                  "L8 does not impact ART profiles when generating DEX and not shrinking"));
        });
  }

  private ExternalArtProfile getArtProfile() {
    MethodReference forEachMethodReference =
        Reference.method(
            Reference.classFromTypeName("j$.util.stream.Stream"),
            "forEach",
            ImmutableList.of(
                Reference.classFromTypeName(
                    libraryDesugaringSpecification.functionPrefix(parameters)
                        + ".util.function.Consumer")),
            null);
    return ExternalArtProfile.builder()
        .addRule(
            ExternalArtProfileMethodRule.builder()
                .setMethodReference(forEachMethodReference)
                .setMethodRuleInfo(ArtProfileMethodRuleInfoImpl.builder().setIsStartup().build())
                .build())
        .build();
  }

  private void inspect(ArtProfileInspector profileInspector, CodeInspector inspector) {
    ClassSubject consumerClassSubject =
        inspector.clazz(
            libraryDesugaringSpecification.functionPrefix(parameters) + ".util.function.Consumer");
    assertThat(consumerClassSubject, isPresent());

    ClassSubject streamClassSubject = inspector.clazz("j$.util.stream.Stream");
    assertThat(streamClassSubject, isPresentAndNotRenamed());

    MethodSubject forEachMethodSubject = streamClassSubject.uniqueMethodWithOriginalName("forEach");
    assertThat(
        forEachMethodSubject,
        isPresentAndRenamed(
            compilationSpecification.isL8Shrink()
                && libraryDesugaringSpecification == LibraryDesugaringSpecification.JDK8));
    assertEquals(consumerClassSubject.asTypeSubject(), forEachMethodSubject.getParameter(0));

    profileInspector.assertContainsMethodRule(forEachMethodSubject).assertContainsNoOtherRules();
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(new ArrayList<>().stream().collect(Collectors.toList()).size());
    }
  }
}
