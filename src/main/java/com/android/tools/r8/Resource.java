// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Set;

/** Represents application resources. */
public abstract class Resource {

  /** Origin of the resource. */
  public final Origin origin;

  protected Resource(Origin origin) {
    this.origin = origin;
  }

  /** Create an application resource for a given file. */
  public static Resource fromFile(Path file) {
    return new FileResource(file);
  }

  /** Create an application resource for a given content. */
  public static Resource fromBytes(Origin origin, byte[] bytes) {
    return fromBytes(origin, bytes, null);
  }

  /** Create an application resource for a given content and type descriptor. */
  public static Resource fromBytes(Origin origin, byte[] bytes, Set<String> typeDescriptors) {
    return new ByteResource(origin, bytes, typeDescriptors);
  }

  /**
   * Returns the set of class descriptors for classes represented
   * by the resource if known, or `null' otherwise.
   */
  public abstract Set<String> getClassDescriptors();

  /** Get the resource as a stream. */
  public abstract InputStream getStream() throws IOException;

  /**
   * File-based application resource.
   *
   * <p>The origin of a file resource is the path of the file.
   */
  private static class FileResource extends Resource {
    final Path file;

    FileResource(Path file) {
      super(new PathOrigin(file, Origin.root()));
      assert file != null;
      this.file = file;
    }

    @Override
    public Set<String> getClassDescriptors() {
      return null;
    }

    @Override
    public InputStream getStream() throws IOException {
      return new FileInputStream(file.toFile());
    }
  }

  /**
   * Byte-content based application resource.
   *
   * <p>The origin of a byte resource must be supplied upon construction. If no reasonable origin
   * exits, use {@code Origin.unknown()}.
   */
  private static class ByteResource extends Resource {
    final Set<String> classDescriptors;
    final byte[] bytes;

    ByteResource(Origin origin, byte[] bytes, Set<String> classDescriptors) {
      super(origin);
      assert bytes != null;
      this.classDescriptors = classDescriptors;
      this.bytes = bytes;
    }

    @Override
    public Set<String> getClassDescriptors() {
      return classDescriptors;
    }

    @Override
    public InputStream getStream() throws IOException {
      return new ByteArrayInputStream(bytes);
    }
  }
}
