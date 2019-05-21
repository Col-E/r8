package nestHostExample;

public class OutsideInliningWithAccess extends NestHostInlining.EmptyWithPrivAccess {

  public static void main(String[] args) {
    System.out.println("OutsideInliningNoAccess");
    System.out.println(new OutsideInliningWithAccess().access(new NestHostInlining()));
  }
}
