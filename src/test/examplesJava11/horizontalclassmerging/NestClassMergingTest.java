// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package horizontalclassmerging;

public class NestClassMergingTest {

  public static void main(String[] args) {
    NestHostA hostA = new NestHostA();
    new NestHostA.NestMemberA();
    new NestHostA.NestMemberB(hostA);
    NestHostB hostB = new NestHostB();
    new NestHostB.NestMemberA();
    new NestHostB.NestMemberB(hostB);
  }
}
