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

/**
 * Base interface for application resources.
 *
 * Resources are inputs to the compilation that are provided from outside sources, e.g., the
 * command-line interface or API clients such as gradle. Each resource has an associated
 * {@link Origin} which is some opaque description of where the resource comes from. The D8/R8
 * compiler does not assume any particular structure of origin and does not rely on it for
 * compilation. The origin will be provided to diagnostics handlers so that they may detail what
 * resource was cause of some particular error.
 *
 * The D8/R8 compilers uses default implementations for various file-system resources, but the
 * client is free to provide their own.
 */
public interface Resource {

  /**
   * Get the origin of the resource.
   *
   * The origin is a description of where the resource originates from. The client is free to define
   * what that means for a particular resource.
   */
  Origin getOrigin();

  // Deprecated API: See StringResource and ProgramResource.

  @Deprecated
  static Resource fromFile(Path file) {
    return new FileResource(file);
  }

  @Deprecated
  static Resource fromBytes(Origin origin, byte[] bytes) {
    return new ByteResource(origin, bytes);
  }

  @Deprecated
  static Resource fromBytes(Origin origin, byte[] bytes, Set<String> typeDescriptors) {
    return new ByteResource(origin, bytes, typeDescriptors);
  }

  @Deprecated
  InputStream getStream() throws IOException;

  @Deprecated
  Set<String> getClassDescriptors();

  @Deprecated
  class FileResource implements Resource {
    final Origin origin;
    final Path file;

    private FileResource(Path file) {
      assert file != null;
      origin = new PathOrigin(file);
      this.file = file;
    }

    @Override
    public Origin getOrigin() {
      return origin;
    }

    @Override
    @Deprecated
    public InputStream getStream() throws IOException {
      return new FileInputStream(file.toFile());
    }

    @Override
    @Deprecated
    public Set<String> getClassDescriptors() {
      return null;
    }
  }

  @Deprecated
  class ByteResource implements Resource {
    final Origin origin;
    final byte[] bytes;
    final Set<String> classDescriptors;

    private ByteResource(Origin origin, byte[] bytes) {
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
    @Deprecated
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
