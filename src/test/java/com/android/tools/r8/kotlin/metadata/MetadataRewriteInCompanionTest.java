// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.KmClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInCompanionTest extends KotlinMetadataTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataRewriteInCompanionTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static Map<KotlinTargetVersion, Path> companionLibJarMap = new HashMap<>();

  @BeforeClass
  public static void createLibJar() throws Exception {
    String companionLibFolder = PKG_PREFIX + "/companion_lib";
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      Path companionLibJar =
          kotlinc(KOTLINC, targetVersion)
              .addSourceFiles(getKotlinFileInTest(companionLibFolder, "lib"))
              .compile();
      companionLibJarMap.put(targetVersion, companionLibJar);
    }
  }

  @Test
  public void testMetadataInCompanion_renamed() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramFiles(companionLibJarMap.get(targetVersion))
            // Keep the B class and its interface (which has the doStuff method).
            .addKeepRules("-keep class **.B")
            .addKeepRules("-keep class **.I { <methods>; }")
            // Keep getters for B$Companion.(singleton|foo) which will be referenced at the app.
            .addKeepRules("-keepclassmembers class **.B$* { *** get*(...); }")
            // No rule for Super, but will be kept and renamed.
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile();
    String pkg = getClass().getPackage().getName();
    final String superClassName = pkg + ".companion_lib.Super";
    final String bClassName = pkg + ".companion_lib.B";
    final String companionClassName = pkg + ".companion_lib.B$Companion";
    compileResult.inspect(inspector -> {
      ClassSubject sup = inspector.clazz(superClassName);
      assertThat(sup, isRenamed());

      ClassSubject impl = inspector.clazz(bClassName);
      assertThat(impl, isPresent());
      assertThat(impl, not(isRenamed()));
      // API entry is kept, hence the presence of Metadata.
      KmClassSubject kmClass = impl.getKmClass();
      assertThat(kmClass, isPresent());
      List<ClassSubject> superTypes = kmClass.getSuperTypes();
      assertTrue(superTypes.stream().noneMatch(
          supertype -> supertype.getFinalDescriptor().contains("Super")));
      assertTrue(superTypes.stream().anyMatch(
          supertype -> supertype.getFinalDescriptor().equals(sup.getFinalDescriptor())));

      // Bridge for the property in the companion that needs a backing field.
      MethodSubject singletonBridge = impl.uniqueMethodWithName("access$getSingleton$cp");
      assertThat(singletonBridge, isRenamed());

      // For B$Companion.foo, no backing field needed, hence no bridge.
      MethodSubject fooBridge = impl.uniqueMethodWithName("access$getFoo$cp");
      assertThat(fooBridge, not(isPresent()));

      ClassSubject companion = inspector.clazz(companionClassName);
      assertThat(companion, isRenamed());

      MethodSubject singletonGetter = companion.uniqueMethodWithName("getSingleton");
      assertThat(singletonGetter, isPresent());

      MethodSubject fooGetter = companion.uniqueMethodWithName("getFoo");
      assertThat(fooGetter, isPresent());
    });

    Path libJar = compileResult.writeToZip();

    String appFolder = PKG_PREFIX + "/companion_app";
    ProcessResult kotlinTestCompileResult =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(appFolder, "main"))
            .setOutputPath(temp.newFolder().toPath())
            // TODO(b/70169921): update to just .compile() once fixed.
            .compileRaw();

    // TODO(b/70169921): should be able to compile!
    assertNotEquals(0, kotlinTestCompileResult.exitCode);
    assertThat(kotlinTestCompileResult.stderr, containsString("unresolved reference: singleton"));
    assertThat(kotlinTestCompileResult.stderr, containsString("unresolved reference: foo"));
  }
}
