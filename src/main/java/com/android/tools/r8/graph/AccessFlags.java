// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Access flags common to classes, methods and fields. */
public abstract class AccessFlags<T extends AccessFlags<T>> implements StructuralItem<T> {

  protected static final int BASE_FLAGS
      = Constants.ACC_PUBLIC
      | Constants.ACC_PRIVATE
      | Constants.ACC_PROTECTED
      | Constants.ACC_STATIC
      | Constants.ACC_FINAL
      | Constants.ACC_SYNTHETIC;

  // Ordered list of flag names. Must be consistent with getPredicates.
  private static final List<String> NAMES = ImmutableList.of(
      "public",
      "private",
      "protected",
      "static",
      "final",
      "synthetic"
  );

  // Get ordered list of flag predicates. Must be consistent with getNames.
  protected List<BooleanSupplier> getPredicates() {
    return ImmutableList.of(
        this::isPublic,
        this::isPrivate,
        this::isProtected,
        this::isStatic,
        this::isFinal,
        this::isSynthetic);
  }

  // Get ordered list of flag names. Must be consistent with getPredicates.
  protected List<String> getNames() {
    return NAMES;
  }

  protected int originalFlags;
  protected int modifiedFlags;

  protected AccessFlags(int originalFlags, int modifiedFlags) {
    this.originalFlags = originalFlags;
    this.modifiedFlags = modifiedFlags;
  }

  protected static <T extends AccessFlags<T>> void specify(StructuralSpecification<T, ?> spec) {
    spec.withInt(a -> a.originalFlags).withInt(a -> a.modifiedFlags);
  }

  @Override
  public StructuralMapping<T> getStructuralMapping() {
    return AccessFlags::specify;
  }

  public abstract T copy();

  @Override
  public abstract T self();

  public int materialize() {
    return modifiedFlags;
  }

  public abstract int getAsCfAccessFlags();

  public abstract int getAsDexAccessFlags();

  public final int getOriginalAccessFlags() {
    return originalFlags;
  }

  public ClassAccessFlags asClassAccessFlags() {
    return null;
  }

  public MethodAccessFlags asMethodAccessFlags() {
    return null;
  }

  public FieldAccessFlags asFieldAccessFlags() {
    return null;
  }

  @Override
  public boolean equals(Object object) {
    if (object instanceof AccessFlags) {
      AccessFlags other = (AccessFlags) object;
      return originalFlags == other.originalFlags && modifiedFlags == other.modifiedFlags;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return originalFlags | modifiedFlags;
  }

  public boolean isMoreVisibleThan(
      AccessFlags<?> other, String packageNameThis, String packageNameOther) {
    int visibilityOrdinal = getVisibilityOrdinal();
    if (visibilityOrdinal > other.getVisibilityOrdinal()) {
      return true;
    }
    return visibilityOrdinal == other.getVisibilityOrdinal()
        && isVisibilityDependingOnPackage()
        && !packageNameThis.equals(packageNameOther);
  }

  public int getVisibilityOrdinal() {
    // public > protected > package > private
    if (isPublic()) {
      return 3;
    }
    if (isProtected()) {
      return 2;
    }
    if (isPrivate()) {
      return 0;
    }
    // Package-private
    return 1;
  }

  public boolean isVisibilityDependingOnPackage() {
    return getVisibilityOrdinal() == 1 || getVisibilityOrdinal() == 2;
  }

  public boolean isPackagePrivate() {
    return !isPublic() && !isPrivate() && !isProtected();
  }

  public boolean isPackagePrivateOrProtected() {
    return !isPublic() && !isPrivate();
  }

  public boolean isPublic() {
    return isSet(Constants.ACC_PUBLIC);
  }

  public void setPublic() {
    assert !isPrivate() && !isProtected();
    set(Constants.ACC_PUBLIC);
  }

  public void unsetPublic() {
    unset(Constants.ACC_PUBLIC);
  }

  public boolean isPrivate() {
    return isSet(Constants.ACC_PRIVATE);
  }

  public void setPrivate() {
    assert !isPublic() && !isProtected();
    set(Constants.ACC_PRIVATE);
  }

  public void unsetPrivate() {
    unset(Constants.ACC_PRIVATE);
  }

  public boolean isProtected() {
    return isSet(Constants.ACC_PROTECTED);
  }

  public void setProtected() {
    assert !isPublic() && !isPrivate();
    set(Constants.ACC_PROTECTED);
  }

  public void unsetProtected() {
    unset(Constants.ACC_PROTECTED);
  }

  public boolean isStatic() {
    return isSet(Constants.ACC_STATIC);
  }

  public void setStatic() {
    set(Constants.ACC_STATIC);
  }

  public boolean isOpen() {
    return !isFinal();
  }

  public boolean isFinal() {
    return isSet(Constants.ACC_FINAL);
  }

  public void setFinal() {
    set(Constants.ACC_FINAL);
  }

  public T unsetFinal() {
    unset(Constants.ACC_FINAL);
    return self();
  }

  public boolean isSynthetic() {
    return isSet(Constants.ACC_SYNTHETIC);
  }

  public void setSynthetic() {
    set(Constants.ACC_SYNTHETIC);
  }

  public T unsetSynthetic() {
    unset(Constants.ACC_SYNTHETIC);
    return self();
  }

  public void demoteFromSynthetic() {
    demote(Constants.ACC_SYNTHETIC);
  }

  public void promoteToFinal() {
    promote(Constants.ACC_FINAL);
  }

  public T demoteFromFinal() {
    demote(Constants.ACC_FINAL);
    return self();
  }

  public boolean isPromotedFromPrivateToPublic() {
    return isDemoted(Constants.ACC_PRIVATE) && isPromoted(Constants.ACC_PUBLIC);
  }

  public boolean isPromotedToPublic() {
    return isPromoted(Constants.ACC_PUBLIC);
  }

  public void promoteToPublic() {
    demote(Constants.ACC_PRIVATE | Constants.ACC_PROTECTED);
    promote(Constants.ACC_PUBLIC);
  }

  public T withPublic() {
    T newAccessFlags = copy();
    newAccessFlags.promoteToPublic();
    return newAccessFlags;
  }

  public void promoteToStatic() {
    promote(Constants.ACC_STATIC);
  }

  private boolean wasSet(int flag) {
    return isSet(originalFlags, flag);
  }

  protected boolean isSet(int flag) {
    return isSet(modifiedFlags, flag);
  }

  public static boolean isSet(int flag, int flags) {
    return (flags & flag) != 0;
  }

  protected void set(int flag) {
    originalFlags |= flag;
    modifiedFlags |= flag;
  }

  protected void unset(int flag) {
    originalFlags &= ~flag;
    modifiedFlags &= ~flag;
  }

  protected boolean isDemoted(int flag) {
    return wasSet(flag) && !isSet(flag);
  }

  protected boolean isPromoted(int flag) {
    return !wasSet(flag) && isSet(flag);
  }

  protected void promote(int flag) {
    modifiedFlags |= flag;
  }

  protected void demote(int flag) {
    modifiedFlags &= ~flag;
  }

  public String toSmaliString() {
    return toStringInternal(true);
  }

  @Override
  public String toString() {
    return toStringInternal(false);
  }

  private String toStringInternal(boolean ignoreSuper) {
    List<String> names = getNames();
    List<BooleanSupplier> predicates = getPredicates();
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < names.size(); i++) {
      if (predicates.get(i).getAsBoolean()) {
        if (!ignoreSuper || !names.get(i).equals("super")) {
          if (builder.length() > 0) {
            builder.append(' ');
          }
          builder.append(names.get(i));
        }
      }
    }
    return builder.toString();
  }

  abstract static class BuilderBase<B extends BuilderBase<B, F>, F extends AccessFlags<F>> {

    protected F flags;

    BuilderBase(F flags) {
      this.flags = flags;
    }

    public B setPackagePrivate() {
      assert flags.isPackagePrivate();
      return self();
    }

    public B setPrivate(boolean value) {
      if (value) {
        flags.setPrivate();
      } else {
        flags.unsetPrivate();
      }
      return self();
    }

    public B setProtected(boolean value) {
      if (value) {
        flags.setProtected();
      } else {
        flags.unsetProtected();
      }
      return self();
    }

    public B setPublic() {
      return setPublic(true);
    }

    public B setPublic(boolean value) {
      if (value) {
        flags.setPublic();
      } else {
        flags.unsetPublic();
      }
      return self();
    }

    public B setStatic() {
      flags.setStatic();
      return self();
    }

    public B setSynthetic() {
      flags.setSynthetic();
      return self();
    }

    public F build() {
      return flags;
    }

    public abstract B self();
  }
}
