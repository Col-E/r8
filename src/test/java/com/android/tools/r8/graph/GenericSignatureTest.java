// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.ClassTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.GenericSignature.Parser;
import com.android.tools.r8.graph.GenericSignature.TypeSignature;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.Test;

public class GenericSignatureTest extends TestBase {

  @Test
  public void test() throws Exception {
    AndroidApp app =
        testForD8()
            .debug()
            .addProgramClassesAndInnerClasses(A.class, B.class, CY.class, CYY.class)
            .compile()
            .app;
    AppView<AppInfoWithLiveness> appView = computeAppViewWithLiveness(app);
    DexItemFactory factory = appView.dexItemFactory();
    CodeInspector inspector = new CodeInspector(appView.appInfo().app());

    ClassSubject a = inspector.clazz(A.class);
    assertThat(a, isPresent());
    ClassSubject y = inspector.clazz(A.Y.class);
    assertThat(y, isPresent());
    ClassSubject yy = inspector.clazz(A.Y.YY.class);
    assertThat(yy, isPresent());
    ClassSubject zz = inspector.clazz(A.Y.ZZ.class);
    assertThat(zz, isPresent());
    ClassSubject b = inspector.clazz(B.class);
    assertThat(b, isPresent());
    ClassSubject cy = inspector.clazz(CY.class);
    assertThat(cy, isPresent());
    ClassSubject cyy = inspector.clazz(CYY.class);
    assertThat(cyy, isPresent());

    ClassSignature classSignature;
    ClassTypeSignature classTypeSignature;
    FieldTypeSignature fieldTypeSignature;
    MethodTypeSignature methodTypeSignature;
    List<FieldTypeSignature> typeArguments;
    FieldTypeSignature typeArgument;
    TypeSignature parameterSignature;
    TypeSignature elementSignature;

    //
    // Testing ClassSignature
    //

    // class CYY<T extends A<T>.Y> extends CY<T>
    DexClass clazz = cyy.getDexClass();
    assertNotNull(clazz);
    classSignature = Parser.toClassSignature(clazz, appView);
    assertNotNull(classSignature);

    // TODO(b/129925954): test formal type parameter of CYY

    assertTrue(classSignature.superInterfaceSignatures.isEmpty());
    classTypeSignature = classSignature.superClassSignature;
    assertEquals(cy.getDexClass().type, classTypeSignature.type);
    typeArguments = classTypeSignature.typeArguments;
    assertEquals(1, typeArguments.size());
    typeArgument = typeArguments.get(0);
    assertTrue(typeArgument.isTypeVariableSignature());
    assertEquals("T", typeArgument.asTypeVariableSignature().typeVariable);

    //
    // Testing FieldTypeSignature
    //

    FieldSubject yyInZZ = zz.uniqueFieldWithName("yy");
    assertThat(yyInZZ, isPresent());
    DexEncodedField field = yyInZZ.getField();
    assertNotNull(field);

    fieldTypeSignature = Parser.toFieldTypeSignature(field, appView);
    assertNotNull(fieldTypeSignature);

    // field type: A$Y$YY
    assertTrue(fieldTypeSignature.isClassTypeSignature());
    check_A_Y_YY(a, y, yy, fieldTypeSignature.asClassTypeSignature());

    //
    // Testing MethodTypeSignature
    //

    // A$Y$YY newYY([B<T>)
    MethodSubject newYY = zz.uniqueMethodWithName("newYY");
    assertThat(newYY, isPresent());
    DexEncodedMethod method = newYY.getMethod();
    assertNotNull(method);

    methodTypeSignature = Parser.toMethodTypeSignature(method, appView);
    assertNotNull(methodTypeSignature);

    // return type: A$Y$YY
    TypeSignature returnType = methodTypeSignature.returnType();
    assertTrue(returnType.isFieldTypeSignature());
    assertTrue(returnType.asFieldTypeSignature().isClassTypeSignature());
    check_A_Y_YY(a, y, yy, returnType.asFieldTypeSignature().asClassTypeSignature());

    // type of 1st argument: [B<T>
    assertEquals(1, methodTypeSignature.typeSignatures.size());
    parameterSignature = methodTypeSignature.getParameterTypeSignature(0);
    assertNotNull(parameterSignature);
    assertTrue(parameterSignature.isFieldTypeSignature());
    assertTrue(parameterSignature.asFieldTypeSignature().isArrayTypeSignature());
    elementSignature =
        parameterSignature.asFieldTypeSignature().asArrayTypeSignature().elementSignature;
    assertTrue(elementSignature.isFieldTypeSignature());
    assertTrue(elementSignature.asFieldTypeSignature().isClassTypeSignature());
    classTypeSignature = elementSignature.asFieldTypeSignature().asClassTypeSignature();
    assertEquals(b.getDexClass().type, classTypeSignature.type);

    // Function<A$Y$ZZ<TT>, A$Y$YY> convertToYY(Supplier<A$Y$ZZ<TT>>
    MethodSubject convertToYY = zz.uniqueMethodWithName("convertToYY");
    assertThat(convertToYY, isPresent());
    method = convertToYY.getMethod();
    assertNotNull(method);

    methodTypeSignature = GenericSignature.Parser.toMethodTypeSignature(method, appView);
    assertNotNull(methodTypeSignature);

    // return type: Function<A$Y$ZZ<TT>, A$Y$YY>
    returnType = methodTypeSignature.returnType();
    assertTrue(returnType.isFieldTypeSignature());
    assertTrue(returnType.asFieldTypeSignature().isClassTypeSignature());
    classTypeSignature = returnType.asFieldTypeSignature().asClassTypeSignature();
    DexType functionType =
        factory.createType(DescriptorUtils.javaTypeToDescriptor(Function.class.getTypeName()));
    assertEquals(functionType, classTypeSignature.type);

    typeArguments = classTypeSignature.typeArguments;
    assertEquals(2, typeArguments.size());

    typeArgument = typeArguments.get(0);
    assertTrue(typeArgument.isClassTypeSignature());
    check_A_Y_ZZ(a, y, zz, typeArgument.asClassTypeSignature());

    typeArgument = typeArguments.get(1);
    assertTrue(typeArgument.isClassTypeSignature());
    check_A_Y_YY(a, y, yy, typeArgument.asClassTypeSignature());

    // type of 1st argument: Supplier<A$Y$ZZ<TT>>
    assertEquals(1, methodTypeSignature.typeSignatures.size());
    parameterSignature = methodTypeSignature.getParameterTypeSignature(0);
    assertNotNull(parameterSignature);
    assertTrue(parameterSignature.isFieldTypeSignature());
    assertTrue(parameterSignature.asFieldTypeSignature().isClassTypeSignature());
    classTypeSignature = parameterSignature.asFieldTypeSignature().asClassTypeSignature();
    DexType supplierType =
        factory.createType(DescriptorUtils.javaTypeToDescriptor(Supplier.class.getTypeName()));
    assertEquals(supplierType, classTypeSignature.type);
    typeArguments = classTypeSignature.typeArguments;
    assertEquals(1, typeArguments.size());
    typeArgument = typeArguments.get(0);
    assertTrue(typeArgument.isClassTypeSignature());
    check_A_Y_ZZ(a, y, zz, typeArgument.asClassTypeSignature());
  }

  private void check_A_Y(ClassSubject a, ClassSubject y, ClassTypeSignature signature) {
    assertEquals(a.getDexClass().type, signature.type);
    List<FieldTypeSignature> typeArguments = signature.typeArguments;
    assertEquals(1, typeArguments.size());
    FieldTypeSignature typeArgument = typeArguments.get(0);
    assertTrue(typeArgument.isTypeVariableSignature());
    assertEquals("T", typeArgument.asTypeVariableSignature().typeVariable);
    assertEquals(y.getDexClass().type, signature.innerTypeSignature.type);
  }

  private void check_A_Y_YY(
      ClassSubject a, ClassSubject y, ClassSubject yy, ClassTypeSignature signature) {
    check_A_Y(a, y, signature);
    assertEquals(yy.getDexClass().type, signature.innerTypeSignature.innerTypeSignature.type);
  }

  private void check_A_Y_ZZ(
      ClassSubject a, ClassSubject y, ClassSubject zz, ClassTypeSignature signature) {
    check_A_Y(a, y, signature);
    ClassTypeSignature innerMost = signature.innerTypeSignature.innerTypeSignature;
    assertEquals(zz.getDexClass().type, innerMost.type);
    List<FieldTypeSignature> typeArguments = innerMost.typeArguments;
    assertEquals(1, typeArguments.size());
    FieldTypeSignature typeArgument = typeArguments.get(0);
    assertTrue(typeArgument.isTypeVariableSignature());
    assertEquals("TT", typeArgument.asTypeVariableSignature().typeVariable);
  }
}

//
// TODO(b/129925954): Once unified, these would be stale comments.
// Borrowed from ...naming.signature.GenericSignatureRenamingTest
// and then extended a bit to explore more details, e.g., MethodTypeSignature.
//

class A<T> {
  class Y {

    class YY {}

    class ZZ<TT> extends YY {
      public YY yy;

      YY newYY(B... bs) {
        return new YY();
      }

      Function<ZZ<TT>, YY> convertToYY(Supplier<ZZ<TT>> zzSupplier) {
        return zz -> {
          if (System.currentTimeMillis() > 0) {
            return zzSupplier.get().newYY();
          } else {
            return zz.newYY();
          }
        };
      }
    }

    ZZ<T> zz() {
      return new ZZ<T>();
    }
  }

  class Z extends Y {}

  static class S {}

  Y newY() {
    return new Y();
  }

  Z newZ() {
    return new Z();
  }

  Y.ZZ<T> newZZ() {
    return new Y().zz();
  }
}

class B<T extends A<T>> {}

class CY<T extends A<T>.Y> {}

class CYY<T extends A<T>.Y> extends CY<T> {}
