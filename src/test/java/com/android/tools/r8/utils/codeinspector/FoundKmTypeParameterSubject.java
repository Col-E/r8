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

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof FoundKmTypeParameterSubject)) {
      return false;
    }
    KmTypeParameter other = ((FoundKmTypeParameterSubject) obj).kmTypeParameter;
    if (!kmTypeParameter.getName().equals(other.getName())
        || kmTypeParameter.getId() != other.getId()
        || kmTypeParameter.getFlags() != other.getFlags()
        || kmTypeParameter.getVariance() != other.getVariance()) {
      return false;
    }
    if (kmTypeParameter.getUpperBounds().size() != other.getUpperBounds().size()) {
      return false;
    }
    for (int i = 0; i < kmTypeParameter.getUpperBounds().size(); i++) {
      if (!KmTypeSubject.areEqual(
          kmTypeParameter.getUpperBounds().get(i), other.getUpperBounds().get(i), true)) {
        return false;
      }
    }
    return true;
  }
}
