// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.mappings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.RetraceFieldResult;
import com.android.tools.r8.retrace.Retracer;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.stream.Collectors;

public class FieldsWithSameMinifiedNameMapping implements MappingForTest {

  @Override
  public String mapping() {
    return StringUtils.lines(
        "foo.bar.Baz -> foo.bar.Baz:", "  java.lang.Object f1 -> a", "  java.lang.String f2 -> a");
  }

  public void inspect(Retracer retracer) {
    FieldReference f1FieldReference =
        Reference.field(
            Reference.classFromTypeName("foo.bar.Baz"),
            "f1",
            Reference.classFromTypeName("java.lang.Object"));
    FieldReference f2FieldReference =
        Reference.field(
            Reference.classFromTypeName("foo.bar.Baz"),
            "f2",
            Reference.classFromTypeName("java.lang.String"));

    FieldReference mappedF1FieldReference =
        Reference.field(
            Reference.classFromTypeName("foo.bar.Baz"),
            "a",
            Reference.classFromTypeName("java.lang.Object"));

    RetraceFieldResult result = retracer.retraceField(mappedF1FieldReference);
    assertFalse(result.isAmbiguous());

    List<FieldReference> retracedFields =
        result.stream()
            .map(f -> f.getField().asKnown().getFieldReference())
            .collect(Collectors.toList());
    assertEquals(ImmutableList.of(f1FieldReference), retracedFields);
  }
}
