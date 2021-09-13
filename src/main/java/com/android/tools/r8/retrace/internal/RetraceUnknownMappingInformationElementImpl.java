// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.naming.mappinginformation.UnknownJsonMappingInformation;
import com.android.tools.r8.retrace.RetraceUnknownJsonMappingInformationResult;
import com.android.tools.r8.retrace.RetraceUnknownMappingInformationElement;

public class RetraceUnknownMappingInformationElementImpl
    implements RetraceUnknownMappingInformationElement {

  private final RetraceUnknownJsonMappingInformationResult result;
  private final UnknownJsonMappingInformation mappingInformation;

  RetraceUnknownMappingInformationElementImpl(
      RetraceUnknownJsonMappingInformationResult result,
      UnknownJsonMappingInformation mappingInformation) {
    this.result = result;
    this.mappingInformation = mappingInformation;
  }

  @Override
  public String getIdentifier() {
    return mappingInformation.getId();
  }

  @Override
  public String getPayLoad() {
    return mappingInformation.getPayload();
  }

  @Override
  public RetraceUnknownJsonMappingInformationResult getRetraceResultContext() {
    return result;
  }
}
