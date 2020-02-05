// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.utils.DescriptorUtils.descriptorToJavaType;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.KmClassSubject;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInNestedClassTest extends KotlinMetadataTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataRewriteInNestedClassTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static final Map<KotlinTargetVersion, Path> nestedLibJarMap = new HashMap<>();

  @BeforeClass
  public static void createLibJar() throws Exception {
    String nestedLibFolder = PKG_PREFIX + "/nested_lib";
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      Path nestedLibJar =
          kotlinc(KOTLINC, targetVersion)
              .addSourceFiles(getKotlinFileInTest(nestedLibFolder, "lib"))
              .compile();
      nestedLibJarMap.put(targetVersion, nestedLibJar);
    }
  }

  @Test
  public void testMetadataInNestedClass() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(nestedLibJarMap.get(targetVersion))
            // Keep the Outer class and delegations.
            .addKeepRules("-keep class **.Outer { <init>(...); *** delegate*(...); }")
            // Keep Inner to check the hierarchy.
            .addKeepRules("-keep class **.*Inner")
            // Keep Nested, but allow obfuscation.
            .addKeepRules("-keep,allowobfuscation class **.*Nested")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .addKeepAttributes(ProguardKeepAttributes.INNER_CLASSES)
            .addKeepAttributes(ProguardKeepAttributes.ENCLOSING_METHOD)
            .compile()
            .inspect(this::inspect)
            .writeToZip();

    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/nested_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".nested_app.MainKt")
        .assertSuccessWithOutputLines("Inner::inner", "42", "Nested::nested", "42");
  }

  private void inspect(CodeInspector inspector) {
    String outerClassName = PKG + ".nested_lib.Outer";
    String innerClassName = outerClassName + "$Inner";
    String nestedClassName = outerClassName + "$Nested";

    ClassSubject inner = inspector.clazz(innerClassName);
    assertThat(inner, isPresent());
    assertThat(inner, not(isRenamed()));

    ClassSubject nested = inspector.clazz(nestedClassName);
    assertThat(nested, isRenamed());

    ClassSubject outer = inspector.clazz(outerClassName);
    assertThat(outer, isPresent());
    assertThat(outer, not(isRenamed()));

    KmClassSubject kmClass = outer.getKmClass();
    assertThat(kmClass, isPresent());

    kmClass.getNestedClassDescriptors().forEach(nestedClassDescriptor -> {
      ClassSubject nestedClass = inspector.clazz(descriptorToJavaType(nestedClassDescriptor));
      if (nestedClass.getOriginalName().contains("Inner")) {
        assertThat(nestedClass, not(isRenamed()));
        assertEquals(nestedClassDescriptor, nestedClass.getFinalDescriptor());
      } else {
        assertThat(nestedClass, isRenamed());
        // TODO(b/70169921): nestedClass in KmClass should refer to renamed classes.
        assertNotEquals(nestedClassDescriptor, nestedClass.getFinalDescriptor());
      }
    });
  }
}
