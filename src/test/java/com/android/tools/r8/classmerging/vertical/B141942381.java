// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.vertical;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class B141942381 extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public B141942381(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJVM() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("true");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(B141942381.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .addKeepAttributes("Signatures")
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .enableNeverClassInliningAnnotations()
        .enableNoAccessModificationAnnotationsForMembers()
        .addDontObfuscate()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("true");
  }

  private void inspect(CodeInspector inspector) {
    // Merged to BoxValueImpl
    ClassSubject boxValue = inspector.clazz(BoxValue.class);
    assertThat(boxValue, not(isPresent()));

    // Merged to BoxImpl.
    ClassSubject box = inspector.clazz(Box.class);
    assertThat(box, not(isPresent()));

    ClassSubject boxImpl = inspector.clazz(BoxImpl.class);
    assertThat(boxImpl, isPresent());
    FieldSubject storage = boxImpl.uniqueFieldWithOriginalName("_storage");
    assertThat(storage, isPresent());

    MethodSubject set = boxImpl.uniqueMethodWithOriginalName("set");
    assertThat(set, isPresent());

    assertEquals(
        set.getMethod().getReference().proto.parameters.values[0],
        storage.getField().getReference().type.toBaseType(inspector.getFactory()));
  }

  static class TestClass {
    public static void main(String... args) {
      BoxImpl impl = new BoxImpl();
      BoxValueImpl v = new BoxValueImpl();
      impl.set(v);
      System.out.println(impl.getFirst() == v);
    }
  }

  static abstract class Box<T extends BoxValue> {
    @NoAccessModification
    @SuppressWarnings("unchecked")
    private T[] _storage = (T[]) (new BoxValue[1]);

    void set(T node) {
      _storage[0] = node;
    }

    T getFirst() {
      return _storage[0];
    }
  }

  @NeverClassInline
  static class BoxImpl extends Box<BoxValueImpl> {
    BoxImpl() {}
  }

  interface BoxValue {}

  @NeverClassInline
  static class BoxValueImpl implements BoxValue {}
}
