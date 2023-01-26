// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package file;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FilesMain {

  public static void main(String[] args) throws IOException {
    Path temp = Files.createTempFile("temp", ".txt");
    Files.writeString(temp, "content:", StandardOpenOption.WRITE);
    Files.writeString(temp, "this", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    System.out.println(Files.readString(temp));
    System.out.println(Files.readString(temp, StandardCharsets.UTF_8));
    Files.deleteIfExists(temp);
  }
}
