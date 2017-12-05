// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.origin.Origin;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/** Represents program application resources. */
public class ProgramResource implements Resource {

  /** Type of program-format kinds. */
  public enum Kind {
    /** Format-kind for Java class-file resources. */
    CF,
    /** Format-kind for Android dex-file resources. */
    DEX,
  }

  private final Kind kind;
  private final Resource resource;
  private final Set<String> classDescriptors;

  public ProgramResource(Kind kind, Resource resource, Set<String> classDescriptors) {
    assert !(resource instanceof ProgramResource);
    this.kind = kind;
    this.resource = resource;
    this.classDescriptors = classDescriptors;
  }

  @Deprecated
  public ProgramResource(Kind kind, Resource resource) {
    this(kind, resource, resource.getClassDescriptors());
  }

  /** Get the program format-kind of the resource. */
  public Kind getKind() {
    return kind;
  }

  @Override
  public Origin getOrigin() {
    return resource.getOrigin();
  }

  @Override
  public InputStream getStream() throws IOException {
    return resource.getStream();
  }

  /**
   * Get the set of class descriptors for classes defined by this resource.
   *
   * <p>This is not deprecated and will remain after Resource::getClassDescriptors is removed.
   */
  @Override // Need to keep this to not trip up error prone until the base method is gone.
  public Set<String> getClassDescriptors() {
    return classDescriptors;
  }
}
