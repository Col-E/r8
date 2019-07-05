package backport;

public final class ByteBackportJava9Main {
  private static final byte MIN_UNSIGNED_VALUE = (byte) 0;
  private static final byte MAX_UNSIGNED_VALUE = (byte) -1;

  public static void main(String[] args) {
    testCompareUnsigned();
  }

  private static void testCompareUnsigned() {
    assertTrue(Byte.compareUnsigned(MIN_UNSIGNED_VALUE, MIN_UNSIGNED_VALUE) == 0);
    assertTrue(Byte.compareUnsigned(MIN_UNSIGNED_VALUE, Byte.MAX_VALUE) < 0);
    assertTrue(Byte.compareUnsigned(MIN_UNSIGNED_VALUE, Byte.MIN_VALUE) < 0);
    assertTrue(Byte.compareUnsigned(MIN_UNSIGNED_VALUE, MAX_UNSIGNED_VALUE) < 0);

    assertTrue(Byte.compareUnsigned(Byte.MAX_VALUE, MIN_UNSIGNED_VALUE) > 0);
    assertTrue(Byte.compareUnsigned(Byte.MAX_VALUE, Byte.MAX_VALUE) == 0);
    assertTrue(Byte.compareUnsigned(Byte.MAX_VALUE, Byte.MIN_VALUE) < 0);
    assertTrue(Byte.compareUnsigned(Byte.MAX_VALUE, MAX_UNSIGNED_VALUE) < 0);

    assertTrue(Byte.compareUnsigned(Byte.MIN_VALUE, MIN_UNSIGNED_VALUE) > 0);
    assertTrue(Byte.compareUnsigned(Byte.MIN_VALUE, Byte.MAX_VALUE) > 0);
    assertTrue(Byte.compareUnsigned(Byte.MIN_VALUE, Byte.MIN_VALUE) == 0);
    assertTrue(Byte.compareUnsigned(Byte.MIN_VALUE, MAX_UNSIGNED_VALUE) < 0);

    assertTrue(Byte.compareUnsigned(MAX_UNSIGNED_VALUE, MIN_UNSIGNED_VALUE) > 0);
    assertTrue(Byte.compareUnsigned(MAX_UNSIGNED_VALUE, Byte.MAX_VALUE) > 0);
    assertTrue(Byte.compareUnsigned(MAX_UNSIGNED_VALUE, Byte.MIN_VALUE) > 0);
    assertTrue(Byte.compareUnsigned(MAX_UNSIGNED_VALUE, MAX_UNSIGNED_VALUE) == 0);
  }

  private static void assertTrue(boolean value) {
    if (!value) {
      throw new AssertionError("Expected <true> but was <false>");
    }
  }
}
