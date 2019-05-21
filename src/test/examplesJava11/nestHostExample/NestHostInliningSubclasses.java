package nestHostExample;

public class NestHostInliningSubclasses {

  public static class InnerWithPrivAccess extends NestHostInlining.InnerWithPrivAccess {
    public String accessSubclass(NestHostInlining host) {
      return super.access(host) + "Subclass";
    }
  }

  public static class InnerNoPrivAccess extends NestHostInlining.InnerNoPrivAccess {
    @NeverInline
    public String printSubclass() {
      return super.print() + "Subclass";
    }
  }

  public static void main(String[] args) {
    System.out.println(new InnerWithPrivAccess().accessSubclass(new NestHostInlining()));
    System.out.println(new InnerNoPrivAccess().printSubclass());
  }
}
