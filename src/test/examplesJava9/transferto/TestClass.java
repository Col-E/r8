// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package transferto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class TestClass {
  public static void main(String[] args) throws IOException {
    transferTo();
    transferToOverride();
  }

  public static void transferTo() throws IOException {
    String initialString = "Hello World!";
    System.out.println(initialString);

    try (InputStream inputStream = new ByteArrayInputStream(initialString.getBytes());
        ByteArrayOutputStream targetStream = new ByteArrayOutputStream()) {
      inputStream.transferTo(targetStream);
      String copied = new String(targetStream.toByteArray());
      System.out.println(copied);
    }
  }

  public static void transferToOverride() throws IOException {
    String initialString = "Hello World!";
    System.out.println(initialString);

    try (MyInputStream inputStream = new MyInputStream(initialString.getBytes());
        ByteArrayOutputStream targetStream = new ByteArrayOutputStream()) {
      inputStream.transferTo(targetStream);
      String copied = new String(targetStream.toByteArray());
      System.out.println(copied);
    }
  }
}
