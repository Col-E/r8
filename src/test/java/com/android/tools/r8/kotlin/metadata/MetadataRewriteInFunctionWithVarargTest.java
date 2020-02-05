// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.utils.codeinspector.Matchers.isExtensionFunction;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.KmFunctionSubject;
import com.android.tools.r8.utils.codeinspector.KmPackageSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInFunctionWithVarargTest extends KotlinMetadataTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataRewriteInFunctionWithVarargTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static Map<KotlinTargetVersion, Path> varargLibJarMap = new HashMap<>();

  @BeforeClass
  public static void createLibJar() throws Exception {
    String varargLibFolder = PKG_PREFIX + "/vararg_lib";
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      Path varargLibJar =
          kotlinc(KOTLINC, targetVersion)
              .addSourceFiles(getKotlinFileInTest(varargLibFolder, "lib"))
              .compile();
      varargLibJarMap.put(targetVersion, varargLibJar);
    }
  }

  @Test
  public void testMetadataInFunctionWithVararg() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(varargLibJarMap.get(targetVersion))
            // keep SomClass#foo, since there is a method reference in the app.
            .addKeepRules("-keep class **.SomeClass { *** foo(...); }")
            // Keep LibKt, along with bar function.
            .addKeepRules("-keep class **.LibKt { *** bar(...); }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile()
            .inspect(this::inspect)
            .writeToZip();

    ProcessResult kotlinTestCompileResult =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/vararg_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            // TODO(b/70169921): update to just .compile() once fixed.
            .compileRaw();

    // TODO(b/70169921): should be able to compile!
    assertNotEquals(0, kotlinTestCompileResult.exitCode);
    assertThat(
        kotlinTestCompileResult.stderr,
        containsString("type mismatch: inferred type is String but Array<T> was expected"));
    assertThat(
        kotlinTestCompileResult.stderr,
        containsString("(???, ???) -> [ERROR : <ERROR FUNCTION RETURN TYPE>]"));
    assertThat(
        kotlinTestCompileResult.stderr,
        containsString("but kotlin.jvm.functions.Function2<P1, P2, R> was expected"));
  }

  private void inspect(CodeInspector inspector) {
    String className = PKG + ".vararg_lib.SomeClass";
    String libClassName = PKG + ".vararg_lib.LibKt";

    ClassSubject cls = inspector.clazz(className);
    assertThat(cls, isPresent());
    assertThat(cls, not(isRenamed()));

    MethodSubject foo = cls.uniqueMethodWithName("foo");
    assertThat(foo, isPresent());
    assertThat(foo, not(isRenamed()));

    ClassSubject libKt = inspector.clazz(libClassName);
    assertThat(libKt, isPresent());
    assertThat(libKt, not(isRenamed()));

    MethodSubject bar = libKt.uniqueMethodWithName("bar");
    assertThat(bar, isPresent());
    assertThat(bar, not(isRenamed()));

    KmPackageSubject kmPackage = libKt.getKmPackage();
    assertThat(kmPackage, isPresent());

    KmFunctionSubject kmFunction = kmPackage.kmFunctionWithUniqueName("bar");
    assertThat(kmFunction, not(isExtensionFunction()));
    // TODO(b/70169921): inspect 1st arg is `vararg`.
  }
}
