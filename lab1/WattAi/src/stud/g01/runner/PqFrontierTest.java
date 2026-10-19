package stud.g01.runner;

import core.solver.queue.Node;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.queue.PqFrontier;

/**
 * PqFrontier 回归测试。
 *
 * 1. 使用显式检查，不依赖 JVM 的 -ea 参数。
 * 2. 每项断言通过后输出具体检查内容。
 * 3. 任意断言失败都会抛出 AssertionError，中止执行。
 * 4. 只有全部测试成功后才输出总通过信息。
 *
 * 不需要修改 SearchTester 或其他 core 文件。
 */
public class PqFrontierTest {

    private static int passedChecks;
    private static int passedTests;
    private static String currentTest;

    private static final PuzzleBoard S1 =
            new PuzzleBoard(3, new int[]{
                    1, 2, 3,
                    4, 5, 6,
                    7, 8, 0
            });

    private static final PuzzleBoard S2 =
            new PuzzleBoard(3, new int[]{
                    1, 2, 3,
                    4, 5, 6,
                    7, 0, 8
            });

    private static final PuzzleBoard S3 =
            new PuzzleBoard(3, new int[]{
                    1, 2, 3,
                    4, 5, 6,
                    0, 7, 8
            });

    private static final PuzzleBoard S4 =
            new PuzzleBoard(3, new int[]{
                    1, 2, 3,
                    4, 5, 0,
                    6, 7, 8
            });

    public static void main(String[] args) {
        passedChecks = 0;
        passedTests = 0;

        runTest("Sorting", PqFrontierTest::testSorting);
        runTest("Replacement", PqFrontierTest::testReplacement);
        runTest("Stale-entry", PqFrontierTest::testSkipStaleEntry);
        runTest("Reinsertion", PqFrontierTest::testReinsertAfterPoll);
        runTest("Clear", PqFrontierTest::testClear);
        runTest("Empty-queue", PqFrontierTest::testEmptyQueue);

        System.out.println();
        System.out.printf(
                "All PqFrontier tests Passed. tests=%d, checks=%d%n",
                passedTests,
                passedChecks
        );
    }

    /**
     * 任意异常或断言失败都会继续向外抛出，
     * 因此不会在失败后打印总通过信息。
     */
    private static void runTest(String name, Runnable test) {
        currentTest = name;
        int checksBefore = passedChecks;

        System.out.println();
        System.out.println("[TEST] " + name);

        test.run();

        passedTests++;

        System.out.printf(
                "[PASS] %s test Passed. checks=%d%n",
                name,
                passedChecks - checksBefore
        );
    }

    /**
     * 检查默认比较器按 f=g+h 排序，
     * 并检查每次出队后成员索引与有效结点数量。
     */
    private static void testSorting() {
        PqFrontier frontier = new PqFrontier();

        Node f10 = node(S1, 10, 0);
        Node f7 = node(S2, 5, 2);
        Node f8 = node(S3, 8, 0);
        Node f6 = node(S4, 6, 0);

        check(frontier.offer(f10), "接受 f=10 的结点");
        check(frontier.offer(f7), "接受 f=7 的结点");
        check(frontier.offer(f8), "接受 f=8 的结点");
        check(frontier.offer(f6), "接受 f=6 的结点");
        check(frontier.size() == 4, "有效结点数为 4");

        Node[] expected = {f6, f7, f8, f10};
        int[] expectedF = {6, 7, 8, 10};

        for (int i = 0; i < expected.length; i++) {
            String prefix = "第 " + (i + 1) + " 次出队：";

            check(!frontier.isEmpty(),
                    prefix + "出队前 isEmpty=false");

            check(frontier.contains(expected[i]),
                    prefix + "出队前 contains=true");

            Node actual = frontier.poll();

            check(actual == expected[i],
                    prefix + "返回 f=" + expectedF[i] + " 的结点");

            check(!frontier.contains(expected[i]),
                    prefix + "出队后 contains=false");

            check(frontier.size() == expected.length - i - 1,
                    prefix + "有效结点数正确减少 1");
        }

        checkEmpty(frontier);
    }

    /**
     * 复现关键问题：
     * 同状态依次插入 g=5、g=3，取出 g=3 后，
     * 必须立即报告 size=0、isEmpty=true、contains=false。
     */
    private static void testReplacement() {
        PqFrontier frontier = new PqFrontier();

        Node g5 = node(S1, 5, 0);

        // 内容相同但对象不同，验证按照状态值判重。
        PuzzleBoard equalState =
                new PuzzleBoard(3, S1.toArray());

        Node g3 = node(equalState, 3, 0);

        check(frontier.offer(g5),
                "首次插入 g=5 成功");

        check(frontier.offer(g3),
                "同状态更优 g=3 替换 g=5 成功");

        check(!frontier.offer(node(S1, 3, 0)),
                "拒绝同状态、相同 g=3 的重复结点");

        check(!frontier.offer(node(S1, 7, 0)),
                "拒绝同状态、更差 g=7 的结点");

        check(frontier.size() == 1,
                "替换后只统计 1 个有效结点");

        check(!frontier.isEmpty(),
                "替换后仍有有效结点，isEmpty=false");

        check(!frontier.contains(g5),
                "旧 g=5 不再属于有效成员");

        check(frontier.contains(node(S1, 3, 0)),
                "包含相同状态、相同 g=3 的当前成员");

        Node actual = frontier.poll();

        check(actual == g3,
                "poll 返回更优的 g=3 结点");

        // 必须在再次调用 poll 之前检查，
        // 防止依赖下一次 poll 才清理逻辑上的空队列。
        check(frontier.size() == 0,
                "取出 g=3 后 size 立即为 0");

        check(frontier.isEmpty(),
                "即使堆内残留旧 g=5，isEmpty 也立即为 true");

        check(!frontier.contains(g3),
                "取出 g=3 后 contains 立即为 false");

        check(!frontier.contains(g5),
                "残留的旧 g=5 也不能成为有效成员");

        checkEmpty(frontier);
    }

    /**
     * 检查过期条目夹在有效结点之间时，
     * poll 能跳过过期条目并继续返回有效结点。
     */
    private static void testSkipStaleEntry() {
        PqFrontier frontier = new PqFrontier();

        Node old = node(S1, 5, 0);
        Node g3 = node(S1, 3, 0);
        Node other = node(S2, 8, 0);

        check(frontier.offer(old),
                "插入旧 g=5 成功");
        check(frontier.offer(g3),
                "用 g=3 替换旧 g=5 成功");
        check(frontier.offer(other),
                "插入另一状态 g=8 成功");

        check(frontier.size() == 2,
                "替换后有效状态数为 2");

        check(frontier.poll() == g3,
                "首先取出 g=3");

        check(!frontier.contains(g3),
                "g=3 出队后成员索引已清理");

        check(frontier.size() == 1,
                "出队后剩余 1 个有效状态");

        check(!frontier.isEmpty(),
                "仍有有效结点时 isEmpty=false");

        check(frontier.contains(other),
                "另一有效结点仍在成员索引中");

        // 堆中旧 g=5 排在 other 的 g=8 前面。
        check(frontier.poll() == other,
                "跳过过期 g=5，返回另一有效结点 g=8");

        check(!frontier.contains(other),
                "另一结点出队后 contains=false");

        checkEmpty(frontier);
    }

    /**
     * 检查出队后可以重新插入同状态，
     * 且此前残留的旧条目不会被误认为当前有效结点。
     */
    private static void testReinsertAfterPoll() {
        PqFrontier frontier = new PqFrontier();

        Node old = node(S1, 5, 0);
        Node g3 = node(S1, 3, 0);

        check(frontier.offer(old),
                "插入旧 g=5 成功");
        check(frontier.offer(g3),
                "插入更优 g=3 成功");

        check(frontier.poll() == g3,
                "首先取出 g=3");

        check(!frontier.contains(g3),
                "出队后清理有效成员索引");

        check(frontier.size() == 0,
                "重新插入前有效结点数为 0");

        check(frontier.isEmpty(),
                "重新插入前 isEmpty=true");

        // 旧 g=5 仍可能留在堆中，但已不是有效成员。
        Node g9 = node(S1, 9, 0);

        check(frontier.offer(g9),
                "已出队状态允许以 g=9 重新插入");

        check(frontier.size() == 1,
                "重新插入后有效结点数为 1");

        check(!frontier.isEmpty(),
                "重新插入后 isEmpty=false");

        check(frontier.contains(g9),
                "重新插入后 contains=true");

        check(!frontier.contains(old),
                "旧 g=5 不能重新成为有效成员");

        check(frontier.poll() == g9,
                "跳过旧 g=5，返回重新插入的 g=9");

        check(!frontier.contains(g9),
                "再次出队后 contains=false");

        checkEmpty(frontier);
    }

    /**
     * 检查 clear 同时清理堆和有效成员索引，
     * 并验证清空后仍可正常使用。
     */
    private static void testClear() {
        PqFrontier frontier = new PqFrontier();

        Node old = node(S1, 5, 0);
        Node current = node(S1, 3, 0);
        Node other1 = node(S2, 2, 0);
        Node other2 = node(S3, 4, 0);

        check(frontier.offer(old),
                "插入旧结点成功");
        check(frontier.offer(current),
                "替换旧结点成功");
        check(frontier.offer(other1),
                "插入第二种状态成功");
        check(frontier.offer(other2),
                "插入第三种状态成功");

        check(frontier.size() == 3,
                "clear 前有效状态数为 3");

        frontier.clear();

        check(!frontier.contains(old),
                "clear 后不包含旧结点");

        check(!frontier.contains(current),
                "clear 后不包含替换后的结点");

        check(!frontier.contains(other1),
                "clear 后不包含第二种状态");

        check(!frontier.contains(other2),
                "clear 后不包含第三种状态");

        checkEmpty(frontier);

        Node reinserted = node(S1, 10, 0);

        check(frontier.offer(reinserted),
                "clear 后允许重新插入");

        check(frontier.size() == 1,
                "clear 后重新插入，有效结点数为 1");

        check(frontier.contains(reinserted),
                "clear 后重新插入的结点属于有效成员");

        check(frontier.poll() == reinserted,
                "clear 后重新插入的结点正常出队");

        check(!frontier.contains(reinserted),
                "clear 后重新插入的结点出队后索引已清理");

        checkEmpty(frontier);
    }

    /**
     * 检查新建空队列以及重复 clear、poll 的行为。
     */
    private static void testEmptyQueue() {
        PqFrontier frontier = new PqFrontier();

        checkEmpty(frontier);

        check(!frontier.contains(null),
                "contains(null) 返回 false");

        frontier.clear();
        frontier.clear();

        checkEmpty(frontier);
    }

    private static Node node(PuzzleBoard state, int g, int h) {
        return new Node(state, null, null, g, h);
    }

    /**
     * 在 poll 前后分别检查空队列状态，
     * 确保 size、isEmpty、contains、poll 保持一致。
     */
    private static void checkEmpty(PqFrontier frontier) {
        check(frontier.size() == 0,
                "空队列 size=0");

        check(frontier.isEmpty(),
                "空队列 isEmpty=true");

        check(!frontier.contains(node(S1, 3, 0)),
                "空队列 contains=false");

        check(frontier.poll() == null,
                "空队列 poll 返回 null");

        check(frontier.poll() == null,
                "重复 poll 空队列仍返回 null");

        check(frontier.size() == 0 && frontier.isEmpty(),
                "重复 poll 后 size=0 且 isEmpty=true");
    }

    /**
     * 显式断言：不依赖 -ea。
     * 失败时打印原因并抛出异常，使进程以失败状态退出。
     */
    private static void check(boolean condition, String message) {
        if (!condition) {
            String failure = "[" + currentTest + "] " + message;
            System.err.println("[FAIL] " + failure);
            throw new AssertionError(failure);
        }

        passedChecks++;
        System.out.println("[PASS] " + message);
    }
}