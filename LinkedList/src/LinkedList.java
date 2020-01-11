public class LinkedList<E> {

    private class Node {

        E e;
        Node next;

        public Node(E e, Node next) {
            this.e = e;
            this.next = next;
        }

        public Node(E e) {
            this(e, null);
        }

        public Node() {
            this(null, null);
        }

        @Override
        public String toString() {
            return e.toString();
        }
    }

    private Node head;
    private int size;

    // 构造函数, 传入数组构造LinkedList
    public LinkedList(E[] arr){

        if(arr == null)
            throw new IllegalArgumentException("Is Empty Array");

        for(int i = arr.length - 1; i >= 0; i --){
            head = new Node(arr[i], head);
            size ++;
        }
    }

    // 默认构造函数
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
            throw new IllegalArgumentException("index illegal");

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

    // 返回在链表中index位置的元素
    public E get(int index){
        if(index < 0 || index >= size)
            throw new IllegalArgumentException("index illega");
        return get(head, index).e;
    }

    // 返回链表中第一个位置的元素
    public E getFirst(){
        return get(0);
    }

    // 返回链表中最后一个位置的元素
    public E getLast(){
        return get(size - 1);
    }

    // 返回以node为节点的链表中第index位置的节点, 递归算法
    private Node get(Node node, int index){

        if(index == 0)
            return node;
        else
            return get(node.next, index - 1);
    }

    // 在链表中更新第index位置的元素
    public void set(int index, E e){
        if(index < 0 || index >= size)
            throw new IllegalArgumentException("index is illega");
//        Node node = get(head, index);
//        node.e = e;
        get(head, index).e = e;
    }

    // 在链表中更新第一个位置的元素
    public void setFirst(E e){
        set(0, e);
    }

    // 在链表中更新最后一个位置的元素
    public void setLast(E e){
        set(size - 1, e);
    }

    // 在链表中删除第index位置的元素, 返回删除元素的值
    public E remove(int index){

        if(index < 0 || index >= size)
            throw new IllegalArgumentException("index is illega");

        Node ret = get(head, index);
        head = remove(head, index);

        return ret.e;
    }

    // 在链表中删除第一个位置的元素, 返回删除元素的值
    public E removeFirst(){
        return remove(0);
    }

    // 在链表中删除最后一个位置的元素, 返回删除元素的值
    public E removeLast(){
        return remove(size - 1);
    }

    // 在以node为节点的链表中删除第index位置的元素
    // 返回新的链表
    private Node remove(Node node, int index){

        if(index == 0){
            size --;
            return node.next;
        }

        node.next = remove(node.next, index - 1);
        return node;
    }

    // 在链表中删除所有元素为e的节点
    public void removeAll(E e){
        head = removeAll(head, e);
    }

    // 在以node为节点的链表中删除所有元素为e的节点, 递归算法
    // 返回新的链表
    private Node removeAll(Node node, E e){

        if(node == null)
            return node;

        node.next = removeAll(node.next, e);
        if(node.e.equals(e)){
            size ++;
            return node.next;
        }

        return node;
    }

    @Override
    public String toString(){

        StringBuilder res = new StringBuilder();
        res.append(String.format("LinkedList: size = %d\n", size));
        for(Node cur = head; cur != null; cur = cur.next)
            res.append(cur.e + "->");

        res.append("NULl");
        return res.toString();
    }
}
