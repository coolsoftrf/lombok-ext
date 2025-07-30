@lombok.Lookup(field = "x", constructorArgumentOrdinal = 0, defaultValue = "TWOS") enum LookupEnum {
	ONES(111),
	TWOS(222);
	
	private LookupEnum(int v){
		x = v;
	}

	int x;

	public static void test() {
		LookupEnum.lookup(111);
	}
}