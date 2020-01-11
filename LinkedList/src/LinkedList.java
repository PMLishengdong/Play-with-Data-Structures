public class LinkedList<E> {

    private class Node{

        E e;
        Node next;

        public Node(E e, Node next){
            this.e = e;
            this.next = next;
        }

        public Node(E e){
            this(e, null);
        }

        public Node(){
            this(null, null);
        }

        @Override
        public String toString(){
            return e.toString();
        }
    }

    private Node head;
    private int size;

    public LinkedList(){
        head = null;
        size = 0;
    }

    // 返回链表元素个数
    public int getSize(){
        return size;
    }

    // 返回链表是否为空
    public boolean isEmpty(){
        return size == 0;
    }

    // 在链表中第index位置的元素添加一个新元素
    public void add(int index, E e){

        if(index < 0 || index > size)
            throw new IllegalArgumentException("添加失败， 索引非法的: " + index);

        head = add(head, index, e);
    }

    // 在以node为节点的链表中index位置的元素添加一个新元素, 递归算法
    // 返回新的链表
    private Node add(Node node, int index, E e){

        if(index == 0){
            size ++;
            return new Node(e, node);
        }

        node.next = add(node.next, index - 1, e);
        return node;
    }

    // 在链表中第一个位置添加新元素
    public void addFirst(E e){
        add(0, e);
    }

    // 在链表中最后一个后置添加新元素
    public void addLast(E e){
        add(size, e);
    }

    @Override
    public String toString(){

        StringBuilder res = new StringBuilder();
        for(Node cur = head; cur != null; cur = cur.next)
            res.append(cur.e + "->");

        res.append("NULl");
        return res.toString();
    }
}
