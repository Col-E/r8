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
public interface Resource {

  /**
   * Create an application resource for a given file.
   *
   * <p>The origin of a file resource is the path of the file.
   */
  static Resource fromFile(Path file) {
    return new FileResource(file);
  }

  /**
   * Create an application resource for a given content.
   *
   * <p>The origin of a byte resource must be supplied upon construction. If no reasonable origin
   * exits, use {@code Origin.unknown()}.
   */
  static Resource fromBytes(Origin origin, byte[] bytes) {
    return new ByteResource(origin, bytes);
  }

  /**
   * Create an application resource for a given content and type descriptor.
   *
   * @deprecated Moved class descriptors to ProgramResource.
   */
  @Deprecated
  static Resource fromBytes(Origin origin, byte[] bytes, Set<String> typeDescriptors) {
    return new ByteResource(origin, bytes, typeDescriptors);
  }

  /** Get the origin of the resource. */
  Origin getOrigin();

  /** Get the resource as a stream. */
  InputStream getStream() throws IOException;

  /** @deprecated Moved to ProgramResource. */
  @Deprecated
  Set<String> getClassDescriptors();

  /** File-based application resource. */
  class FileResource implements Resource {
    final Origin origin;
    final Path file;

    FileResource(Path file) {
      assert file != null;
      origin = new PathOrigin(file);
      this.file = file;
    }

    @Override
    public Origin getOrigin() {
      return origin;
    }

    @Override
    public InputStream getStream() throws IOException {
      return new FileInputStream(file.toFile());
    }

    @Override
    @Deprecated
    public Set<String> getClassDescriptors() {
      return null;
    }
  }

  /** Byte-content based application resource. */
  class ByteResource implements Resource {
    final Origin origin;
    final byte[] bytes;
    final Set<String> classDescriptors;

    ByteResource(Origin origin, byte[] bytes) {
      assert bytes != null;
      this.origin = origin;
      this.bytes = bytes;
      classDescriptors = null;
    }

    /** Deprecated: Moved class descriptors to ProgramResource. */
    @Deprecated
    ByteResource(Origin origin, byte[] bytes, Set<String> classDescriptors) {
      assert bytes != null;
      this.origin = origin;
      this.bytes = bytes;
      this.classDescriptors = classDescriptors;
    }

    @Override
    public InputStream getStream() throws IOException {
      return new ByteArrayInputStream(bytes);
    }

    @Override
    public Origin getOrigin() {
      return origin;
    }

    @Override
    @Deprecated
    public Set<String> getClassDescriptors() {
      return classDescriptors;
    }
  }
}
