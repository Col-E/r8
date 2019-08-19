// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import java.util.List;

public class ListBackportJava9Main {

  public static void main(String[] args) throws Exception {
    testOf0();
    testOf1();
    testOf2();
    testOf10();
  }

  private static void testOf0() {
    List<Object> ofObject = List.of();
    List<Integer> ofInteger = List.of();
    assertTrue(ofObject instanceof List);
    assertTrue(ofObject.size() == 0);
    assertTrue(ofInteger instanceof List);
    assertTrue(ofInteger.size() == 0);
  }

  private static void testOf1() {
    List<Object> ofObject = List.of(new Object());
    List<Integer> ofInteger = List.of(1);
    assertTrue(ofObject instanceof List);
    assertTrue(ofObject.size() == 1);
    assertTrue(ofInteger instanceof List);
    assertTrue(ofInteger.size() == 1);
  }

  private static void testOf2() {
    List<Object> ofObject = List.of(new Object(), new Object());
    List<Integer> ofInteger = List.of(1, 2);
    List<Object> ofMixed = List.of(new Object(), 1);
    assertTrue(ofObject instanceof List);
    assertTrue(ofObject.size() == 2);
    assertTrue(ofInteger instanceof List);
    assertTrue(ofInteger.size() == 2);
    assertTrue(ofMixed instanceof List);
    assertTrue(ofMixed.size() == 2);
  }

  private static void testOf10() {
    Object e = new Object();
    List<Object> ofObject = List.of(e, e, e, e, e, e, e, e, e, e);
    List<Integer> ofInteger = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
    List<Object> ofMixed = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, e);
    assertTrue(ofObject instanceof List);
    assertTrue(ofObject.size() == 10);
    assertTrue(ofInteger instanceof List);
    assertTrue(ofInteger.size() == 10);
    assertTrue(ofMixed instanceof List);
    assertTrue(ofMixed.size() == 10);
  }

  private static void assertTrue(boolean value) {
    if (!value) {
      throw new AssertionError("Expected <true> but was <false>");
    }
  }
}
