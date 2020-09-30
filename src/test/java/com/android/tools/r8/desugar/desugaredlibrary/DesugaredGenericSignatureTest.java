// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.time.LocalDate;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DesugaredGenericSignatureTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;
  private static final String EXPECTED = StringUtils.lines("1970", "1", "2");

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(), BooleanUtils.values());
  }

  public DesugaredGenericSignatureTest(TestParameters parameters, boolean shrinkDesugaredLibrary) {
    this.parameters = parameters;
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
  }

  @Test
  public void testD8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .addInnerClasses(DesugaredGenericSignatureTest.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .setIncludeClassesChecksum(true)
        .compile()
        .inspect(this::checkRewrittenSignature)
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(parameters.getBackend())
        .addInnerClasses(DesugaredGenericSignatureTest.class)
        .addKeepMainRule(Main.class)
        .addKeepAllClassesRuleWithAllowObfuscation()
        .addKeepAttributes(
            ProguardKeepAttributes.SIGNATURE,
            ProguardKeepAttributes.INNER_CLASSES,
            ProguardKeepAttributes.ENCLOSING_METHOD)
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .inspect(this::checkRewrittenSignature)
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  private void checkRewrittenSignature(CodeInspector inspector) {
    if (!requiresEmulatedInterfaceCoreLibDesugaring(parameters)) {
      return;
    }
    ClassSubject javaTimeBox = inspector.clazz(JavaTimeDateBox.class);
    assertThat(javaTimeBox, isPresent());
    ClassSubject box = inspector.clazz(Box.class);
    assertThat(box, isPresent());
    String finalBoxDescriptor = box.getFinalDescriptor();
    assertEquals(
        "Ljava/lang/Object;"
            + finalBoxDescriptor.substring(0, finalBoxDescriptor.length() - 1)
            + "<Lj$/time/LocalDate;>;",
        javaTimeBox.getFinalSignatureAttribute());
  }

  public interface Box<T> {
    T addOne(T t);
  }

  public static class JavaTimeDateBox implements Box<java.time.LocalDate> {

    @Override
    @NeverInline
    public LocalDate addOne(LocalDate localDate) {
      return localDate.plusDays(1);
    }
  }

  public static class Main {

    public static Box<java.time.LocalDate> bar() {
      return new JavaTimeDateBox();
    }

    public static void main(String[] args) {
      LocalDate localDate = bar().addOne(LocalDate.of(1970, 1, 1));
      System.out.println(localDate.getYear());
      System.out.println(localDate.getMonthValue());
      System.out.println(localDate.getDayOfMonth());
    }
  }
}
