// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;

// Mock Path since Path is not present before Dex-8.
// File is present and it contains most of the utilities.
public class MockedPath implements Path {

  private MockedPath(File file) {
    this.wrappedFile = file;
  }

  public static Path of(File file, String... more) {
    // Delegate to File separator management.
    File current = file;
    for (String s : more) {
      current = new File(current.getPath(), s);
    }
    return new MockedPath(current);
  }

  public static Path of(String first, String... more) {
    return of(new File(first), more);
  }

  // MockedPath wraps the path in a file.
  private File wrappedFile;

  @Override
  public FileSystem getFileSystem() {
    throw new RuntimeException("Mocked Path does not implement getFileSystem");
  }

  @Override
  public boolean isAbsolute() {
    throw new RuntimeException("Mocked Path does not implement isAbsolute");
  }

  @Override
  public Path getRoot() {
    throw new RuntimeException("Mocked Path does not implement getRoot");
  }

  @Override
  public Path getFileName() {
    return of(wrappedFile.getName());
  }

  @Override
  public Path getParent() {
    return of(wrappedFile.getParent());
  }

  @Override
  public int getNameCount() {
    throw new RuntimeException("Mocked Path does not implement getNameCount");
  }

  @Override
  public Path getName(int index) {
    throw new RuntimeException("Mocked Path does not implement getName");
  }

  @Override
  public Path subpath(int beginIndex, int endIndex) {
    throw new RuntimeException("Mocked Path does not implement subpath");
  }

  @Override
  public boolean startsWith(Path other) {
    throw new RuntimeException("Mocked Path does not implement startsWith");
  }

  @Override
  public boolean endsWith(Path other) {
    throw new RuntimeException("Mocked Path does not implement endswith");
  }

  @Override
  public Path normalize() {
    throw new RuntimeException("Mocked Path does not implement normalize");
  }

  @Override
  public Path resolve(Path other) {
    return new MockedPath(new File(wrappedFile.getPath(), other.toString()));
  }

  @Override
  public Path resolve(String other) {
    return new MockedPath(new File(wrappedFile.getPath(), other));
  }

  @Override
  public Path relativize(Path other) {
    throw new RuntimeException("Mocked Path does not implement relativize");
  }

  @Override
  public URI toUri() {
    throw new RuntimeException("Mocked Path does not implement toUri");
  }

  @Override
  public Path toAbsolutePath() {
    throw new RuntimeException("Mocked Path does not implement toAbsolutePath");
  }

  @Override
  public Path toRealPath(LinkOption... options) throws IOException {
    throw new RuntimeException("Mocked Path does not implement toRealPath");
  }

  @Override
  public int compareTo(Path other) {
    throw new RuntimeException("Mocked Path does not implement compareTo");
  }

  @Override
  public String toString() {
    return wrappedFile.toString();
  }

  @Override
  public File toFile() {
    return wrappedFile;
  }

  // Compatibility with Files.

  public byte[] getAllBytes() throws IOException {
    FileInputStream fileInputStream = new FileInputStream(wrappedFile);
    // In android the result of file.length() is long
    // byte count of the file-content
    long byteLength = wrappedFile.length();
    byte[] filecontent = new byte[(int) byteLength];
    fileInputStream.read(filecontent, 0, (int) byteLength);
    return filecontent;
  }

  public OutputStream newOutputStream(boolean append) throws IOException {
    return new FileOutputStream(wrappedFile, append);
  }
}
