// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.configuration;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;

public class WrongUseOfImplementsOrExtendsWarningTest extends TestBase {

  @Test
  public void testCorrectUseOfExtends() throws Exception {
    CodeInspector inspector =
        testForR8(Backend.DEX)
            .addInnerClasses(WrongUseOfImplementsOrExtendsWarningTest.class)
            .addKeepRules(
                "-keep class " + B.class.getTypeName() + " extends " + A.class.getTypeName())
            .compile()
            .assertNoMessages()
            .inspector();
    assertThat(inspector.clazz(B.class), isPresent());
  }

  @Test
  public void testWrongUseOfExtends() throws Exception {
    CodeInspector inspector =
        testForR8(Backend.DEX)
            .addInnerClasses(WrongUseOfImplementsOrExtendsWarningTest.class)
            .addKeepRules(
                "-keep class " + B.class.getTypeName() + " extends " + I.class.getTypeName())
            .compile()
            .assertWarningMessageThatMatches(
                containsString("uses extends but actually matches implements"))
            .inspector();
    assertThat(inspector.clazz(B.class), isPresent());
  }

  @Test
  public void testUseOfExtendsWithWildcards() throws Exception {
    CodeInspector inspector =
        testForR8(Backend.DEX)
            .addInnerClasses(WrongUseOfImplementsOrExtendsWarningTest.class)
            .addKeepRules(
                "-keep class " + B.class.getTypeName() + " extends **$" + I.class.getSimpleName())
            .compile()
            .assertNoMessages()
            .inspector();
    assertThat(inspector.clazz(B.class), isPresent());
  }

  @Test
  public void testCorrectUseOfImplements() throws Exception {
    CodeInspector inspector =
        testForR8(Backend.DEX)
            .addInnerClasses(WrongUseOfImplementsOrExtendsWarningTest.class)
            .addKeepRules(
                "-keep class " + B.class.getTypeName() + " implements " + I.class.getTypeName())
            .compile()
            .assertNoMessages()
            .inspector();
    assertThat(inspector.clazz(B.class), isPresent());
  }

  @Test
  public void testWrongUseOfImplements() throws Exception {
    CodeInspector inspector =
        testForR8(Backend.DEX)
            .addInnerClasses(WrongUseOfImplementsOrExtendsWarningTest.class)
            .addKeepRules(
                "-keep class " + B.class.getTypeName() + " implements " + A.class.getTypeName())
            .compile()
            .assertWarningMessageThatMatches(
                containsString("uses implements but actually matches extends"))
            .inspector();
    assertThat(inspector.clazz(B.class), isPresent());
  }

  @Test
  public void testUseOfImplementsWithWildcards() throws Exception {
    CodeInspector inspector =
        testForR8(Backend.DEX)
            .addInnerClasses(WrongUseOfImplementsOrExtendsWarningTest.class)
            .addKeepRules(
                "-keep class "
                    + B.class.getTypeName()
                    + " implements **$"
                    + A.class.getSimpleName())
            .compile()
            .assertNoMessages()
            .inspector();
    assertThat(inspector.clazz(B.class), isPresent());
  }

  static class A {}

  static class B extends A implements I {}

  interface I {}
}
