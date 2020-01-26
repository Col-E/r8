// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.utils.codeinspector.Matchers.isExtensionProperty;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.KmClassSubject;
import com.android.tools.r8.utils.codeinspector.KmPropertySubject;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRenameInPropertyTest extends KotlinMetadataTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataRenameInPropertyTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static Path propertyTypeLibJar;

  @BeforeClass
  public static void createLibJar() throws Exception {
    String propertyTypeLibFolder = PKG_PREFIX + "/fragile_property_lib";
    propertyTypeLibJar =
        kotlinc(KOTLINC, KotlinTargetVersion.JAVA_8)
            .addSourceFiles(getKotlinFileInTest(propertyTypeLibFolder, "lib"))
            .compile();
  }

  @Test
  public void testMetadataInProperty_getterOnly() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramFiles(propertyTypeLibJar)
            // Keep property getters
            .addKeepRules("-keep class **.Person { <init>(...); }")
            .addKeepRules("-keepclassmembers class **.Person { *** get*(); }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile();
    String pkg = getClass().getPackage().getName();
    final String personClassName = pkg + ".fragile_property_lib.Person";
    compileResult.inspect(inspector -> {
      ClassSubject person = inspector.clazz(personClassName);
      assertThat(person, isPresent());
      assertThat(person, not(isRenamed()));

      // API entry is kept, hence the presence of Metadata.
      KmClassSubject kmClass = person.getKmClass();
      assertThat(kmClass, isPresent());

      KmPropertySubject name = kmClass.kmPropertyWithUniqueName("name");
      assertThat(name, isPresent());
      assertThat(name, not(isExtensionProperty()));
      assertNotNull(name.fieldSignature());
      assertNotNull(name.getterSignature());
      assertNull(name.setterSignature());

      KmPropertySubject familyName = kmClass.kmPropertyWithUniqueName("familyName");
      assertThat(familyName, isPresent());
      assertThat(familyName, not(isExtensionProperty()));
      // No backing field for property `familyName`
      assertNull(familyName.fieldSignature());
      assertNotNull(familyName.getterSignature());
      // No setter for property `familyName`
      assertNull(familyName.setterSignature());

      KmPropertySubject age = kmClass.kmPropertyWithUniqueName("age");
      assertThat(age, isPresent());
      assertThat(age, not(isExtensionProperty()));
      assertNotNull(age.fieldSignature());
      assertNotNull(age.getterSignature());
      assertNull(name.setterSignature());
    });

    Path libJar = compileResult.writeToZip();

    String appFolder = PKG_PREFIX + "/fragile_property_only_getter";
    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, KotlinTargetVersion.JAVA_8)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(appFolder, "getter_user"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), pkg + ".fragile_property_only_getter.Getter_userKt")
        .assertSuccessWithOutputLines("true", "false", "Hey");
  }

  @Test
  public void testMetadataInProperty_setterOnly() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramFiles(propertyTypeLibJar)
            // Keep property setters (and users)
            .addKeepRules("-keep class **.Person { <init>(...); }")
            .addKeepRules("-keepclassmembers class **.Person { void set*(...); }")
            .addKeepRules("-keepclassmembers class **.Person { void aging(); }")
            .addKeepRules("-keepclassmembers class **.Person { void change*(...); }")
            // Keep LibKt extension methods
            .addKeepRules("-keep class **.LibKt { <methods>; }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile();
    String pkg = getClass().getPackage().getName();
    final String personClassName = pkg + ".fragile_property_lib.Person";
    compileResult.inspect(inspector -> {
      ClassSubject person = inspector.clazz(personClassName);
      assertThat(person, isPresent());
      assertThat(person, not(isRenamed()));
      // API entry is kept, hence the presence of Metadata.
      KmClassSubject kmClass = person.getKmClass();
      assertThat(kmClass, isPresent());

      KmPropertySubject name = kmClass.kmPropertyWithUniqueName("name");
      assertThat(name, isPresent());
      assertThat(name, not(isExtensionProperty()));
      assertNull(name.fieldSignature());
      assertNull(name.getterSignature());
      assertNotNull(name.setterSignature());

      KmPropertySubject familyName = kmClass.kmPropertyWithUniqueName("familyName");
      assertThat(familyName, not(isPresent()));

      KmPropertySubject age = kmClass.kmPropertyWithUniqueName("age");
      assertThat(age, isPresent());
      assertThat(age, not(isExtensionProperty()));
      assertNotNull(age.fieldSignature());
      assertNull(age.getterSignature());
      assertNotNull(age.setterSignature());
    });

    Path libJar = compileResult.writeToZip();

    String appFolder = PKG_PREFIX + "/fragile_property_only_setter";
    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, KotlinTargetVersion.JAVA_8)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(appFolder, "setter_user"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), pkg + ".fragile_property_only_setter.Setter_userKt")
        .assertSuccessWithOutputLines();
  }
}
