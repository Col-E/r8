// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.D8CommandParser.OrderedClassFileResourceProvider;
import com.android.tools.r8.origin.Origin;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class OrderedClassFileResourceProviderTest extends TestBase {
  class SimpleClassFileResourceProvider implements ClassFileResourceProvider {

    private final Set<String> descriptors;
    private final ProgramResource fixedProgramResource;

    SimpleClassFileResourceProvider(int id, Set<String> descriptors) {
      this.descriptors = descriptors;
      this.fixedProgramResource = new SimpleProgramResource(id);
    }

    @Override
    public Set<String> getClassDescriptors() {
      return descriptors;
    }

    @Override
    public ProgramResource getProgramResource(String descriptor) {
      return fixedProgramResource;
    }
  }

  class SimpleProgramResource implements ProgramResource {

    private final Origin origin;

    SimpleProgramResource(int id) {
      origin = new SimpleOrigin(id);
    }

    @Override
    public Kind getKind() {
      return null;
    }

    @Override
    public InputStream getByteStream() throws ResourceException {
      return null;
    }

    @Override
    public Set<String> getClassDescriptors() {
      return null;
    }

    @Override
    public Origin getOrigin() {
      return origin;
    }
  }

  public class SimpleOrigin extends Origin {

    private final int id;

    private SimpleOrigin(int index) {
      super(root());
      this.id = index;
    }

    int getId() {
      return id;
    }

    @Override
    public String part() {
      return "Test";
    }
  }

  @Test
  public void test() {
    OrderedClassFileResourceProvider.Builder builder = OrderedClassFileResourceProvider.builder();
    builder.addClassFileResourceProvider(new SimpleClassFileResourceProvider(1, ImmutableSet.of(
        "L/a/a/a", "L/a/a/b", "L/a/a/c"
    )));
    builder.addClassFileResourceProvider(new SimpleClassFileResourceProvider(2, ImmutableSet.of(
        "L/a/a/b", "L/a/a/c", "L/a/a/d"
        )));
    ClassFileResourceProvider provider = builder.build();
    assertEquals(
        ImmutableSet.of("L/a/a/a", "L/a/a/b", "L/a/a/c", "L/a/a/d"),
        provider.getClassDescriptors());

    Map<String, Integer> expectations = ImmutableMap.of(
        "L/a/a/a", 1,
        "L/a/a/b", 1,
        "L/a/a/c", 1,
        "L/a/a/d", 2
    );
    expectations.forEach((descriptor, id) ->
        assertEquals(
            (int) id,
            ((SimpleOrigin) provider.getProgramResource(descriptor).getOrigin()).getId()));
  }
}
