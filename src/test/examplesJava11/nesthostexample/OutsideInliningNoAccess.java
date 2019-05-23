// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package nesthostexample;

public class OutsideInliningNoAccess extends NestHostInlining.EmptyNoPrivAccess {

  public static void main(String[] args) {
    System.out.println("OutsideInliningNoAccess");
  }
}
