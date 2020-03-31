package com.android.tools.r8.utils.codeinspector;

import java.util.List;
import java.util.stream.Collectors;
import kotlinx.metadata.KmTypeParameter;
import kotlinx.metadata.KmVariance;

public class FoundKmTypeParameterSubject extends KmTypeParameterSubject {

  private final CodeInspector codeInspector;
  private final KmTypeParameter kmTypeParameter;

  public FoundKmTypeParameterSubject(CodeInspector codeInspector, KmTypeParameter kmTypeParameter) {
    this.codeInspector = codeInspector;
    this.kmTypeParameter = kmTypeParameter;
  }

  @Override
  public boolean isPresent() {
    return true;
  }

  @Override
  public boolean isRenamed() {
    return false;
  }

  @Override
  public boolean isSynthetic() {
    return false;
  }

  @Override
  public int getId() {
    return kmTypeParameter.getId();
  }

  @Override
  public int getFlags() {
    return kmTypeParameter.getFlags();
  }

  @Override
  public KmVariance getVariance() {
    return kmTypeParameter.getVariance();
  }

  @Override
  public List<KmTypeSubject> upperBounds() {
    return kmTypeParameter.getUpperBounds().stream()
        .map(kmType -> new KmTypeSubject(codeInspector, kmType))
        .collect(Collectors.toList());
  }
}
