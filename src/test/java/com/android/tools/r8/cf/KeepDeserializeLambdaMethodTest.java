// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

class KeepDeserializeLambdaMethodTest {
  static final String LAMBDA_MESSAGE = "[I'm the lambda.]";

  static void invokeLambda(Object o)
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    Method runMethod = o.getClass().getMethod("run");
    runMethod.invoke(o);
  }

  static Serializable getLambda() {
    return (Runnable & Serializable) () -> System.out.println("base lambda");
  }
}

class KeepDeserializeLambdaMethodTestDex extends KeepDeserializeLambdaMethodTest {
  public static void main(String[] args) throws Exception {
    invokeLambda(getLambda());
    invokeLambda(
        (Runnable & Serializable)
            () -> System.out.println(KeepDeserializeLambdaMethodTest.LAMBDA_MESSAGE));
  }
}

class KeepDeserializeLambdaMethodTestCf extends KeepDeserializeLambdaMethodTest {

  public static void main(String[] args) throws Exception {
    invokeLambda(roundtrip(getLambda()));
    invokeLambda(roundtrip((Runnable & Serializable) () -> System.out.println(LAMBDA_MESSAGE)));
  }

  private static Object roundtrip(Serializable myLambda)
      throws IOException, ClassNotFoundException {
    byte[] bytes;
    {
      ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
      ObjectOutputStream out = new ObjectOutputStream(byteStream);
      out.writeObject(myLambda);
      out.close();
      bytes = byteStream.toByteArray();
    }
    Object o;
    {
      ByteArrayInputStream byteStream = new ByteArrayInputStream(bytes);
      ObjectInputStream in = new ObjectInputStream(byteStream);
      o = in.readObject();
      in.close();
    }
    return o;
  }
}
