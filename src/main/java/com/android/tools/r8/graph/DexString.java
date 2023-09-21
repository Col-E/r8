// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.lightir.LirConstant;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.IdentifierUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThrowingCharIterator;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralMapping;
import java.io.UTFDataFormatException;
import java.util.Arrays;
import java.util.NoSuchElementException;

public class DexString extends IndexedDexItem
    implements NamingLensComparable<DexString>, LirConstant {

  @SuppressWarnings("ReferenceEquality")
  public static boolean identical(DexString t1, DexString t2) {
    return t1 == t2;
  }

  public final boolean isIdenticalTo(DexString other) {
    return identical(this, other);
  }

  public static final DexString[] EMPTY_ARRAY = {};
  private static final int ARRAY_CHARACTER = '[';

  public final int size;  // size of this string, in UTF-16
  public final byte[] content;

  DexString(int size, byte[] content) {
    this.size = size;
    this.content = content;
  }

  DexString(String string) {
    this.size = string.length();
    this.content = encodeToMutf8(string);
  }

  public char getFirstByteAsChar() {
    return (char) content[0];
  }

  public boolean isEqualTo(String string) {
    if (size != string.length()) {
      return false;
    }
    int index = 0;
    ThrowingCharIterator<UTFDataFormatException> iterator = iterator();
    while (iterator.hasNext()) {
      int c;
      try {
        c = iterator.nextChar();
      } catch (UTFDataFormatException e) {
        return false;
      }
      if (c != string.charAt(index)) {
        return false;
      }
      index++;
    }
    assert index == size;
    return true;
  }

  @Override
  public DexString self() {
    return this;
  }

  public int size() {
    return size;
  }

  @Override
  public StructuralMapping<DexString> getStructuralMapping() {
    // Structural accept is never accessed as all accept methods are defined directly.
    throw new Unreachable();
  }

  public byte byteAt(int index) {
    return content[index];
  }

  /** DexString is a leaf item so we directly define its compareTo which avoids overhead. */
  @Override
  public int compareTo(DexString other) {
    return internalCompareTo(other);
  }

  @Override
  public int acceptCompareTo(DexString other, CompareToVisitor visitor) {
    return visitor.visitDexString(this, other);
  }

  @Override
  public void acceptHashing(HashingVisitor visitor) {
    visitor.visitDexString(this);
  }

  @Override
  public LirConstantOrder getLirConstantOrder() {
    return LirConstantOrder.STRING;
  }

  @Override
  public int internalLirConstantAcceptCompareTo(LirConstant other, CompareToVisitor visitor) {
    return acceptCompareTo((DexString) other, visitor);
  }

  @Override
  public void internalLirConstantAcceptHashing(HashingVisitor visitor) {
    visitor.visitDexString(this);
  }

  public ThrowingCharIterator<UTFDataFormatException> iterator() {
    return new ThrowingCharIterator<UTFDataFormatException>() {

      private int i = 0;

      @Override
      public char nextChar() throws UTFDataFormatException {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        char a = (char) (content[i++] & 0xff);
        assert a != 0;
        if (a < '\u0080') {
          return a;
        }
        if ((a & 0xe0) == 0xc0) {
          int b = content[i++] & 0xff;
          if ((b & 0xC0) == 0x80) {
            return (char) (((a & 0x1F) << 6) | (b & 0x3F));
          }
          throw new UTFDataFormatException("bad second byte");
        }
        if ((a & 0xf0) == 0xe0) {
          int b = content[i++] & 0xff;
          int c = content[i++] & 0xff;
          if ((b & 0xC0) == 0x80 && (c & 0xC0) == 0x80) {
            return (char) (((a & 0x0F) << 12) | ((b & 0x3F) << 6) | (c & 0x3F));
          }
          throw new UTFDataFormatException("bad second or third byte");
        }
        throw new UTFDataFormatException("bad byte");
      }

      @Override
      public boolean hasNext() {
        return i < content.length && (content[i] & 0xff) != 0;
      }
    };
  }

  @Override
  public int computeHashCode() {
    return size * 7 + Arrays.hashCode(content);
  }

  @Override
  public boolean computeEquals(Object other) {
    if (other instanceof DexString) {
      DexString o = (DexString) other;
      return size == o.size && Arrays.equals(content, o.content);
    }
    return false;
  }

  @Override
  public String toString() {
    try {
      return decode();
    } catch (UTFDataFormatException e) {
      throw new RuntimeException("Bad format", e);
    }
  }

  public String toASCIIString() {
    try {
      return StringUtils.toASCIIString(decode());
    } catch (UTFDataFormatException e) {
      throw new RuntimeException("Bad format", e);
    }
  }

  private String decode() throws UTFDataFormatException {
    char[] out = new char[size];
    int decodedLength = decodePrefix(out);
    return new String(out, 0, decodedLength);
  }

  // Inspired from /dex/src/main/java/com/android/dex/Mutf8.java
  public int decodePrefix(char[] out) throws UTFDataFormatException {
    int s = 0;
    int p = 0;
    int prefixLength = out.length;
    while (true) {
      char a = (char) (content[p++] & 0xff);
      if (a == 0) {
        return s;
      }
      out[s] = a;
      if (a < '\u0080') {
        if (++s == prefixLength) {
          return s;
        }
      } else if ((a & 0xe0) == 0xc0) {
        int b = content[p++] & 0xff;
        if ((b & 0xC0) != 0x80) {
          throw new UTFDataFormatException("bad second byte");
        }
        out[s] = (char) (((a & 0x1F) << 6) | (b & 0x3F));
        if (++s == prefixLength) {
          return s;
        }
      } else if ((a & 0xf0) == 0xe0) {
        int b = content[p++] & 0xff;
        int c = content[p++] & 0xff;
        if (((b & 0xC0) != 0x80) || ((c & 0xC0) != 0x80)) {
          throw new UTFDataFormatException("bad second or third byte");
        }
        out[s] = (char) (((a & 0x0F) << 12) | ((b & 0x3F) << 6) | (c & 0x3F));
        if (++s == prefixLength) {
          return s;
        }
      } else {
        throw new UTFDataFormatException("bad byte");
      }
    }
  }

  public int decodedHashCode() throws UTFDataFormatException {
    if (size == 0) {
      assert decode().hashCode() == 0;
      return 0;
    }
    int h = 0;
    int p = 0;
    while (true) {
      char a = (char) (content[p++] & 0xff);
      if (a == 0) {
        break;
      }
      if (a < '\u0080') {
        h = 31 * h + a;
      } else if ((a & 0xe0) == 0xc0) {
        int b = content[p++] & 0xff;
        if ((b & 0xC0) != 0x80) {
          throw new UTFDataFormatException("bad second byte");
        }
        h = 31 * h + (char) (((a & 0x1F) << 6) | (b & 0x3F));
      } else if ((a & 0xf0) == 0xe0) {
        int b = content[p++] & 0xff;
        int c = content[p++] & 0xff;
        if (((b & 0xC0) != 0x80) || ((c & 0xC0) != 0x80)) {
          throw new UTFDataFormatException("bad second or third byte");
        }
        h = 31 * h + (char) (((a & 0x0F) << 12) | ((b & 0x3F) << 6) | (c & 0x3F));
      } else {
        throw new UTFDataFormatException("bad byte");
      }
    }

    assert h == decode().hashCode();
    return h;
  }

  // Inspired from /dex/src/main/java/com/android/dex/Mutf8.java
  private static int countBytes(String string) {
    // We need an extra byte for the terminating '0'.
    int result = 1;
    for (int i = 0; i < string.length(); ++i) {
      result += countBytes(string.charAt(i));
      assert result > 0;
    }
    return result;
  }

  public static int countBytes(char ch) {
    if (ch != 0 && ch <= 127) { // U+0000 uses two bytes.
      return 1;
    }
    if (ch <= 2047) {
      return 2;
    }
    return 3;
  }

  // Inspired from /dex/src/main/java/com/android/dex/Mutf8.java
  public static byte[] encodeToMutf8(String string) {
    byte[] result = new byte[countBytes(string)];
    int offset = 0;
    for (int i = 0; i < string.length(); i++) {
      offset = encodeToMutf8(string.charAt(i), result, offset);
    }
    result[offset] = 0;
    return result;
  }

  public static int encodeToMutf8(char ch, byte[] array, int offset) {
    if (ch != 0 && ch <= 127) { // U+0000 uses two bytes.
      array[offset++] = (byte) ch;
    } else if (ch <= 2047) {
      array[offset++] = (byte) (0xc0 | (0x1f & (ch >> 6)));
      array[offset++] = (byte) (0x80 | (0x3f & ch));
    } else {
      array[offset++] = (byte) (0xe0 | (0x0f & (ch >> 12)));
      array[offset++] = (byte) (0x80 | (0x3f & (ch >> 6)));
      array[offset++] = (byte) (0x80 | (0x3f & ch));
    }
    return offset;
  }

  public void collectIndexedItems(IndexedItemCollection indexedItems) {
    indexedItems.addString(this);
  }

  @Override
  public int getOffset(ObjectToOffsetMapping mapping) {
    return mapping.getOffsetFor(this);
  }

  private int internalCompareTo(DexString other) {
    // Compare the bytes, as comparing UTF-8 encoded strings as strings of unsigned bytes gives
    // the same result as comparing the corresponding Unicode strings lexicographically by
    // codepoint. The only complication is the MUTF-8 encoding have the two byte encoding c0 80 of
    // the null character (U+0000) to allow embedded null characters.
    // Supplementary characters (unicode code points above U+FFFF) are always represented as
    // surrogate pairs and are compared using UTF-16 code units as per Java string semantics.
    int index = 0;
    while (true) {
      char b1 = (char) (content[index] & 0xff);
      char b2 = (char) (other.content[index] & 0xff);
      int diff = b1 - b2;
      if (diff != 0) {
        // Check if either string ends here.
        if (b1 == 0 || b2 == 0) {
          return diff;
        }
        // If either of the strings have the null character starting here, the null character
        // sort lowest.
        if ((b1 == 0xc0 && (content[index + 1] & 0xff) == 0x80) ||
            (b2 == 0xc0 && (other.content[index + 1] & 0xff) == 0x80)) {
          return b1 == 0xc0 && (content[index + 1] & 0xff) == 0x80 ? -1 : 1;
        }
        return diff;
      } else if (b1 == 0) {
        // Reached the end in both strings.
        return 0;
      }
      index++;
    }
  }

  public boolean isValidMethodName() {
    try {
      return DescriptorUtils.isValidMethodName(decode());
    } catch (UTFDataFormatException e) {
      return false;
    }
  }

  public boolean isValidFieldName() {
    try {
      return DescriptorUtils.isValidFieldName(decode());
    } catch (UTFDataFormatException e) {
      return false;
    }
  }

  public boolean isValidClassDescriptor() {
    try {
      return DescriptorUtils.isValidClassDescriptor(decode());
    } catch (UTFDataFormatException e) {
      return false;
    }
  }

  public static boolean isValidSimpleName(AndroidApiLevel apiLevel, String string) {
    // space characters are not allowed prior to Android R
    if (apiLevel.isLessThan(AndroidApiLevel.R)) {
      int cp;
      for (int i = 0; i < string.length(); ) {
        cp = string.codePointAt(i);
        if (IdentifierUtils.isUnicodeSpace(cp)) {
          return false;
        }
        i += Character.charCount(cp);
      }
    }
    return true;
  }

  public boolean isValidSimpleName(AndroidApiLevel apiLevel) {
    // space characters are not allowed prior to Android R
    if (apiLevel.isLessThan(AndroidApiLevel.R)) {
      try {
        return isValidSimpleName(apiLevel, decode());
      } catch (UTFDataFormatException e) {
        return false;
      }
    }
    return true;
  }

  public String dump() {
    StringBuilder builder = new StringBuilder();
    builder.append(toString());
    builder.append(" [");
    for (int i = 0; i < content.length; i++) {
      if (i > 0) {
        builder.append(" ");
      }
      builder.append(Integer.toHexString(content[i] & 0xff));
    }
    builder.append("]");
    return builder.toString();
  }

  public boolean startsWith(DexString prefix) {
    return startsWith(prefix.content);
  }

  public boolean startsWith(String prefix) {
    return startsWith(encodeToMutf8(prefix));
  }

  public boolean startsWith(byte[] prefixContent) {
    if (content.length < prefixContent.length) {
      return false;
    }
    for (int i = 0; i < prefixContent.length - 1; i++) {
      if (content[i] != prefixContent[i]) {
        return false;
      }
    }
    return true;
  }

  public boolean contains(DexString s) {
    // TODO(b/146621590): This does not handle character boundaries correctly.
    int index = 0;
    while (content.length - index >= s.content.length) {
      int i = 0;
      while (i < s.content.length - 1 && content[index + i] == s.content[i]) {
        i++;
      }
      if (i == s.content.length - 1) {
        return true;
      }
      index++;
    }
    return false;
  }

  public boolean endsWith(DexString suffix) {
    if (content.length < suffix.content.length) {
      return false;
    }
    for (int i = content.length - suffix.content.length, j = 0; i < content.length; i++, j++) {
      if (content[i] != suffix.content[j]) {
        return false;
      }
    }
    return true;
  }

  public DexString prepend(String prefix, DexItemFactory dexItemFactory) {
    return prepend(dexItemFactory.createString(prefix), dexItemFactory);
  }

  public DexString prepend(DexString prefix, DexItemFactory dexItemFactory) {
    int newSize = prefix.size + this.size;
    // Each string ends with a 0 terminating byte, hence the +/- 1.
    byte[] newContent = new byte[prefix.content.length + this.content.length - 1];
    System.arraycopy(prefix.content, 0, newContent, 0, prefix.content.length - 1);
    System.arraycopy(
        this.content, 0, newContent, prefix.content.length - 1, this.content.length - 1);
    return dexItemFactory.createString(newSize, newContent);
  }

  public DexString withNewPrefix(
      DexString prefix, DexString rewrittenPrefix, DexItemFactory factory) {
    // Copy bytes over to avoid decoding/encoding cost.
    // Each string ends with a 0 terminating byte, hence the +/- 1.
    // Maintain the [[ at the beginning for array dimensions.
    assert prefix.startsWith("L") && rewrittenPrefix.startsWith("L");
    if (prefix.equals(rewrittenPrefix)) {
      return this;
    }
    // When concatinating two paths, we may end up adding a separator or removing a separator.
    // 'L' -> 'Lfoo/bar', for a string 'Lbaz/Qux' the result should be 'Lfoo/bar' + '/' + 'baz/Qux'.
    // 'Lfoo' -> 'L', for a string 'Lfoo/Qux' the result should be 'L' + Qux', thus we remove a '/'.
    boolean insertSeparator =
        prefix.size == 1 && !rewrittenPrefix.endsWith(factory.descriptorSeparator);
    boolean removeSeparator =
        rewrittenPrefix.size == 1 && !prefix.endsWith(factory.descriptorSeparator);
    int sizeAdjustment = 0;
    if (insertSeparator) {
      sizeAdjustment += 1;
    } else if (removeSeparator) {
      sizeAdjustment -= 1;
    }
    int arrayDim = getArrayDim();
    // In the case that prefix is 'L' we also insert a separator '/' after the destination
    int newSize = rewrittenPrefix.size + this.size - prefix.size + sizeAdjustment;
    byte[] newContent =
        new byte
            [rewrittenPrefix.content.length
                + this.content.length
                - prefix.content.length
                + sizeAdjustment];
    // Write array dim.
    for (int i = 0; i < arrayDim; i++) {
      newContent[i] = ARRAY_CHARACTER;
    }
    // Write new prefix.
    System.arraycopy(
        rewrittenPrefix.content, 0, newContent, arrayDim, rewrittenPrefix.content.length - 1);
    // Account for target being an empty string as to not start the descriptor with '/'.
    int prefixIndex = prefix.content.length - 1;
    int rewrittenIndex = rewrittenPrefix.content.length - 1;
    if (removeSeparator) {
      prefixIndex += 1;
    } else if (insertSeparator) {
      newContent[rewrittenIndex] = '/';
      rewrittenIndex += 1;
    }
    // Write existing name - old prefix.
    System.arraycopy(
        this.content, prefixIndex, newContent, rewrittenIndex, this.content.length - prefixIndex);
    return factory.createString(newSize, newContent);
  }

  public DexString withoutArray(DexItemFactory factory) {
    int arrayDim = getArrayDim();
    if (arrayDim == 0) {
      return this;
    }
    byte[] newContent = new byte[content.length - arrayDim];
    System.arraycopy(this.content, arrayDim, newContent, 0, newContent.length);
    return factory.createString(this.size - arrayDim, newContent);
  }

  private int getArrayDim() {
    int arrayDim = 0;
    while (content[arrayDim] == ARRAY_CHARACTER) {
      arrayDim++;
    }
    return arrayDim;
  }

  public DexString toArrayDescriptor(int dimensions, DexItemFactory dexItemFactory) {
    byte[] newContent = new byte[content.length + dimensions];
    Arrays.fill(newContent, 0, dimensions, (byte) '[');
    System.arraycopy(content, 0, newContent, dimensions, content.length);
    return dexItemFactory.createString(size + dimensions, newContent);
  }
}
