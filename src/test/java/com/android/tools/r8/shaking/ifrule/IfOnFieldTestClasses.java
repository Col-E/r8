// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

class D1 {
}

class D2 {
}

class D {
}

class R1 {
  public static int id1 = 1;
  public static int id2 = 2;
}

class R2 {
  public static int id1 = 1;
  public static int id2 = 2;
}

class R {
  public static int id1 = 1;
  public static int id2 = 2;
}

interface I {
  void ack();
}

class Impl implements I {
  private int usedPrivateIntField;
  private int unusedPrivateIntField;
  public int unusedPublicIntField;
  public String unusedPublicStringField;

  public void ack() {
    System.out.println("ack(" + usedPrivateIntField++ + ")");
  }
}

class MainUsesR {
  public static void main(String[] args) {
    System.out.println(R.id1);
    //System.out.println(R.id2);
  }
}

class MainWithIf {
  public static void main(String[] args) {
    if (false) {
      System.out.println(R1.id1);
      System.out.println(R1.id2);
    } else {
      System.out.println(R2.id1);
      System.out.println(R2.id2);
    }
  }
}

class MainWithInner {

  public static class InnerR {
    public static int id1 = 1;
    public static int id2 = 2;
  }

  public static class InnerD {
  }

  public static void main(String[] args) {
    System.out.println(InnerR.id1);
    System.out.println(InnerR.id2);
  }
}

class MainUsesImpl {
  public static void main(String[] args) {
    I instance = new Impl();
    instance.ack();
  }
}
