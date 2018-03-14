// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.kotlin.TestKotlinClass.AccessorKind;
import com.android.tools.r8.kotlin.TestKotlinClass.Visibility;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.FieldSubject;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Ignore;
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

  private static final TestKotlinCompanionClass COMPANION_LATE_INIT_PROPERTY_CLASS =
      new TestKotlinCompanionClass("properties.CompanionLateInitProperties")
          .addProperty("privateLateInitProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("internalLateInitProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicLateInitProp", JAVA_LANG_STRING, Visibility.PUBLIC);

  private static final TestKotlinClass PROPERTY_ACCESS_FOR_INNER_CLASS =
      new TestKotlinClass("accessors.PropertyAccessorForInnerClass")
          .addProperty("privateProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("privateLateInitProp", JAVA_LANG_STRING, Visibility.PRIVATE);

  private static final TestKotlinClass PROPERTY_ACCESS_FOR_LAMBDA_CLASS =
      new TestKotlinClass("accessors.PropertyAccessorForLambda")
          .addProperty("property", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("indirectPropertyGetter", JAVA_LANG_STRING, Visibility.PRIVATE);

  @Test
  public void testCompanionProperty_primitivePropertyIsAlwaysInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionPropertiesKt",
        "companionProperties_usePrimitiveProp");
    runTest(PROPERTIES_PACKAGE_NAME, mainClass, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject outerClass = checkClassExists(dexInspector, testedClass.getOuterClassName());
      String propertyName = "primitiveProp";
      FieldSubject fieldSubject = checkFieldIsPresent(outerClass, "int", propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getterAccessor = testedClass
          .getGetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);
      MemberNaming.MethodSignature setterAccessor = testedClass
          .getSetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);

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
    final TestKotlinCompanionClass testedClass = COMPANION_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionPropertiesKt",
        "companionProperties_usePrivateProp");
    runTest(PROPERTIES_PACKAGE_NAME, mainClass, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject outerClass = checkClassExists(dexInspector, testedClass.getOuterClassName());
      String propertyName = "privateProp";
      FieldSubject fieldSubject = checkFieldIsPresent(outerClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getterAccessor = testedClass
          .getGetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);
      MemberNaming.MethodSignature setterAccessor = testedClass
          .getSetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);
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
    final TestKotlinCompanionClass testedClass = COMPANION_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionPropertiesKt",
        "companionProperties_useInternalProp");
    runTest(PROPERTIES_PACKAGE_NAME, mainClass, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject outerClass = checkClassExists(dexInspector, testedClass.getOuterClassName());
      String propertyName = "internalProp";
      FieldSubject fieldSubject = checkFieldIsPresent(outerClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getterAccessor = testedClass
          .getGetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);
      MemberNaming.MethodSignature setterAccessor = testedClass
          .getSetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);

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
    final TestKotlinCompanionClass testedClass = COMPANION_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionPropertiesKt",
        "companionProperties_usePublicProp");
    runTest(PROPERTIES_PACKAGE_NAME, mainClass, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject outerClass = checkClassExists(dexInspector, testedClass.getOuterClassName());
      String propertyName = "publicProp";
      FieldSubject fieldSubject = checkFieldIsPresent(outerClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getterAccessor = testedClass
          .getGetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);
      MemberNaming.MethodSignature setterAccessor = testedClass
          .getSetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);

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
  public void testCompanionLateInitProperty_privatePropertyIsAlwaysInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_LATE_INIT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionLateInitPropertiesKt",
        "companionLateInitProperties_usePrivateLateInitProp");
    runTest(PROPERTIES_PACKAGE_NAME, mainClass, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject outerClass = checkClassExists(dexInspector, testedClass.getOuterClassName());
      String propertyName = "privateLateInitProp";
      FieldSubject fieldSubject = checkFieldIsPresent(outerClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getterAccessor = testedClass
          .getGetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);
      MemberNaming.MethodSignature setterAccessor = testedClass
          .getSetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);
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
  public void testCompanionLateInitProperty_internalPropertyIsAlwaysInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_LATE_INIT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionLateInitPropertiesKt",
        "companionLateInitProperties_useInternalLateInitProp");
    runTest(PROPERTIES_PACKAGE_NAME, mainClass, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject outerClass = checkClassExists(dexInspector, testedClass.getOuterClassName());
      String propertyName = "internalLateInitProp";
      FieldSubject fieldSubject = checkFieldIsPresent(outerClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getterAccessor = testedClass
          .getGetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);
      MemberNaming.MethodSignature setterAccessor = testedClass
          .getSetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);

      assertTrue(fieldSubject.getField().accessFlags.isPublic());
      checkMethodIsAbsent(outerClass, getterAccessor);
      checkMethodIsAbsent(outerClass, setterAccessor);
    });
  }

  @Test
  public void testCompanionLateInitProperty_publicPropertyIsAlwaysInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_LATE_INIT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionLateInitPropertiesKt",
        "companionLateInitProperties_usePublicLateInitProp");
    runTest(PROPERTIES_PACKAGE_NAME, mainClass, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject outerClass = checkClassExists(dexInspector, testedClass.getOuterClassName());
      String propertyName = "publicLateInitProp";
      FieldSubject fieldSubject = checkFieldIsPresent(outerClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getterAccessor = testedClass
          .getGetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);
      MemberNaming.MethodSignature setterAccessor = testedClass
          .getSetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);

      assertTrue(fieldSubject.getField().accessFlags.isPublic());
      checkMethodIsAbsent(outerClass, getterAccessor);
      checkMethodIsAbsent(outerClass, setterAccessor);
    });
  }

  @Test
  public void testAccessor() throws Exception {
    TestKotlinCompanionClass testedClass = ACCESSOR_COMPANION_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("accessors.AccessorKt",
        "accessor_accessPropertyFromCompanionClass");
    runTest("accessors", mainClass, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject outerClass = checkClassExists(dexInspector, testedClass.getOuterClassName());
      ClassSubject companionClass = checkClassExists(dexInspector, testedClass.getClassName());
      String propertyName = "property";
      FieldSubject fieldSubject = checkFieldIsPresent(outerClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      // The getter is always inlined since it just calls into the accessor.
      MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
      checkMethodIsAbsent(companionClass, getter);

      MemberNaming.MethodSignature getterAccessor =
          testedClass.getGetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);
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
  public void testAccessorFromPrivate() throws Exception {
    TestKotlinCompanionClass testedClass = ACCESSOR_COMPANION_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("accessors.AccessorKt",
        "accessor_accessPropertyFromOuterClass");
    runTest("accessors", mainClass, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject outerClass = checkClassExists(dexInspector, testedClass.getOuterClassName());
      ClassSubject companionClass = checkClassExists(dexInspector, testedClass.getClassName());
      String propertyName = "property";
      FieldSubject fieldSubject = checkFieldIsPresent(outerClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      // We cannot inline the getter because we don't know if NPE is preserved.
      MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
      checkMethodIsPresent(companionClass, getter);

      // We should always inline the static accessor method.
      MemberNaming.MethodSignature getterAccessor =
          testedClass.getGetterAccessorForProperty(propertyName, AccessorKind.FROM_INNER);
      checkMethodIsAbsent(outerClass, getterAccessor);

      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
      }
    });
  }

  @Test
  public void testAccessorForInnerClassIsRemovedWhenNotUsed() throws Exception {
    TestKotlinClass testedClass = PROPERTY_ACCESS_FOR_INNER_CLASS;
    String mainClass = addMainToClasspath(testedClass.className + "Kt",
        "noUseOfPropertyAccessorFromInnerClass");
    runTest("accessors", mainClass, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject classSubject = checkClassExists(dexInspector, testedClass.getClassName());

      for (String propertyName : testedClass.properties.keySet()) {
        MemberNaming.MethodSignature getterAccessor =
            testedClass.getGetterAccessorForProperty(propertyName, AccessorKind.FROM_INNER);
        MemberNaming.MethodSignature setterAccessor =
            testedClass.getSetterAccessorForProperty(propertyName, AccessorKind.FROM_INNER);

        checkMethodIsAbsent(classSubject, getterAccessor);
        checkMethodIsAbsent(classSubject, setterAccessor);
      }
    });
  }

  @Test
  @Ignore("b/74103342")
  public void testPrivatePropertyAccessorForInnerClassCanBeInlined() throws Exception {
    TestKotlinClass testedClass = PROPERTY_ACCESS_FOR_INNER_CLASS;
    String mainClass = addMainToClasspath(testedClass.className + "Kt",
        "usePrivatePropertyAccessorFromInnerClass");
    runTest("accessors", mainClass, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject classSubject = checkClassExists(dexInspector, testedClass.getClassName());

      String propertyName = "privateProp";
      FieldSubject fieldSubject = checkFieldIsPresent(classSubject, JAVA_LANG_STRING,
          propertyName);
      assertFalse(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getterAccessor =
          testedClass.getGetterAccessorForProperty(propertyName, AccessorKind.FROM_INNER);
      MemberNaming.MethodSignature setterAccessor =
          testedClass.getSetterAccessorForProperty(propertyName, AccessorKind.FROM_INNER);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
        checkMethodIsAbsent(classSubject, getterAccessor);
        checkMethodIsAbsent(classSubject, setterAccessor);
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
        checkMethodIsPresent(classSubject, getterAccessor);
        checkMethodIsPresent(classSubject, setterAccessor);
      }
    });
  }

  @Test
  @Ignore("b/74103342")
  public void testPrivateLateInitPropertyAccessorForInnerClassCanBeInlined() throws Exception {
    TestKotlinClass testedClass = PROPERTY_ACCESS_FOR_INNER_CLASS;
    String mainClass = addMainToClasspath(testedClass.className + "Kt",
        "usePrivateLateInitPropertyAccessorFromInnerClass");
    runTest("accessors", mainClass, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject classSubject = checkClassExists(dexInspector, testedClass.getClassName());

      String propertyName = "privateLateInitProp";
      FieldSubject fieldSubject = checkFieldIsPresent(classSubject, JAVA_LANG_STRING,
          propertyName);
      assertFalse(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getterAccessor =
          testedClass.getGetterAccessorForProperty(propertyName, AccessorKind.FROM_INNER);
      MemberNaming.MethodSignature setterAccessor =
          testedClass.getSetterAccessorForProperty(propertyName, AccessorKind.FROM_INNER);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
        checkMethodIsAbsent(classSubject, getterAccessor);
        checkMethodIsAbsent(classSubject, setterAccessor);
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
        checkMethodIsPresent(classSubject, getterAccessor);
        checkMethodIsPresent(classSubject, setterAccessor);
      }
    });
  }

  @Test
  @Ignore("b/74103342")
  public void testAccessorForLambdaIsRemovedWhenNotUsed() throws Exception {
    TestKotlinClass testedClass = PROPERTY_ACCESS_FOR_LAMBDA_CLASS;
    String mainClass = addMainToClasspath(testedClass.className + "Kt",
        "noUseOfPropertyAccessorFromLambda");
    runTest("accessors", mainClass, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject classSubject = checkClassExists(dexInspector, testedClass.getClassName());
      String propertyName = "property";

      MemberNaming.MethodSignature getterAccessor =
          testedClass.getGetterAccessorForProperty(propertyName, AccessorKind.FROM_LAMBDA);
      MemberNaming.MethodSignature setterAccessor =
          testedClass.getSetterAccessorForProperty(propertyName, AccessorKind.FROM_LAMBDA);

      checkMethodIsAbsent(classSubject, getterAccessor);
      checkMethodIsAbsent(classSubject, setterAccessor);
    });
  }

  @Test
  @Ignore("b/74103342")
  public void testAccessorForLambdaCanBeInlined() throws Exception {
    TestKotlinClass testedClass = PROPERTY_ACCESS_FOR_LAMBDA_CLASS;
    String mainClass = addMainToClasspath(testedClass.className + "Kt",
        "usePropertyAccessorFromLambda");
    runTest("accessors", mainClass, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject classSubject = checkClassExists(dexInspector, testedClass.getClassName());
      String propertyName = "property";
      FieldSubject fieldSubject = checkFieldIsPresent(classSubject, JAVA_LANG_STRING, propertyName);
      assertFalse(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getterAccessor =
          testedClass.getGetterAccessorForProperty(propertyName, AccessorKind.FROM_LAMBDA);
      MemberNaming.MethodSignature setterAccessor =
          testedClass.getSetterAccessorForProperty(propertyName, AccessorKind.FROM_LAMBDA);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
        checkMethodIsAbsent(classSubject, getterAccessor);
        checkMethodIsAbsent(classSubject, setterAccessor);
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
        checkMethodIsPresent(classSubject, getterAccessor);
        checkMethodIsPresent(classSubject, setterAccessor);
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
    }

    AndroidApp app = compileWithR8(jasminBuilder.build(),
        keepMainProguardConfiguration("Foo") + "\ndontobfuscate");
    String artOutput = runOnArt(app, "Foo");
    assertEquals(javaResult.stdout, artOutput);
  }
}
