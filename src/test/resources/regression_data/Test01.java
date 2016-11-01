package regression_data;

class A {
	public int vCall(int i) {
		return 1;
	}
}

class B extends A {
	@Override
	public int vCall(int i) {
		return 2;
	}	
}

public class Test01 {

	public static void main(String[] args) {
		Test01 t = new Test01();
		A a = new B();
		t.foo(a);
	}
	
	public int foo(A a) {
		return a.vCall(42);
	}
}
