package stud.g01.runner;

import core.solver.queue.Node;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Deque;

/**
 * 解路径导出工具：按 NP_PATH_V1 格式写出纯文本解路径文件，供成员D的可视化播放器读取。
 * 格式约定（「棋盘与路径接口.md」）：
 *   - 文件为 UTF-8 纯文本，一个文件对应一个问题；
 *   - 首行为协议头 NP_PATH_V1；
 *   - SOLVED：moves 为步数，states = moves + 1，第 0 帧为初始状态，最后一帧必须等于目标；
 *   - UNSOLVABLE：moves -1、states 1，只保存初始状态与目标，播放器显示无解不播放。
 * 注意：框架 generatePath 返回的队列不含根节点，因此导出 SOLVED 时必须单独加入初始状态
 * 作为第 0 帧。
 */
public final class SolutionPathWriter {

    /** 工具类，禁止实例化 */
    private SolutionPathWriter() {
    }

    /**
     * 写出解路径文件（NP_PATH_V1）。
     * @param output  目标文件路径；所在目录若不存在会自动创建
     * @param problem 对应的问题实例
     * @param path    解路径（由 generatePath 得到，不含根节点）；
     *                path == null 只有在问题已确认不可解时才写 UNSOLVABLE，
     *                否则视为失败、不导出伪造路径
     * @throws IOException 写文件失败，或"可解但无解路径"的异常情况
     */
    public static void write(Path output, NPuzzleProblem problem, Deque<Node> path) throws IOException {
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);   // 目录由导出器创建
        }

        PuzzleBoard initial = problem.initialBoard();
        PuzzleBoard goal = problem.goalBoard();
        StringBuilder sb = new StringBuilder();
        sb.append("NP_PATH_V1\n");
        sb.append("size ").append(initial.size()).append('\n');

        if (path == null) {
            // 只有确认不可解才允许写 UNSOLVABLE
            if (!problem.solvable()) {
                sb.append("status UNSOLVABLE\n");
                sb.append("moves -1\n");
                sb.append("states 1\n");
                sb.append("goal ").append(boardLine(goal)).append('\n');
                sb.append("state 0 ").append(boardLine(initial)).append('\n');
            } else {
                throw new IOException("问题可解但未得到解路径，拒绝导出伪造路径: " + output);
            }
        } else {
            sb.append("status SOLVED\n");
            sb.append("moves ").append(path.size()).append('\n');
            sb.append("states ").append(path.size() + 1).append('\n');
            sb.append("goal ").append(boardLine(goal)).append('\n');
            // 第 0 帧为初始状态（generatePath 的队列不含根节点）
            sb.append("state 0 ").append(boardLine(initial)).append('\n');
            int step = 0;
            for (Node node : path) {
                step++;
                sb.append("state ").append(step).append(' ')
                  .append(boardLine((PuzzleBoard) node.getState())).append('\n');
            }
        }

        Files.write(output, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 把棋盘数字按行优先、以空格分隔拼成一行 */
    private static StringBuilder boardLine(PuzzleBoard board) {
        int[] a = board.toArray();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(a[i]);
        }
        return sb;
    }
}
