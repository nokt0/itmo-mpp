package msqueue;

import kotlinx.atomicfu.AtomicRef;
// Может быть ситуация когда tail указывает на неверный элемент из за неатомарности передвижения
// tail и добавления элемента в очередь
public class MSQueue implements Queue {
    private final AtomicRef<Node> head;
    private final AtomicRef<Node> tail;

    public MSQueue() {
        Node dummy = new Node(0);
        this.head = new AtomicRef<>(dummy);
        this.tail = new AtomicRef<>(dummy);
    }

    @Override
    public void enqueue(int x) {
        Node newTail = new Node(x);
        while (true) {
            Node currTail = tail.getValue();
            // Может быть ситуация когда при добавление элемента в очередь, tail обновить не успели
            // Помогаем подвинуть tail, если видим что tail.next != null
            if (currTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(currTail, newTail);
                return;
            } else {
                tail.compareAndSet(currTail, currTail.next.getValue());
            }
        }

    }

    @Override
    public int dequeue() {
        while (true) {
            Node curHead = head.getValue();

            // Двигаем tail если у него есть next, пока не подвинем до конца
            while (true) {
                Node curTail = tail.getValue();
                Node tailNext = curTail.next.getValue();

                if (tailNext == null) {
                    break;
                }
                tail.compareAndSet(curTail, tailNext);
            }

            // Очередь пуста
            if (curHead == tail.getValue()) {
                return Integer.MIN_VALUE;
            }

            // Проверяем не поменялся ли head, меняем head на next. Возвращаем next так как у нас изначально был dummy и
            // head всегда указывает на элемент который уже вернули, либо dummy
            Node next = head.getValue().next.getValue();
            if (head.compareAndSet(curHead, curHead.next.getValue()))
                return next.x;
        }
    }

    @Override
    public int peek() {
        while (true) {
            Node curHead = head.getValue();
            Node next = curHead.next.getValue();

            if (curHead != head.getValue())
                continue;

            // Очередь пуста
            if (next == null)
                return Integer.MIN_VALUE;
            // Двигаем хвост если есть next
            if (tail.getValue() == curHead)
                tail.setValue(next);
            // Если голова не изменилась возвращаем next
            if (head.getValue() == curHead)
                return next.x;
        }
    }

    private class Node {
        final int x;
        AtomicRef<Node> next = new AtomicRef<>(null);

        Node(int x) {
            this.x = x;
        }
    }
}