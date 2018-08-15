// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package twrcloseresource;

import java.util.jar.JarFile;

public class TwrCloseResourceTest implements Iface {
  public static void main(String[] args) {
    TwrCloseResourceTest o = new TwrCloseResourceTest();
    o.foo(args[0]);
    o.iFoo(args[0]);
    bar(args[0]);
    Iface.iBar(args[0]);
  }

  synchronized void foo(String arg) {
    try {
      try (JarFile a = new JarFile(arg)) {
        System.out.println("A");
      } catch (Exception e) {
        System.out.println("B");
        try (JarFile a = new JarFile(arg)) {
          System.out.println("C");
        }
        System.out.println("D");
        throw new RuntimeException();
      }
      try (JarFile a = new JarFile(arg)) {
        System.out.println("E");
      }
    } catch (Exception e) {
      System.out.println("F");
    }
    try (JarFile a = new JarFile(arg)) {
      System.out.println("G");
      try (JarFile b = new JarFile(arg)) {
        System.out.println("H");
      } finally {
        System.out.println("I");
        throw new RuntimeException();
      }
    } catch (Exception e) {
      System.out.println("J");
    }
    System.out.println("K");
  }

  static synchronized void bar(String arg) {
    try (JarFile a = new JarFile(arg)) {
      System.out.println("1");
      throw new RuntimeException();
    } catch (Exception e) {
      System.out.println("2");
    }
    try (JarFile a = new JarFile(arg)) {
      System.out.println("3");
      throw new RuntimeException();
    } catch (Exception e) {
      System.out.println("4");
    }
    try (JarFile a = new JarFile(arg)) {
      System.out.println("5");
      throw new RuntimeException();
    } catch (Exception e) {
      System.out.println("6");
    }
    try (JarFile a = new JarFile(arg)) {
      System.out.println("7");
      throw new RuntimeException();
    } catch (Exception e) {
      System.out.println("8");
    }
    System.out.println("99");
  }
}

interface Iface {
  default void iFoo(String arg) {
    try {
      try (JarFile a = new JarFile(arg)) {
        System.out.println("iA");
      } catch (Exception e) {
        System.out.println("iB");
        try (JarFile a = new JarFile(arg)) {
          System.out.println("iC");
        }
        System.out.println("iD");
        throw new RuntimeException();
      }
      try (JarFile a = new JarFile(arg)) {
        System.out.println("iE");
      }
    } catch (Exception e) {
      System.out.println("iF");
    }
    try (JarFile a = new JarFile(arg)) {
      System.out.println("iG");
      try (JarFile b = new JarFile(arg)) {
        System.out.println("iH");
      } finally {
        System.out.println("iI");
        throw new RuntimeException();
      }
    } catch (Exception e) {
      System.out.println("iJ");
    }
    System.out.println("iK");
  }

  static void iBar(String arg) {
    try (JarFile a = new JarFile(arg)) {
      System.out.println("i1");
      throw new RuntimeException();
    } catch (Exception e) {
      System.out.println("i2");
    }
    try (JarFile a = new JarFile(arg)) {
      System.out.println("i3");
      throw new RuntimeException();
    } catch (Exception e) {
      System.out.println("i4");
    }
    try (JarFile a = new JarFile(arg)) {
      System.out.println("i5");
      throw new RuntimeException();
    } catch (Exception e) {
      System.out.println("i6");
    }
    try (JarFile a = new JarFile(arg)) {
      System.out.println("i7");
      throw new RuntimeException();
    } catch (Exception e) {
      System.out.println("i8");
    }
    System.out.println("i99");
  }
}
