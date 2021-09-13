// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.naming.mappinginformation.MappingInformation;
import com.android.tools.r8.naming.mappinginformation.UnknownJsonMappingInformation;
import com.android.tools.r8.retrace.RetraceUnknownJsonMappingInformationResult;
import com.android.tools.r8.retrace.RetraceUnknownMappingInformationElement;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.stream.Stream;

public class RetraceUnknownJsonMappingInformationResultImpl
    implements RetraceUnknownJsonMappingInformationResult {

  private final List<UnknownJsonMappingInformation> elements;

  private RetraceUnknownJsonMappingInformationResultImpl(
      List<UnknownJsonMappingInformation> elements) {
    this.elements = elements;
  }

  @Override
  public Stream<RetraceUnknownMappingInformationElement> stream() {
    return elements.stream()
        .map(
            unknownJsonMappingInformation ->
                new RetraceUnknownMappingInformationElementImpl(
                    this, unknownJsonMappingInformation));
  }

  static RetraceUnknownJsonMappingInformationResult build(
      List<MappingInformation> mappingInformations) {
    ImmutableList.Builder<UnknownJsonMappingInformation> unknownBuilder = ImmutableList.builder();
    mappingInformations.forEach(
        info -> {
          if (info.isUnknownJsonMappingInformation()) {
            unknownBuilder.add(info.asUnknownJsonMappingInformation());
          }
        });
    return new RetraceUnknownJsonMappingInformationResultImpl(unknownBuilder.build());
  }
}
