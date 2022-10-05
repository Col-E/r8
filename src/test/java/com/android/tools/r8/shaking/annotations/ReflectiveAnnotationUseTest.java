// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.annotations;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.AnnotationSubject;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ReflectiveAnnotationUseTest extends KotlinTestBase {
  private static final String FOLDER = "internal_annotation";
  private static final String MAIN_CLASS_NAME = "internal_annotation.MainKt";
  private static final String ANNOTATION_NAME = "internal_annotation.Annotation";
  private static final String IMPL_CLASS_NAME = "internal_annotation.Impl";
  private static final String KEEP_ANNOTATIONS = "-keepattributes *Annotation*";

  private static final String JAVA_OUTPUT = StringUtils.lines(
      "Impl::toString",
      "Impl::Annotation::field2.Impl::Annotation::field2(Impl::Annotation::field2:2)"
  );

  private static final String OUTPUT_WITHOUT_ANNOTATION = StringUtils.lines(
      "Impl::toString",
      "null"
  );

  private static final Map<String, String> EXPECTED_ANNOTATION_VALUES = ImmutableMap.of(
      "f1", "2",
      "f2", "Impl::Annotation::field2",
      "f3", "3]",
      "f4", "field4]"
  );

  private final TestParameters parameters;
  private final boolean minify;

  @Parameterized.Parameters(name = "{0}, {1}, minify: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build(),
        BooleanUtils.values());
  }

  public ReflectiveAnnotationUseTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters, boolean minify) {
    super(kotlinParameters);
    this.parameters = parameters;
    this.minify = minify;
  }

  private static final KotlinCompileMemoizer compiledJars =
      getCompileMemoizer(getKotlinFilesInResource(FOLDER), FOLDER)
          .configure(kotlinCompilerTool -> kotlinCompilerTool.includeRuntime().noReflect());

  @Test
  public void b120951621_JVMOutput() throws Exception {
    assumeTrue("Only run JVM reference on CF runtimes", parameters.isCfRuntime());
    AndroidApp app =
        AndroidApp.builder()
            .addProgramFile(compiledJars.getForConfiguration(kotlinc, targetVersion))
            .addProgramFile(getJavaJarFile(FOLDER))
            .build();
    String result = runOnJava(app, MAIN_CLASS_NAME);
    assertEquals(JAVA_OUTPUT, result);
  }

  @Test
  public void b120951621_keepAll() throws Exception {
    CodeInspector inspector =
        testForR8Compat(parameters.getBackend())
            .addProgramFiles(
                compiledJars.getForConfiguration(kotlinc, targetVersion),
                kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(getJavaJarFile(FOLDER))
            .addKeepMainRule(MAIN_CLASS_NAME)
            .addKeepRules(KEEP_ANNOTATIONS)
            .addKeepRules("-keep @interface " + ANNOTATION_NAME + " {", "  *;", "}")
            .allowDiagnosticWarningMessages()
            .minification(minify)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .assertAllWarningMessagesMatch(
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
            .run(parameters.getRuntime(), MAIN_CLASS_NAME)
            .assertSuccessWithOutput(JAVA_OUTPUT)
            .inspector();
    ClassSubject clazz = inspector.clazz(ANNOTATION_NAME);
    assertThat(clazz, isPresentAndNotRenamed());
    MethodSubject f1 = clazz.uniqueMethodWithOriginalName("f1");
    assertThat(f1, isPresentAndNotRenamed());
    MethodSubject f2 = clazz.uniqueMethodWithOriginalName("f2");
    assertThat(f2, isPresentAndNotRenamed());
    MethodSubject f3 = clazz.uniqueMethodWithOriginalName("f3");
    assertThat(f3, isPresentAndNotRenamed());
    MethodSubject f4 = clazz.uniqueMethodWithOriginalName("f4");
    assertThat(f4, isPresentAndNotRenamed());

    ClassSubject impl = inspector.clazz(IMPL_CLASS_NAME);
    assertThat(impl, isPresent());
    AnnotationSubject anno = impl.annotation(ANNOTATION_NAME);
    assertThat(anno, isPresent());
    inspectAnnotationInstantiation(anno, ImmutableSet.of("f1", "f2", "f3", "f4"));
  }

  @Test
  public void b120951621_partiallyKeep() throws Exception {
    CodeInspector inspector =
        testForR8Compat(parameters.getBackend())
            .addProgramFiles(
                compiledJars.getForConfiguration(kotlinc, targetVersion),
                kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(getJavaJarFile(FOLDER))
            .addKeepMainRule(MAIN_CLASS_NAME)
            .addKeepRules(KEEP_ANNOTATIONS)
            .addKeepRules(
                "-keep,allowobfuscation @interface " + ANNOTATION_NAME + " {",
                "  java.lang.String *f2();",
                "}")
            .allowDiagnosticWarningMessages()
            .minification(minify)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .assertAllWarningMessagesMatch(
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
            .run(parameters.getRuntime(), MAIN_CLASS_NAME)
            .assertSuccessWithOutput(JAVA_OUTPUT)
            .inspector();
    ClassSubject clazz = inspector.clazz(ANNOTATION_NAME);
    assertThat(clazz, isPresent());
    assertEquals(minify, clazz.isRenamed());
    MethodSubject f1 = clazz.uniqueMethodWithOriginalName("f1");
    assertThat(f1, isPresentAndNotRenamed());
    MethodSubject f2 = clazz.uniqueMethodWithOriginalName("f2");
    assertThat(f2, isPresentAndNotRenamed());
    MethodSubject f3 = clazz.uniqueMethodWithOriginalName("f3");
    assertThat(f3, not(isPresent()));
    MethodSubject f4 = clazz.uniqueMethodWithOriginalName("f4");
    assertThat(f4, not(isPresent()));

    ClassSubject impl = inspector.clazz(IMPL_CLASS_NAME);
    assertThat(impl, isPresent());
    AnnotationSubject anno = impl.annotation(ANNOTATION_NAME);
    assertThat(anno, isPresent());
    inspectAnnotationInstantiation(anno, ImmutableSet.of("f1", "f2"));
  }

  @Test
  public void b120951621_keepAnnotation() throws Exception {
    CodeInspector inspector =
        testForR8Compat(parameters.getBackend())
            .addProgramFiles(
                compiledJars.getForConfiguration(kotlinc, targetVersion),
                kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(getJavaJarFile(FOLDER))
            .addKeepMainRule(MAIN_CLASS_NAME)
            .addKeepRules(KEEP_ANNOTATIONS)
            .allowDiagnosticWarningMessages()
            .minification(minify)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .assertAllWarningMessagesMatch(
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
            .run(parameters.getRuntime(), MAIN_CLASS_NAME)
            .assertSuccessWithOutput(JAVA_OUTPUT)
            .inspector();
    ClassSubject clazz = inspector.clazz(ANNOTATION_NAME);
    assertThat(clazz, isPresent());
    assertEquals(minify, clazz.isRenamed());
    MethodSubject f1 = clazz.uniqueMethodWithOriginalName("f1");
    assertThat(f1, isPresentAndNotRenamed());
    MethodSubject f2 = clazz.uniqueMethodWithOriginalName("f2");
    assertThat(f2, isPresentAndNotRenamed());
    MethodSubject f3 = clazz.uniqueMethodWithOriginalName("f3");
    assertThat(f3, not(isPresent()));
    MethodSubject f4 = clazz.uniqueMethodWithOriginalName("f4");
    assertThat(f4, not(isPresent()));

    ClassSubject impl = inspector.clazz(IMPL_CLASS_NAME);
    assertThat(impl, isPresent());
    AnnotationSubject anno = impl.annotation(ANNOTATION_NAME);
    assertThat(anno, isPresent());
    inspectAnnotationInstantiation(anno, ImmutableSet.of("f1", "f2"));
  }

  @Test
  public void b120951621_noKeep() throws Exception {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addProgramFiles(
                compiledJars.getForConfiguration(kotlinc, targetVersion),
                kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(getJavaJarFile(FOLDER))
            .addKeepMainRule(MAIN_CLASS_NAME)
            .allowDiagnosticWarningMessages()
            .minification(minify)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .assertAllWarningMessagesMatch(
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
            .run(parameters.getRuntime(), MAIN_CLASS_NAME)
            .assertSuccessWithOutput(OUTPUT_WITHOUT_ANNOTATION)
            .inspector();
    ClassSubject clazz = inspector.clazz(ANNOTATION_NAME);
    assertThat(clazz, isPresent());
    assertEquals(minify, clazz.isRenamed());
    MethodSubject f1 = clazz.uniqueMethodWithOriginalName("f1");
    assertThat(f1, isPresent());
    MethodSubject f2 = clazz.uniqueMethodWithOriginalName("f2");
    assertThat(f2, isPresent());
    MethodSubject f3 = clazz.uniqueMethodWithOriginalName("f3");
    assertThat(f3, not(isPresent()));
    MethodSubject f4 = clazz.uniqueMethodWithOriginalName("f4");
    assertThat(f4, not(isPresent()));

    ClassSubject impl = inspector.clazz(IMPL_CLASS_NAME);
    assertThat(impl, isPresent());
    AnnotationSubject anno = impl.annotation(ANNOTATION_NAME);
    assertThat(anno, not(isPresent()));
  }

  private void inspectAnnotationInstantiation(
      AnnotationSubject annotationSubject, Set<String> expectedFields) {
    int count = 0;
    for (DexAnnotationElement element : annotationSubject.getAnnotation().elements) {
      String fieldName = element.name.toString();
      if (expectedFields.contains(fieldName)) {
        count++;
        String expectedValue = EXPECTED_ANNOTATION_VALUES.get(fieldName);
        assertThat(element.value.toString(), containsString(expectedValue));
      }
    }
    assertEquals(expectedFields.size(), count);
  }

}
