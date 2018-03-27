// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution;

public class PublicFieldInnerClassTest {
  public static final Class<?>[] CLASSES = {
      PrivateBase.class,
      PrivateSubclass.class,
      PackageBase.class,
      PackageSubclass.class,
      ProtectedBase.class,
      ProtectedSubclass.class,
      PublicBase.class,
      PublicSubclass.class,
      PublicFieldInnerClassTest.class
  };

  private static class PrivateBase {
    public int value;
  }

  private static class PrivateSubclass extends PrivateBase {
  }

  static class PackageBase {
    public int value;
  }

  private static class PackageSubclass extends PackageBase {
  }

  protected static class ProtectedBase {
    public int value;
  }

  private static class ProtectedSubclass extends ProtectedBase {
  }

  public static class PublicBase {
    public int value;
  }

  private static class PublicSubclass extends PublicBase {
  }

  private static int getPrivate(PrivateSubclass instance) {
    return instance.value;
  }

  private static int getPackage(PackageSubclass instance) {
    return instance.value;
  }

  private static int getProtected(ProtectedSubclass instance) {
    return instance.value;
  }

  private static int getPublic(PublicSubclass instance) {
    return instance.value;
  }

  public static void main(String[] args) {
    System.out.println(getPrivate(new PrivateSubclass()));
    System.out.println(getPackage(new PackageSubclass()));
    System.out.println(getProtected(new ProtectedSubclass()));
    System.out.println(getPublic(new PublicSubclass()));
  }
}
