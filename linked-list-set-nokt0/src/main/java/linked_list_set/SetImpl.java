package linked_list_set;

import kotlinx.atomicfu.AtomicRef;

// Удаление разделяем на логическое и физическое
// Если элемент успели удалить только логически, то при поиске окна мы поможем удалить физически
public class SetImpl implements Set {
    private class Node {
        AtomicRef<Node> next;
        int key;

        Node(int key, Node next) {
            this.next = new AtomicRef<>(next);
            this.key = key;
        }

    }

    private class NodeRemoved extends Node {
        NodeRemoved(int key, Node next) {
            super(key, next);
        }
    }

    private class Window {
        Node current, next;

        Window(Node current, Node next) {
            this.current = current;
            this.next = next;
        }
    }

    private final Node endNode = new Node(Integer.MAX_VALUE, null);
    private final Node head = new Node(Integer.MIN_VALUE, endNode);

    /**
     * Returns the {@link Window}, where cur.x < x <= next.x
     */
    private Window findWindow(int key) {
        while (true) {
            Node current = head;
            Node next = current.next.getValue();
            Node node;


            // элементы отсортированы в порядке возрастания, идем по ним пока не найдем элемент больше нужного
            while (next.key < key) {
                node = next.next.getValue();
                final boolean isRemoved = node instanceof NodeRemoved;

                // Передвигаем окно через логически удаленный элемент и помогаем удалить
                if (isRemoved) {
                    node = node.next.getValue();
                    // Физическое удаление, после начинаем сначала
                    if (!current.next.compareAndSet(next, node)) {
                       break;
                    }
                    next = node;
                    continue;
                }

                // передвигаем окно на 1
                current = next;
                next = current.next.getValue();
            }

            // Проверка, что next.next не удален логически, если удален то помогаем удалить физически, если не получилось удалить то
            // продолжаем поиск окна
            do {
                node = next.next.getValue();
                final boolean isRemoved = node instanceof NodeRemoved;

                if (!isRemoved) {
                    return new Window(current, next);
                }

                // Физическое удаление
                node = node.next.getValue();
            } while (current.next.compareAndSet(next, node));
        }
    }

    @Override
    public boolean add(int key) {
        while (true) {
            Window window = findWindow(key);
            Node next = window.next;
            Node current = window.current;

            // В псевдокоде в cas мы проверяем что флаг removed == false, тут мы также проверяем
            // что !removed
            if (current instanceof NodeRemoved || next instanceof NodeRemoved || next.next.getValue() instanceof NodeRemoved) {
                continue;
            }

            //Проверяем не записан ли в next уже такой ключ
            if (next.key == key) {
                return false;
            }

            Node node = new Node(key, next);
            // атомарно добавляем новый узел
            if (current.next.compareAndSet(next, node)) {
                return true;
            }
        }
    }

    @Override
    public boolean remove(int key) {
        while (true) {
            Window window = findWindow(key);
            Node next = window.next;
            Node current = window.current;
            Node nextNext = next.next.getValue();

            // В псевдокоде в cas мы проверяем что флаг removed == false, тут мы также проверяем
            // что !removed
            if (next instanceof NodeRemoved || current instanceof NodeRemoved || nextNext instanceof NodeRemoved) {
                continue;
            }

            if (next.key != key) {
                return false;
            }

            Node removedNode = new NodeRemoved(next.key, next.next.getValue());

            // Логическое и физическое удаление
            if (next.next.compareAndSet(nextNext, removedNode)) {
                current.next.compareAndSet(next, nextNext);
                return true;
            }
        }
    }

    @Override
    public boolean contains(int key) {
        Window w = findWindow(key);
        return w.next.key == key;
    }
}