public class Main {

    public static void main(String[] args) {
	// write your code here

        LinkedList<Integer> list = new LinkedList<>(new Integer[]{-11, 22, -33, 44, -55});

        // 添加元素
        list.addFirst(01);
        list.add(list.getSize() / 2, list.get(list.getSize() / 2) - 1);
        list.addLast(66);
        System.out.println(list);

        // 删除元素
        list.removeFirst();
        list.remove(list.getSize() / 2 - 1);
        list.removeLast();
        System.out.println(list);

        // 修改 and 查询元素
        list.setFirst(Math.abs(list.getFirst()));
        list.set(list.getSize() / 2, Math.abs(list.get(list.getSize() / 2)));
        list.setLast(Math.abs(list.getLast()));
        System.out.println(list);
    }
}
