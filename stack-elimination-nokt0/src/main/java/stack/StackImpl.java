package stack;

import kotlinx.atomicfu.AtomicRef;

import java.util.Random;

public class StackImpl implements Stack {
    private static class Node {
        final AtomicRef<Node> next;
        final int x;

        Node(int x, Node next) {
            this.next = new AtomicRef<>(next);
            this.x = x;
        }
    }

    Random random = new Random();
    final int NEIGHBOURS_COUNT = 3;
    final int SIZE = 10;
    final int WAIT_COUNT = 30;
    EliminationArray eliminationArray = new EliminationArray(SIZE);


    private enum OperationStatus {
        EMPTY,
        WAIT,
        DONE,
    }

    private class EliminationArray {
        AtomicRef<EliminationItem>[] arr = new AtomicRef[SIZE];

        EliminationArray(int length) {
            random = new Random();

            for (int i = 0; i < length; ++i) {
                arr[i] = new AtomicRef<>(new EliminationItem());
            }
        }

        AtomicRef<EliminationItem> getItem(int index) {
            return arr[index];
        }

    }

    private class EliminationItem {
        volatile int value;
        OperationStatus status;

        EliminationItem() {
            this.value = Integer.MIN_VALUE;
            this.status = OperationStatus.EMPTY;
        }

        EliminationItem(int value, OperationStatus status) {
            this.value = value;
            this.status = status;
        }
    }

    private int tryPop() {
        int neighboursCount = random.nextInt(SIZE - NEIGHBOURS_COUNT);
        // Ищем в массиве Wait элементы, если находим то пробуем с помощью CAS заменить ячейку в elimination на DONE, если получилось
        // значит мы смогли сделать pop из elimination
        for (int j = neighboursCount; j < neighboursCount + NEIGHBOURS_COUNT; j++) {
            EliminationItem item = eliminationArray.getItem(j).getValue();
            if (item.status != OperationStatus.WAIT)
                continue;
            if (eliminationArray.getItem(j).compareAndSet(item,  new EliminationItem(0, OperationStatus.DONE)))
                return item.value;
        }
        return Integer.MIN_VALUE;
    }


    private boolean tryPush(int x) {
        int index = random.nextInt(SIZE - NEIGHBOURS_COUNT);
        // Проходим по elimination и ищем EMPTY элементы, если находим то пробуем с помощью CAS запушить туда наше значение
        for (int i = index; i < index + NEIGHBOURS_COUNT; i++) {
            EliminationItem item = eliminationArray.getItem(i).getValue();
            if (item.status != OperationStatus.EMPTY) {
                continue;
            }

            EliminationItem newItem = new EliminationItem(x, OperationStatus.WAIT);

            // Если получилось запушить то ждем какое то время, может быть элемент заберут(WAIT -> DONE)
            // Если элемент забрали то pop удался, если нет то убираем из elimination наш WAIT элемент
            // Получилось -> заканчиваем попытку и добавляем в обычный стек
            // Не Получилось -> значит элемент изменился, то есть кто то его забрал и произошел успешный обмен
            if (eliminationArray.getItem(i).compareAndSet(item, newItem)) {
                for (int waitIteration = 0; waitIteration < WAIT_COUNT; waitIteration++) {
                    if (!newItem.equals(eliminationArray.getItem(i).getValue()) || eliminationArray.getItem(i).getValue().status == OperationStatus.DONE )
                        return true;
                }
                return !eliminationArray.getItem(i).compareAndSet(newItem, new EliminationItem());
            }
        }
        return false;
    }

    // head pointer
    private AtomicRef<Node> head = new AtomicRef<>(null);

    @Override
    public void push(int x) {
        if (tryPush(x)) {
            return;
        }
        while (true) {
            Node curHead = head.getValue();
            Node newHead = new Node(x, curHead);
            if (head.compareAndSet(curHead, newHead)) {
                return;
            }
        }
    }

    @Override
    public int pop() {
        int result = tryPop();
        if (result != Integer.MIN_VALUE)
            return result;
        while (true) {
            Node curHead = head.getValue();
            if (curHead == null) return Integer.MIN_VALUE;
            if (head.compareAndSet(curHead, curHead.next.getValue())) {
                return curHead.x;
            }
        }
    }
}

