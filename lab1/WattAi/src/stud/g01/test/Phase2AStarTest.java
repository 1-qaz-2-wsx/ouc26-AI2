package stud.g01.test;

import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.queue.Node;
import stud.g01.heuristic.pdb.DisjointPatternDatabase;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.problem.npuzzle.PuzzleHeuristics;
import stud.g01.queue.PqFrontier;
import stud.g01.solver.Phase2AStar;

import java.nio.file.Path;
import java.util.Deque;

/**
 * A*、Open/Closed 优化和正式 PDB 接入测试。
 *
 * 所有测试均包含实际条件检查。任一检查失败时抛出 AssertionError，
 * 程序以非零状态结束；仅当全部检查完成后才输出 ALL PASS。
 */
public final class Phase2AStarTest {

    private static final PuzzleBoard GOAL =
            board(
                    1, 2, 3,
                    4, 5, 6,
                    7, 8, 0);

    private static int checks;

    private Phase2AStarTest() {
    }

    public static void main(String[] args) throws Exception {
        try {
            runAllTests(args);

            // 只有前面的全部检查成功后，才会执行到这里。
            System.out.println(
                    "ALL PASS: "
                            + checks
                            + " Phase2 A* checks");
        } catch (AssertionError failure) {
            System.err.println(
                    "FAILED after "
                            + checks
                            + " checks: "
                            + failure.getMessage());
            throw failure;
        } catch (Exception failure) {
            System.err.println(
                    "FAILED with exception after "
                            + checks
                            + " checks: "
                            + failure.getMessage());
            throw failure;
        }
    }

    private static void runAllTests(String[] args)
            throws Exception {

        Path pdbDirectory = Path.of(
                args.length == 0
                        ? ".local/pdb-phase2"
                        : args[0]);

        Predictor misplaced =
                PuzzleHeuristics.predictor(
                        HeuristicType.MISPLACED);

        Predictor manhattan =
                PuzzleHeuristics.predictor(
                        HeuristicType.MANHATTAN);

        DisjointPatternDatabase database =
                DisjointPatternDatabase.loadOrBuild(
                        GOAL,
                        pdbDirectory);

        check(database != null,
                "PDB loadOrBuild must return a database");

        Predictor disjointPattern = database.predictor();

        check(disjointPattern != null,
                "PDB must provide a predictor");

        testGoalEstimates(
                misplaced,
                manhattan,
                disjointPattern);

        testFrontierBestG();

        testThreePredictors(
                misplaced,
                manhattan,
                disjointPattern);

        testFourModes(manhattan);
        testGoalCase(manhattan);
        testUnsolvableCase(manhattan);
        testPdbGoalMismatch(disjointPattern);
    }

    /**
     * 验证三种启发函数在目标状态上的估值均为 0。
     */
    private static void testGoalEstimates(
            Predictor misplaced,
            Predictor manhattan,
            Predictor disjointPattern) {

        check(
                misplaced.heuristics(GOAL, GOAL) == 0,
                "misplaced(goal) must be zero");

        check(
                manhattan.heuristics(GOAL, GOAL) == 0,
                "Manhattan(goal) must be zero");

        check(
                disjointPattern.heuristics(GOAL, GOAL) == 0,
                "formal PDB(goal) must be zero");
    }

    /**
     * 验证 Open 表的 best-g、重复拒绝和延迟删除行为。
     */
    private static void testFrontierBestG() {
        PqFrontier frontier = new PqFrontier();

        check(frontier.isEmpty(),
                "new frontier must be empty");

        check(frontier.size() == 0,
                "new frontier size must be zero");

        PuzzleBoard state =
                board(
                        1, 2, 3,
                        4, 5, 6,
                        7, 0, 8);

        Node g10 = new Node(
                state, null, null, 10, 0);

        Node g7 = new Node(
                state, null, null, 7, 0);

        Node g9 = new Node(
                state, null, null, 9, 0);

        Node anotherG7 = new Node(
                state, null, null, 7, 0);

        check(
                frontier.offer(g10),
                "first state must be accepted");

        check(
                frontier.contains(g10),
                "frontier must contain the first active entry");

        check(
                frontier.size() == 1,
                "one inserted state must produce one active entry");

        check(
                frontier.offer(g7),
                "smaller g must replace current entry");

        check(
                !frontier.contains(g10),
                "replaced entry must no longer be active");

        check(
                frontier.contains(g7),
                "smaller-g entry must become active");

        check(
                !frontier.offer(g9),
                "larger g must be rejected");

        check(
                !frontier.offer(anotherG7),
                "equal g must be rejected");

        check(
                frontier.size() == 1,
                "only one active state must be counted");

        check(
                !frontier.isEmpty(),
                "frontier must contain the active best-g entry");

        Node best = frontier.poll();

        check(
                best != null,
                "poll must return the active entry");

        check(
                best == g7,
                "poll must return the current active object");

        check(
                best.getPathCost() == 7,
                "poll must return the smallest g");

        check(
                frontier.size() == 0,
                "active size must be zero after polling best entry");

        check(
                frontier.isEmpty(),
                "frontier must be logically empty after polling best entry");

        /*
         * activeNodes 已为空，但堆中仍保留被替换的 g=10 条目。
         * 再次 poll 必须跳过该过期条目并返回 null。
         */
        check(
                frontier.poll() == null,
                "stale heap entry must be skipped");

        check(
                frontier.staleSkipped() == 1,
                "exactly one stale entry must be counted");

        check(
                frontier.duplicateRejected() == 2,
                "exactly two non-improving duplicates must be counted");

        frontier.clear();

        check(frontier.isEmpty(),
                "cleared frontier must be empty");

        check(frontier.size() == 0,
                "cleared frontier size must be zero");

        check(frontier.poll() == null,
                "polling a cleared frontier must return null");
    }

    /**
     * 验证三种启发函数均能求解同一个已知四步问题，
     * 并返回相同的最优解长度和合法路径。
     */
    private static void testThreePredictors(
            Predictor misplaced,
            Predictor manhattan,
            Predictor disjointPattern) {

        NPuzzleProblem firstProblem =
                knownFourMoveProblem();

        NPuzzleProblem secondProblem =
                knownFourMoveProblem();

        NPuzzleProblem thirdProblem =
                knownFourMoveProblem();

        Phase2AStar.SearchResult first =
                solve(
                        firstProblem,
                        misplaced,
                        Phase2AStar.SearchMode
                                .FULL_OPTIMIZED);

        Phase2AStar.SearchResult second =
                solve(
                        secondProblem,
                        manhattan,
                        Phase2AStar.SearchMode
                                .FULL_OPTIMIZED);

        Phase2AStar.SearchResult third =
                solve(
                        thirdProblem,
                        disjointPattern,
                        Phase2AStar.SearchMode
                                .FULL_OPTIMIZED);

        checkSolvedResult(
                firstProblem,
                first,
                4,
                "MISPLACED");

        checkSolvedResult(
                secondProblem,
                second,
                4,
                "MANHATTAN");

        checkSolvedResult(
                thirdProblem,
                third,
                4,
                "DISJOINT_PATTERN");

        check(
                first.solutionLength()
                        == second.solutionLength(),
                "MISPLACED and MANHATTAN must return "
                        + "the same optimal length");

        check(
                second.solutionLength()
                        == third.solutionLength(),
                "MANHATTAN and PDB must return "
                        + "the same optimal length");
    }

    /**
     * 验证全部 Open/Closed 模式均返回合法的四步最优解。
     */
    private static void testFourModes(
            Predictor predictor) {

        Phase2AStar.SearchMode[] modes =
                Phase2AStar.SearchMode.values();

        check(
                modes.length > 0,
                "at least one search mode must exist");

        for (Phase2AStar.SearchMode mode : modes) {
            NPuzzleProblem problem =
                    knownFourMoveProblem();

            Phase2AStar.SearchResult result =
                    solve(problem, predictor, mode);

            checkSolvedResult(
                    problem,
                    result,
                    4,
                    mode.toString());
        }
    }

    /**
     * 验证初始状态已经是目标状态时返回零步解，
     * 且不扩展或生成任何后继结点。
     */
    private static void testGoalCase(
            Predictor predictor) {

        NPuzzleProblem problem =
                new NPuzzleProblem(GOAL, GOAL);

        Phase2AStar.SearchResult result =
                solve(
                        problem,
                        predictor,
                        Phase2AStar.SearchMode
                                .FULL_OPTIMIZED);

        check(
                result.solvable(),
                "goal case must be identified as solvable");

        check(
                result.solved(),
                "goal case must be solved");

        check(
                result.path() != null,
                "goal case must return a non-null empty path");

        check(
                result.path().isEmpty(),
                "goal case path must be empty");

        check(
                result.solutionLength() == 0,
                "goal case must require zero moves");

        check(
                result.generated() == 0,
                "goal case must generate zero child nodes");

        check(
                result.expanded() == 0,
                "goal node must not be expanded");

        check(
                validPath(problem, result.path()),
                "empty goal path must be valid");
    }

    /**
     * 验证不可解问题会在搜索前被识别，不生成结点、
     * 不扩展结点，也不返回伪造路径。
     */
    private static void testUnsolvableCase(
            Predictor predictor) {

        PuzzleBoard unsolvable =
                board(
                        1, 2, 3,
                        4, 5, 6,
                        8, 7, 0);

        NPuzzleProblem problem =
                new NPuzzleProblem(
                        unsolvable,
                        GOAL);

        check(
                !problem.solvable(),
                "test fixture must actually be unsolvable");

        Phase2AStar.SearchResult result =
                solve(
                        problem,
                        predictor,
                        Phase2AStar.SearchMode
                                .FULL_OPTIMIZED);

        check(
                !result.solvable(),
                "case must be identified as unsolvable");

        check(
                !result.solved(),
                "unsolvable case must not be solved");

        check(
                result.generated() == 0,
                "unsolvable case must generate zero nodes");

        check(
                result.expanded() == 0,
                "unsolvable case must expand zero nodes");

        check(
                result.path() == null,
                "unsolvable case must have no path");
    }

    /**
     * 验证 PDB 不能用于与建库目标不一致的搜索问题。
     */
    private static void testPdbGoalMismatch(
            Predictor predictor) {

        PuzzleBoard otherGoal =
                board(
                        1, 2, 3,
                        4, 5, 6,
                        7, 0, 8);

        expectIllegalArgument(
                () -> predictor.heuristics(
                        GOAL,
                        otherGoal),
                "formal PDB must reject mismatched goal");
    }

    /**
     * 对成功搜索结果执行统一硬校验。
     */
    private static void checkSolvedResult(
            NPuzzleProblem problem,
            Phase2AStar.SearchResult result,
            int expectedLength,
            String name) {

        check(
                result != null,
                name + " must return a SearchResult");

        check(
                result.solvable(),
                name + " must identify the problem as solvable");

        check(
                result.solved(),
                name + " must solve the known case");

        check(
                result.path() != null,
                name + " must return a non-null path");

        check(
                result.solutionLength() == expectedLength,
                name
                        + " expected solution length "
                        + expectedLength
                        + ", but got "
                        + result.solutionLength());

        check(
                result.path().size() == expectedLength,
                name
                        + " path size must equal solution length");

        check(
                result.generated() >= 0,
                name + " generated count cannot be negative");

        check(
                result.expanded() >= 0,
                name + " expanded count cannot be negative");

        check(
                result.generated() >= result.expanded(),
                name
                        + " generated count cannot be less "
                        + "than expanded count");

        check(
                validPath(problem, result.path()),
                name + " must produce a valid path");

        check(
                !result.path().isEmpty()
                        && problem.goal(
                        result.path()
                                .getLast()
                                .getState()),
                name + " path must end at the requested goal");
    }

    /**
     * 独立检查路径中每一步是否为当前状态的合法后继。
     *
     * 检查内容包括：
     * 1. 路径不为 null；
     * 2. 每个路径结点都能由前一状态一步生成；
     * 3. 每个结点的路径代价连续递增；
     * 4. 路径中的动作与合法后继动作一致；
     * 5. 最终状态等于目标状态。
     */
    private static boolean validPath(
            NPuzzleProblem problem,
            Deque<Node> path) {

        if (path == null) {
            return false;
        }

        Predictor zero = (state, goal) -> 0;
        Node current = problem.root(zero);
        int expectedCost = 1;

        for (Node expected : path) {
            if (expected == null
                    || expected.getState() == null
                    || expected.getAction() == null) {

                return false;
            }

            Node matching = null;

            for (Node child
                    : problem.childNodes(current, zero)) {

                boolean sameState =
                        child.getState().equals(
                                expected.getState());

                boolean sameAction =
                        child.getAction().equals(
                                expected.getAction());

                if (sameState && sameAction) {
                    matching = child;
                    break;
                }
            }

            if (matching == null) {
                return false;
            }

            if (expected.getPathCost()
                    != expectedCost) {

                return false;
            }

            if (matching.getPathCost()
                    != expectedCost) {

                return false;
            }

            current = matching;
            expectedCost++;
        }

        return problem.goal(current.getState());
    }

    /**
     * 已知最优解长度为 4 的测试问题。
     */
    private static NPuzzleProblem knownFourMoveProblem() {
        return new NPuzzleProblem(
                board(
                        1, 2, 3,
                        5, 0, 6,
                        4, 7, 8),
                GOAL);
    }

    private static Phase2AStar.SearchResult solve(
            NPuzzleProblem problem,
            Predictor predictor,
            Phase2AStar.SearchMode mode) {

        Phase2AStar searcher =
                new Phase2AStar(
                        predictor,
                        mode);

        Phase2AStar.SearchResult result =
                searcher.search(problem);

        if (result == null) {
            throw new AssertionError(
                    "search returned null SearchResult for "
                            + mode);
        }

        return result;
    }

    private static PuzzleBoard board(int... cells) {
        return new PuzzleBoard(3, cells);
    }

    /**
     * 验证操作确实抛出 IllegalArgumentException。
     */
    private static void expectIllegalArgument(
            Runnable action,
            String description) {

        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            checks++;
            return;
        } catch (RuntimeException unexpected) {
            throw new AssertionError(
                    description
                            + ": expected IllegalArgumentException"
                            + " but got "
                            + unexpected.getClass().getSimpleName(),
                    unexpected);
        }

        throw new AssertionError(
                description
                        + ": expected IllegalArgumentException"
                        + " but no exception was thrown");
    }

    /**
     * 执行实际条件检查。失败时立即抛出 AssertionError，
     * 不依赖 JVM 的 -ea 参数。
     */
    private static void check(
            boolean condition,
            String description) {

        checks++;

        if (!condition) {
            throw new AssertionError(
                    "check "
                            + checks
                            + " failed: "
                            + description);
        }
    }
}