// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.kotlin.TestKotlinClass.Visibility;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.FieldSubject;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;

public class R8KotlinAccessorTest extends AbstractR8KotlinTestBase {

  private static final String JAVA_LANG_STRING = "java.lang.String";

  private static final TestKotlinCompanionClass ACCESSOR_COMPANION_PROPERTY_CLASS =
      new TestKotlinCompanionClass("accessors.Accessor")
          .addProperty("property", JAVA_LANG_STRING, Visibility.PRIVATE);

  private static final String PROPERTIES_PACKAGE_NAME = "properties";

  private static final TestKotlinCompanionClass COMPANION_PROPERTY_CLASS =
      new TestKotlinCompanionClass("properties.CompanionProperties")
          .addProperty("privateProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("protectedProp", JAVA_LANG_STRING, Visibility.PROTECTED)
          .addProperty("internalProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicProp", JAVA_LANG_STRING, Visibility.PUBLIC)
          .addProperty("primitiveProp", "int", Visibility.PUBLIC);

  @Test
  public void testCompanionProperty_primitivePropertyIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath("properties.CompanionPropertiesKt",
        "companionProperties_usePrimitiveProp");
    runTest(PROPERTIES_PACKAGE_NAME, mainClass, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject outerClass = checkClassExists(dexInspector,
          COMPANION_PROPERTY_CLASS.getOuterClassName());
      String propertyName = "primitiveProp";
      FieldSubject fieldSubject = checkFieldIsPresent(outerClass, "int", propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getterAccessor =
          new MemberNaming.MethodSignature("access$getPrimitiveProp$cp", "int",
              Collections.emptyList());
      MemberNaming.MethodSignature setterAccessor =
          new MemberNaming.MethodSignature("access$setPrimitiveProp$cp", "void",
              Collections.singletonList("int"));

      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
        checkMethodIsAbsent(outerClass, getterAccessor);
        checkMethodIsAbsent(outerClass, setterAccessor);
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
        checkMethodIsPresent(outerClass, getterAccessor);
        checkMethodIsPresent(outerClass, setterAccessor);
      }
    });
  }

  @Test
  public void testCompanionProperty_privatePropertyIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath(
        "properties.CompanionPropertiesKt", "companionProperties_usePrivateProp");
    runTest(PROPERTIES_PACKAGE_NAME, mainClass, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject outerClass = checkClassExists(dexInspector,
          COMPANION_PROPERTY_CLASS.getOuterClassName());
      String propertyName = "privateProp";
      FieldSubject fieldSubject = checkFieldIsPresent(outerClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getterAccessor =
          new MemberNaming.MethodSignature("access$getPrivateProp$cp", JAVA_LANG_STRING,
              Collections.emptyList());
      MemberNaming.MethodSignature setterAccessor =
          new MemberNaming.MethodSignature("access$setPrivateProp$cp", "void",
              Collections.singletonList(JAVA_LANG_STRING));
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());

        checkMethodIsAbsent(outerClass, getterAccessor);
        checkMethodIsAbsent(outerClass, setterAccessor);
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());

        checkMethodIsPresent(outerClass, getterAccessor);
        checkMethodIsPresent(outerClass, setterAccessor);
      }
    });
  }

  @Test
  public void testCompanionProperty_internalPropertyIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath(
        "properties.CompanionPropertiesKt", "companionProperties_useInternalProp");
    runTest(PROPERTIES_PACKAGE_NAME, mainClass, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject outerClass = checkClassExists(dexInspector,
          COMPANION_PROPERTY_CLASS.getOuterClassName());
      String propertyName = "internalProp";
      FieldSubject fieldSubject = checkFieldIsPresent(outerClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getterAccessor =
          new MemberNaming.MethodSignature("access$getInternalProp$cp", JAVA_LANG_STRING,
              Collections.emptyList());
      MemberNaming.MethodSignature setterAccessor =
          new MemberNaming.MethodSignature("access$setInternalProp$cp", "void",
              Collections.singletonList(JAVA_LANG_STRING));

      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
        checkMethodIsAbsent(outerClass, getterAccessor);
        checkMethodIsAbsent(outerClass, setterAccessor);
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
        checkMethodIsPresent(outerClass, getterAccessor);
        checkMethodIsPresent(outerClass, setterAccessor);
      }
    });
  }

  @Test
  public void testCompanionProperty_publicPropertyIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath("properties.CompanionPropertiesKt",
        "companionProperties_usePublicProp");
    runTest(PROPERTIES_PACKAGE_NAME, mainClass, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject outerClass = checkClassExists(dexInspector,
          COMPANION_PROPERTY_CLASS.getOuterClassName());
      String propertyName = "publicProp";
      FieldSubject fieldSubject = checkFieldIsPresent(outerClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getterAccessor =
          new MemberNaming.MethodSignature("access$getPublicProp$cp", JAVA_LANG_STRING,
              Collections.emptyList());
      MemberNaming.MethodSignature setterAccessor =
          new MemberNaming.MethodSignature("access$setPublicProp$cp", "void",
              Collections.singletonList(JAVA_LANG_STRING));

      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
        checkMethodIsAbsent(outerClass, getterAccessor);
        checkMethodIsAbsent(outerClass, setterAccessor);
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
        checkMethodIsPresent(outerClass, getterAccessor);
        checkMethodIsPresent(outerClass, setterAccessor);
      }
    });
  }

  @Test
  public void testAccessor() throws Exception {
    String mainClass = addMainToClasspath("accessors.AccessorKt",
        "accessor_accessCompanionPrivate");
    runTest("accessors", mainClass, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      TestKotlinCompanionClass testedClass = ACCESSOR_COMPANION_PROPERTY_CLASS;
      ClassSubject outerClass = checkClassExists(dexInspector,
          testedClass.getOuterClassName());
      ClassSubject companionClass = checkClassExists(dexInspector,
          testedClass.getClassName());
      String propertyName = "property";
      FieldSubject fieldSubject = checkFieldIsPresent(outerClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      // The getter is always inlined since it just calls into the accessor.
      MemberNaming.MethodSignature getter = testedClass
          .getGetterForProperty(propertyName);
      checkMethodIsAbsent(companionClass, getter);

      MemberNaming.MethodSignature getterAccessor =
          new MemberNaming.MethodSignature("access$getProperty$cp", JAVA_LANG_STRING,
              Collections.emptyList());
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
        checkMethodIsAbsent(outerClass, getterAccessor);
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
        checkMethodIsPresent(outerClass, getterAccessor);
      }
    });
  }

  @Test
  public void testStaticFieldAccessorWithJasmin() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();
    ClassBuilder classBuilder = jasminBuilder.addClass("Foo");
    classBuilder.addDefaultConstructor();
    classBuilder.addStaticField("aField", "I", "5");
    classBuilder.addMainMethod(
        ".limit stack 1",
        "invokestatic Foo$Inner/readField()V",
        "return"
    );
    classBuilder.addStaticMethod("access$field", Collections.emptyList(), "I",
        ".limit stack 1",
        "getstatic Foo.aField I",
        "ireturn");

    classBuilder = jasminBuilder.addClass("Foo$Inner");
    classBuilder.addDefaultConstructor();
    classBuilder.addStaticMethod("readField", Collections.emptyList(), "V",
        ".limit stack 2",
        "getstatic java/lang/System.out Ljava/io/PrintStream;",
        "invokestatic Foo/access$field()I",
        "invokevirtual java/io/PrintStream/println(I)V",
        "return"
    );

    Path javaOutput = writeToZip(jasminBuilder);
    ProcessResult javaResult = ToolHelper.runJava(javaOutput, "Foo");
    if (javaResult.exitCode != 0) {
      System.err.println(javaResult.stderr);
      Assert.fail();
    } else {
      System.out.println(javaResult.stdout);
    }

    AndroidApp app = compileWithR8(jasminBuilder.build(),
        keepMainProguardConfiguration("Foo") + "\ndontobfuscate");
    String artOutput = runOnArt(app, "Foo");
    System.out.println(artOutput);
  }
}
