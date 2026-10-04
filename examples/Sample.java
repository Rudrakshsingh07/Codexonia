public class Sample {
    public static void main(String[] args) {
        int x = 2;
        int y = Sample.add(x, 3);
        int[] nums = {1, 2, 3};
        nums[0] = y;
        System.out.println(y + ":" + nums[0]);
    }

    static int add(int a, int b) {
        int c = a + b;
        return c;
    }
}
