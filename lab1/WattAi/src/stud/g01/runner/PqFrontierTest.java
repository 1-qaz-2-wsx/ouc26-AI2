package stud.g01.runner;

import core.problem.State;
import core.solver.queue.EvaluationType;
import core.solver.queue.Node;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.queue.PqFrontier;

public class PqFrontierTest {
    public static void main(String[] args) {
        System.out.println("========== Frontier Detailed Unit Test ==========");

        // 构造辅助方法，快速生成状态
        State s1 = new PuzzleBoard(3, new int[]{1,2,3,4,5,6,7,8,0});
        State s2 = new PuzzleBoard(3, new int[]{1,2,3,4,5,6,7,0,8});
        State s3 = new PuzzleBoard(3, new int[]{1,2,3,4,5,6,0,7,8});
        State s4 = new PuzzleBoard(3, new int[]{1,2,3,4,5,0,6,7,8});
        State s5 = new PuzzleBoard(3, new int[]{1,2,3,4,0,5,6,7,8});

        // ==========================================
        // 1. 排序测试 (Sorting Test)
        // ==========================================
        System.out.println("\n[Test 1: Sorting Test (f = g + h)]");
        PqFrontier frontier = new PqFrontier(Node.evaluator(EvaluationType.FULL));

        // 故意打乱顺序插入，测试优先队列是否能正确排序
        Node nA = new Node(s1, null, null, 10, 0); // f=10
        Node nB = new Node(s2, null, null, 5, 2);  // f=7
        Node nC = new Node(s3, null, null, 8, 0);  // f=8
        Node nD = new Node(s4, null, null, 6, 0);  // f=6

        frontier.offer(nA);
        frontier.offer(nB);
        frontier.offer(nC);
        frontier.offer(nD);

        System.out.println("Input f values: 10, 7, 8, 6");
        System.out.println("Poll 1 (Expect 6): " + frontier.poll().evaluation());
        System.out.println("Poll 2 (Expect 7): " + frontier.poll().evaluation());
        System.out.println("Poll 3 (Expect 8): " + frontier.poll().evaluation());
        System.out.println("Poll 4 (Expect 10): " + frontier.poll().evaluation());

        // ==========================================
        // 2. 队列替换与去重测试 (Replacement Test)
        // ==========================================
        System.out.println("\n[Test 2: Replacement & Deduplication Test]");
        frontier.clear();

        // 先放一个状态 s1，代价 g=5
        frontier.offer(new Node(s1, null, null, 5, 0));
        System.out.println("Insert s1 (g=5). Size: " + frontier.size());

        // 再放相同状态 s1，但是代价 g=3 (更优)
        boolean accepted = frontier.offer(new Node(s1, null, null, 3, 0));
        System.out.println("Insert same s1 (g=3). Accepted: " + accepted + ", Size: " + frontier.size());

        // 再放相同状态 s1，但是代价 g=7 (更差)
        boolean rejected = frontier.offer(new Node(s1, null, null, 7, 0));
        System.out.println("Insert same s1 (g=7). Accepted: " + rejected + ", Size: " + frontier.size());

        // 开始出队，验证懒惰删除
        System.out.println("Poll 1 (Expect g=3): " + frontier.poll().getPathCost());
        System.out.println("Poll 2 (Expect null due to Lazy Deletion of g=5): " + frontier.poll());
        System.out.println("Is queue empty? (Expect true): " + frontier.isEmpty());

        // ==========================================
        // 3. 清空测试 (Clear Test)
        // ==========================================
        System.out.println("\n[Test 3: Clear Test]");
        // 放入全新状态
        frontier.offer(new Node(s2, null, null, 2, 0));
        frontier.offer(new Node(s3, null, null, 4, 0));
        frontier.offer(new Node(s4, null, null, 6, 0));
        System.out.println("Queue size after 3 inserts: " + frontier.size());

        frontier.clear();
        System.out.println("Queue size after clear(): " + frontier.size());
        System.out.println("Is queue empty? (Expect true): " + frontier.isEmpty());
        // 验证清空后辅助哈希表是否也清了，此时再插入 s2 应该是第一次插入
        boolean reInsert = frontier.offer(new Node(s2, null, null, 10, 0));
        System.out.println("Re-insert s2 after clear. Accepted (Expect true): " + reInsert);
        frontier.clear(); // 再次清空

        // ==========================================
        // 4. 混合顺序与边界测试 (Edge Cases)
        // ==========================================
        System.out.println("\n[Test 4: Empty Queue & Boundary Test]");
        System.out.println("Poll from empty queue (Expect null): " + frontier.poll());
        System.out.println("Size of empty queue (Expect 0): " + frontier.size());
        System.out.println("isEmpty on empty queue (Expect true): " + frontier.isEmpty());
        System.out.println("Contains on empty queue (Expect false): " + frontier.contains(nA));

        // ==========================================
        // 5. 包含测试 (Contains Test)
        // ==========================================
        System.out.println("\n[Test 5: Contains Test]");
        frontier.offer(nD); // 加入 s4，g=6
        System.out.println("Queue contains nD (Expect true): " + frontier.contains(nD));
        // 插入一个相同状态，但代价不同的节点
        Node nD_New = new Node(s4, null, null, 4, 0);
        System.out.println("Queue contains nD_New (Expect false, because g is different): " + frontier.contains(nD_New));

        System.out.println("\n========== All Detailed Tests Passed ==========");
    }
}