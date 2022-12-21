// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.examples.jdk9;

import com.android.tools.r8.examples.JavaExampleClassProxy;
import java.nio.file.Path;

public class VarHandle {

  private static final String EXAMPLE_FILE = "examplesJava9/varhandle";

  public static final JavaExampleClassProxy VarHandleTests =
      new JavaExampleClassProxy(EXAMPLE_FILE, "varhandle/VarHandleTests");

  public static final JavaExampleClassProxy ArrayOfInt =
      new JavaExampleClassProxy(EXAMPLE_FILE, "varhandle/ArrayOfInt");
  public static final JavaExampleClassProxy ArrayOfLong =
      new JavaExampleClassProxy(EXAMPLE_FILE, "varhandle/ArrayOfLong");
  public static final JavaExampleClassProxy ArrayOfObject =
      new JavaExampleClassProxy(EXAMPLE_FILE, "varhandle/ArrayOfObject");

  public static final JavaExampleClassProxy InstanceIntField =
      new JavaExampleClassProxy(EXAMPLE_FILE, "varhandle/InstanceIntField");
  public static final JavaExampleClassProxy StaticIntField =
      new JavaExampleClassProxy(EXAMPLE_FILE, "varhandle/StaticIntField");
  public static final JavaExampleClassProxy IntFieldWithMethodHandle =
      new JavaExampleClassProxy(EXAMPLE_FILE, "varhandle/IntFieldWithMethodHandle");

  public static final JavaExampleClassProxy InstanceLongField =
      new JavaExampleClassProxy(EXAMPLE_FILE, "varhandle/InstanceLongField");

  public static final JavaExampleClassProxy InstanceBooleanField =
      new JavaExampleClassProxy(EXAMPLE_FILE, "varhandle/InstanceBooleanField");

  public static final JavaExampleClassProxy InstanceByteField =
      new JavaExampleClassProxy(EXAMPLE_FILE, "varhandle/InstanceByteField");

  public static final JavaExampleClassProxy InstanceShortField =
      new JavaExampleClassProxy(EXAMPLE_FILE, "varhandle/InstanceShortField");

  public static final JavaExampleClassProxy InstanceFloatField =
      new JavaExampleClassProxy(EXAMPLE_FILE, "varhandle/InstanceFloatField");

  public static final JavaExampleClassProxy InstanceDoubleField =
      new JavaExampleClassProxy(EXAMPLE_FILE, "varhandle/InstanceDoubleField");

  public static final JavaExampleClassProxy InstanceObjectField =
      new JavaExampleClassProxy(EXAMPLE_FILE, "varhandle/InstanceObjectField");

  public static final JavaExampleClassProxy InstanceStringField =
      new JavaExampleClassProxy(EXAMPLE_FILE, "varhandle/InstanceStringField");

  public static Path jar() {
    return JavaExampleClassProxy.examplesJar(EXAMPLE_FILE);
  }
}
